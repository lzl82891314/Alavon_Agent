package com.example.avalon.agent.gateway;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.service.AgentTurnExecutionException;
import com.example.avalon.agent.service.ResponseParser;
import com.example.avalon.agent.service.ValidatedAgentTurn;
import com.example.avalon.agent.service.ValidationRetryPolicy;
import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.game.enums.DiscussionStage;
import com.example.avalon.core.game.enums.GamePhase;
import com.example.avalon.core.game.enums.GameStatus;
import com.example.avalon.core.game.enums.PlayerActionType;
import com.example.avalon.core.game.enums.PlayerConnectionState;
import com.example.avalon.core.game.model.AllowedActionSet;
import com.example.avalon.core.game.model.DiscussionTurnDirective;
import com.example.avalon.core.game.model.PlayerTurnContext;
import com.example.avalon.core.game.model.PublicGameSnapshot;
import com.example.avalon.core.game.model.PublicPlayerSummary;
import com.example.avalon.core.game.observation.PlayerObservationBatch;
import com.example.avalon.core.player.enums.PlayerControllerType;
import com.example.avalon.core.player.memory.PlayerMemoryState;
import com.example.avalon.core.player.memory.PlayerPrivateKnowledge;
import com.example.avalon.core.player.memory.PlayerPrivateView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiGameTurnPipelineTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void http200WithoutAssistantContentRetainsResponseDiagnostics() throws Exception {
        OpenAiChatCompletionsGateway gateway = gateway(response(null, null));

        OpenAiCompatibleResponseException exception = assertThrows(
                OpenAiCompatibleResponseException.class,
                () -> gateway.playTurn(request())
        );

        assertTrue(exception.getMessage().contains("did not include assistant content"));
        assertEquals("stop", exception.diagnostics().get("finishReason"));
        assertEquals(false, exception.diagnostics().get("contentPresent"));
        assertEquals("missing_content", exception.diagnostics().get("assistantContentShape"));
    }

    @Test
    void deepSeekReasoningContentWithoutFinalContentIsDiagnosedAsReasoningOnly() throws Exception {
        OpenAiChatCompletionsGateway gateway = gateway(response(null, "thinking without a final answer"));

        OpenAiCompatibleResponseException exception = assertThrows(
                OpenAiCompatibleResponseException.class,
                () -> gateway.playTurn(request())
        );

        assertEquals(true, exception.diagnostics().get("reasoningDetailsPresent"));
        assertEquals("reasoning_only", exception.diagnostics().get("assistantContentShape"));
        assertTrue(exception.getMessage().contains("assistant content was empty"));
    }

    @Test
    void gamePipelineRetriesEmptyContentTwiceAndExposesFinalResponseCause() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        OpenAiChatCompletionsGateway gateway = gateway((uri, headers, body, timeout, frameConsumer) -> {
            calls.incrementAndGet();
            frameConsumer.accept(new SseFrame("", response(null, "reasoning only").toString()));
            return streamResponse();
        });

        AgentTurnExecutionException exception = assertThrows(
                AgentTurnExecutionException.class,
                () -> new ValidationRetryPolicy().execute(context(), request(), gateway, new ResponseParser())
        );

        assertEquals(2, calls.get());
        assertEquals(2, exception.attempts());
        OpenAiCompatibleResponseException cause = assertInstanceOf(
                OpenAiCompatibleResponseException.class,
                exception.getCause()
        );
        assertEquals("reasoning_only", cause.diagnostics().get("assistantContentShape"));
    }

    @Test
    void gamePipelineAcceptsValidActionWithoutStrategicMemory() throws Exception {
        String content = objectMapper.writeValueAsString(Map.of(
                "publicSpeech", "当前信息不足，我先观察。",
                "action", action()
        ));
        OpenAiChatCompletionsGateway gateway = gateway(response(content, null));

        ValidatedAgentTurn validated = new ValidationRetryPolicy().execute(
                context(), request(), gateway, new ResponseParser());

        assertEquals(1, validated.attempts());
        assertNull(validated.turnResult().getMemoryUpdate());
    }

    @Test
    void completeGameResponsePassesTransportParsingAndStrategicValidation() throws Exception {
        Map<String, Object> memoryUpdate = Map.of(
                "roleBeliefs", Map.of(),
                "strategyState", Map.of(
                        "mode", "OBSERVE",
                        "objective", "collect public evidence",
                        "deceptionIntent", "NONE"
                ),
                "communicationPlan", Map.of(
                        "speechAct", "STATE_OPINION",
                        "publicMessage", "当前信息不足，我先观察。"
                ),
                "evidenceReferences", List.of(),
                "beliefEvidenceReferences", Map.of(),
                "observedThroughSequence", 0
        );
        String content = objectMapper.writeValueAsString(Map.of(
                "publicSpeech", "当前信息不足，我先观察。",
                "action", action(),
                "memoryUpdate", memoryUpdate
        ));

        ValidatedAgentTurn validated = new ValidationRetryPolicy().execute(
                context(), request(), gateway(response(content, null)), new ResponseParser()
        );

        assertEquals(1, validated.attempts());
        assertEquals("当前信息不足，我先观察。", validated.turnResult().getPublicSpeech());
    }

    private Map<String, Object> action() {
        return Map.of(
                "actionType", "PUBLIC_SPEECH",
                "speechText", "当前信息不足，我先观察。",
                "speechAct", "STATE_OPINION",
                "mentions", List.of(),
                "replyToEventSequences", List.of()
        );
    }

    private OpenAiChatCompletionsGateway gateway(JsonNode response) {
        return gateway((uri, headers, body, timeout, frameConsumer) -> {
            frameConsumer.accept(new SseFrame("", response.toString()));
            return streamResponse();
        });
    }

    private OpenAiChatCompletionsGateway gateway(OpenAiHttpTransport transport) {
        return new OpenAiChatCompletionsGateway(transport, ignored -> "test-key");
    }

    private SseHttpResponse streamResponse() {
        return new SseHttpResponse(200, Map.of("content-type", List.of("text/event-stream")), 1);
    }

    private JsonNode response(String content, String reasoningContent) {
        Map<String, Object> message = new java.util.LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", content);
        if (reasoningContent != null) {
            message.put("reasoning_content", reasoningContent);
        }
        return objectMapper.valueToTree(Map.of(
                "id", "completion-test",
                "model", "deepseek-v4-flash",
                "choices", List.of(Map.of("index", 0, "finish_reason", "stop", "message", message)),
                "usage", Map.of("prompt_tokens", 10, "completion_tokens", 5)
        ));
    }

    private AgentTurnRequest request() {
        AgentTurnRequest request = new AgentTurnRequest();
        request.setGameId("g1");
        request.setRoundNo(1);
        request.setPhase("DISCUSSION");
        request.setPlayerId("P1");
        request.setSeatNo(1);
        request.setRoleId("LOYAL_SERVANT");
        request.setModelId("deepseek-v4-flash");
        request.setProvider("deepseek");
        request.setProtocol("OPENAI_COMPATIBLE_CHAT");
        request.setModelName("deepseek-v4-flash");
        request.setAllowedActions(List.of("PUBLIC_SPEECH"));
        request.setProviderOptions(Map.of(
                "baseUrl", "https://example.invalid/v1",
                "instructionRole", "system",
                "response_format", Map.of("type", "json_object")
        ));
        request.setPromptText("Return the required game JSON.");
        return request;
    }

    private PlayerTurnContext context() {
        Instant now = Instant.now();
        PlayerMemoryState memory = PlayerMemoryState.empty("g1", "P1", "LOYAL_SERVANT", Camp.GOOD, now);
        PublicGameSnapshot snapshot = new PublicGameSnapshot(
                "g1", GameStatus.RUNNING, GamePhase.DISCUSSION, 1, 0, 0, 0, 1,
                DiscussionStage.OPENING_STATEMENTS, "P1", 0L, List.of(), null, null,
                List.of(player("P1", 1), player("P2", 2)), now
        );
        return new PlayerTurnContext(
                "g1", 1, "DISCUSSION", "P1", 1, "LOYAL_SERVANT", snapshot,
                new PlayerPrivateView("g1", "P1", 1, "LOYAL_SERVANT", Camp.GOOD,
                        new PlayerPrivateKnowledge(List.of(), List.of()), List.of()),
                memory,
                new PlayerObservationBatch("g1", "P1", "P1:primary", 0, 0, List.of()),
                new DiscussionTurnDirective("OPENING_STATEMENTS", List.of("STATE_OPINION"), null, null, 1),
                new AllowedActionSet("g1", "P1", 1, Set.of(PlayerActionType.PUBLIC_SPEECH)),
                null, null, "rules"
        );
    }

    private PublicPlayerSummary player(String playerId, int seatNo) {
        return new PublicPlayerSummary(
                "g1", playerId, seatNo, playerId,
                PlayerControllerType.LLM, PlayerConnectionState.CONNECTED
        );
    }
}
