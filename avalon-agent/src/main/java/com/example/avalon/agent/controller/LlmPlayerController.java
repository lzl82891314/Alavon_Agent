package com.example.avalon.agent.controller;

import com.example.avalon.agent.gateway.AgentGateway;
import com.example.avalon.agent.gateway.OpenAiCompatibleResponseException;
import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.agent.model.RawCompletionMetadata;
import com.example.avalon.agent.service.AgentTurnExecutionException;
import com.example.avalon.agent.service.AgentTurnRequestFactory;
import com.example.avalon.agent.service.PromptBuilder;
import com.example.avalon.agent.service.ResponseParser;
import com.example.avalon.agent.service.ValidatedAgentTurn;
import com.example.avalon.agent.service.ValidationRetryPolicy;
import com.example.avalon.agent.harness.AgentHarness;
import com.example.avalon.agent.harness.DefaultAgentHarness;
import com.example.avalon.agent.harness.HarnessExecution;
import com.example.avalon.core.game.model.PlayerActionResult;
import com.example.avalon.core.game.model.PlayerTurnContext;
import com.example.avalon.core.player.controller.PlayerActionGenerationException;
import com.example.avalon.core.player.controller.PlayerController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LlmPlayerController implements PlayerController {
    private static final String OPTIONAL_SECTION_WARNINGS = "optionalSectionWarnings";
    private static final List<String> RAW_METADATA_ATTRIBUTE_KEYS = List.of(
            "assistantContentShape",
            "assistantContentPreview",
            "reasoningDetailsPreview",
            "contentPresent",
            "reasoningDetailsPresent",
            "streaming",
            "transportAttempts",
            "firstReasoningDeltaMs",
            "firstContentDeltaMs",
            "reasoningChars",
            "finishReason",
            "gatewayType"
    );

    private final AgentHarness harness;
    private final PlayerAgentConfig playerAgentConfig;

    public LlmPlayerController(AgentGateway agentGateway,
                               AgentTurnRequestFactory requestFactory,
                               PromptBuilder promptBuilder,
                               ResponseParser responseParser,
                               ValidationRetryPolicy validationRetryPolicy,
                               PlayerAgentConfig playerAgentConfig) {
        this.harness = new DefaultAgentHarness(requestFactory, promptBuilder, agentGateway, responseParser, validationRetryPolicy);
        this.playerAgentConfig = playerAgentConfig == null ? new PlayerAgentConfig() : playerAgentConfig;
    }

    public LlmPlayerController(AgentHarness harness, PlayerAgentConfig playerAgentConfig) {
        this.harness = harness;
        this.playerAgentConfig = playerAgentConfig == null ? new PlayerAgentConfig() : playerAgentConfig;
    }

    @Override
    public PlayerActionResult act(PlayerTurnContext context) {
        try {
            HarnessExecution validated = harness.execute(context, playerAgentConfig);
            AgentTurnResult turnResult = validated.turnResult();
            return new PlayerActionResult(
                    turnResult.getPublicSpeech(),
                    validated.action(),
                    toCoreAuditReason(turnResult),
                    toCoreMemoryUpdate(turnResult, context),
                    rawMetadata(turnResult, validated.request(), validated.attempts())
            );
        } catch (AgentTurnExecutionException exception) {
            throw new PlayerActionGenerationException(
                    exception.getMessage(),
                    failureMetadata(exception.request(), exception.lastTurnResult(), exception.attempts(), exception.getCause()),
                    exception
            );
        } catch (RuntimeException exception) {
            throw new PlayerActionGenerationException(
                    exception.getMessage() == null ? "LLM turn failed" : exception.getMessage(),
                    failureMetadata(new AgentTurnRequest(), null, 0, exception),
                    exception
            );
        }
    }

    private com.example.avalon.core.player.memory.AuditReason toCoreAuditReason(AgentTurnResult turnResult) {
        if (turnResult.getAuditReason() == null) {
            return null;
        }
        return new com.example.avalon.core.player.memory.AuditReason(
                turnResult.getAuditReason().getGoal(),
                turnResult.getAuditReason().getReasonSummary() == null ? List.of() : turnResult.getAuditReason().getReasonSummary(),
                turnResult.getAuditReason().getConfidence(),
                turnResult.getAuditReason().getBeliefs()
        );
    }

    private com.example.avalon.core.player.memory.MemoryUpdate toCoreMemoryUpdate(AgentTurnResult turnResult,
                                                                                 PlayerTurnContext context) {
        com.example.avalon.agent.model.MemoryUpdate proposed = turnResult.getMemoryUpdate() == null
                ? new com.example.avalon.agent.model.MemoryUpdate()
                : turnResult.getMemoryUpdate();
        List<Map<String, Object>> worldFacts = new java.util.ArrayList<>();
        List<Map<String, Object>> publicClaims = new java.util.ArrayList<>();
        context.observations().events().forEach(event -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("sequence", event.sequence());
            entry.put("eventType", event.eventType());
            entry.put("actorPlayerId", event.actorPlayerId());
            entry.put("facts", event.facts());
            if (event.utterance() != null) entry.put("utterance", event.utterance());
            if (event.scope() == com.example.avalon.core.game.observation.FactScope.PUBLIC_CLAIM) publicClaims.add(entry);
            else worldFacts.add(entry);
        });
        return new com.example.avalon.core.player.memory.MemoryUpdate(
                proposed.getSuspicionDelta(),
                proposed.getTrustDelta(),
                proposed.getObservationsToAdd(),
                proposed.getCommitmentsToAdd(),
                proposed.getInferredFactsToAdd(),
                worldFacts,
                publicClaims,
                proposed.getRoleBeliefs(),
                proposed.getStrategyState(),
                proposed.getCommunicationPlan(),
                proposed.getEvidenceReferences(),
                proposed.getBeliefEvidenceReferences(),
                context.observations().toSequenceInclusive(),
                proposed.getStrategyMode(),
                proposed.getLastSummary()
        );
    }

    private Map<String, Object> rawMetadata(AgentTurnResult turnResult, AgentTurnRequest request, int attempts) {
        Map<String, Object> payload = new LinkedHashMap<>();
        RawCompletionMetadata metadata = turnResult.getModelMetadata();
        if (metadata != null) {
            putIfNotNull(payload, "provider", metadata.getProvider());
            putIfNotNull(payload, "modelName", metadata.getModelName());
            putIfNotNull(payload, "inputTokens", metadata.getInputTokens());
            putIfNotNull(payload, "outputTokens", metadata.getOutputTokens());
            if (metadata.getAttributes() != null && !metadata.getAttributes().isEmpty()) {
                payload.put("attributes", metadata.getAttributes());
            }
        }
        payload.put("attempts", attempts);
        payload.put("outputSchemaVersion", request.getOutputSchemaVersion());
        payload.put("inputContext", inputContext(request));
        payload.put("rawModelResponse", rawModelResponse(turnResult));
        payload.put("validation", validationSummary(turnResult, request, attempts));
        return payload;
    }

    private Map<String, Object> inputContext(AgentTurnRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("gameId", request.getGameId());
        payload.put("roundNo", request.getRoundNo());
        payload.put("phase", request.getPhase());
        payload.put("playerId", request.getPlayerId());
        payload.put("seatNo", request.getSeatNo());
        payload.put("roleId", request.getRoleId());
        payload.put("modelId", request.getModelId());
        payload.put("provider", request.getProvider());
        payload.put("protocol", request.getProtocol());
        payload.put("modelName", request.getModelName());
        payload.put("temperature", request.getTemperature());
        payload.put("allowedActions", request.getAllowedActions());
        payload.put("rulesSummary", request.getRulesSummary());
        payload.put("privateKnowledge", request.getPrivateKnowledge());
        payload.put("publicState", request.getPublicState());
        payload.put("memory", request.getMemory());
        payload.put("strategyContext", request.getStrategyContext());
        payload.put("observationDelta", request.getObservationDelta());
        payload.put("observationFromSequence", request.getObservationFromSequence());
        payload.put("observationToSequence", request.getObservationToSequence());
        payload.put("discussionDirective", request.getDiscussionDirective());
        payload.put("promptText", request.getPromptText());
        payload.put("outputSchemaVersion", request.getOutputSchemaVersion());
        return payload;
    }

    private Map<String, Object> rawModelResponse(AgentTurnResult turnResult) {
        Map<String, Object> payload = new LinkedHashMap<>();
        putIfNotNull(payload, "publicSpeech", turnResult.getPublicSpeech());
        putIfNotNull(payload, "privateThought", turnResult.getPrivateThought());
        putIfNotNull(payload, "actionJson", turnResult.getActionJson());
        if (turnResult.getAuditReason() != null) {
            payload.put("auditReason", turnResult.getAuditReason());
        }
        if (turnResult.getMemoryUpdate() != null) {
            payload.put("memoryUpdate", turnResult.getMemoryUpdate());
        }
        if (turnResult.getModelMetadata() != null) {
            payload.put("modelMetadata", turnResult.getModelMetadata());
            if (turnResult.getModelMetadata().getAttributes() != null) {
                copyDiagnosticAttributes(payload, turnResult.getModelMetadata().getAttributes());
            }
        }
        return payload;
    }

    private Map<String, Object> validationSummary(AgentTurnResult turnResult, AgentTurnRequest request, int attempts) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("valid", true);
        payload.put("attempts", attempts);
        payload.put("allowedActions", request.getAllowedActions());
        payload.put("outputSchemaVersion", request.getOutputSchemaVersion());
        if (turnResult != null
                && turnResult.getModelMetadata() != null
                && turnResult.getModelMetadata().getAttributes() != null) {
            Object warnings = turnResult.getModelMetadata().getAttributes().get(OPTIONAL_SECTION_WARNINGS);
            if (warnings != null) {
                payload.put(OPTIONAL_SECTION_WARNINGS, warnings);
            }
        }
        return payload;
    }

    private Map<String, Object> failureMetadata(AgentTurnRequest request,
                                                AgentTurnResult turnResult,
                                                int attempts,
                                                Throwable throwable) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("attempts", attempts);
        payload.put("outputSchemaVersion", request.getOutputSchemaVersion());
        payload.put("inputContext", inputContext(request));
        payload.put("rawModelResponse", failureRawModelResponse(turnResult, throwable));
        payload.put("validation", failedValidationSummary(request, attempts, throwable));
        payload.put("auditVisibility", "ADMIN_ONLY");
        putIfNotNull(payload, "errorMessage", throwable == null ? null : throwable.getMessage());
        return payload;
    }

    private Map<String, Object> failedValidationSummary(AgentTurnRequest request, int attempts, Throwable throwable) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("valid", false);
        payload.put("attempts", attempts);
        payload.put("allowedActions", request.getAllowedActions());
        payload.put("outputSchemaVersion", request.getOutputSchemaVersion());
        putIfNotNull(payload, "errorMessage", throwable == null ? null : throwable.getMessage());
        if (throwable instanceof OpenAiCompatibleResponseException responseException) {
            copyDiagnosticAttributes(payload, responseException.diagnostics());
        }
        return payload;
    }

    private Map<String, Object> failureRawModelResponse(AgentTurnResult turnResult, Throwable throwable) {
        if (turnResult != null) {
            return rawModelResponse(turnResult);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        if (throwable instanceof OpenAiCompatibleResponseException responseException) {
            payload.putAll(responseException.diagnostics());
        }
        return payload;
    }

    private void copyDiagnosticAttributes(Map<String, Object> target, Map<String, Object> attributes) {
        for (String key : RAW_METADATA_ATTRIBUTE_KEYS) {
            Object value = attributes.get(key);
            if (value != null) {
                target.put(key, value);
            }
        }
    }

    private void putIfNotNull(Map<String, Object> payload, String key, Object value) {
        if (value != null) {
            payload.put(key, value);
        }
    }
}
