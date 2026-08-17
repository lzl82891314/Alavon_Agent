package com.example.avalon.agent.service;

import com.example.avalon.agent.gateway.AgentGateway;
import com.example.avalon.agent.gateway.OpenAiCompatibleResponseException;
import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.agent.strategy.RoleStrategyPolicy;
import com.example.avalon.core.game.model.PlayerAction;
import com.example.avalon.core.game.model.PlayerTurnContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class ValidationRetryPolicy {
    private static final int DEFAULT_MAX_ATTEMPTS = 2;
    private static final double INITIAL_PRIOR_MAX_DISTANCE = 0.15d;
    private static final double NO_EVIDENCE_MAX_DELTA = 0.05d;
    private static final double EVIDENCE_MAX_DELTA = 0.25d;

    private final PrivateKnowledgeExpressionValidator privateKnowledgeExpressionValidator;

    public ValidationRetryPolicy() {
        this(new PrivateKnowledgeExpressionValidator());
    }

    ValidationRetryPolicy(PrivateKnowledgeExpressionValidator privateKnowledgeExpressionValidator) {
        this.privateKnowledgeExpressionValidator = privateKnowledgeExpressionValidator;
    }

    public ValidatedAgentTurn execute(PlayerTurnContext context,
                                      AgentTurnRequest request,
                                      AgentGateway agentGateway,
                                      ResponseParser responseParser) {
        RuntimeException lastFailure = null;
        AgentTurnResult lastResult = null;
        AgentTurnRequest attemptRequest = request.copy();
        for (int attempts = 1; attempts <= DEFAULT_MAX_ATTEMPTS; attempts++) {
            try {
                AgentTurnResult result = agentGateway.playTurn(attemptRequest);
                lastResult = result;
                PlayerAction action = responseParser.parse(context, result);
                validateStrategicOutput(context, result, action);
                privateKnowledgeExpressionValidator.validate(context, result, action);
                return new ValidatedAgentTurn(result, action, attempts, attemptRequest.copy());
            } catch (RuntimeException exception) {
                lastFailure = exception;
                if (attempts < DEFAULT_MAX_ATTEMPTS && shouldRetry(exception)) {
                    attemptRequest = nextAttemptRequest(attemptRequest, exception);
                    continue;
                }
                throw new AgentTurnExecutionException(
                        "Agent turn validation failed after " + attempts + " attempts",
                        attemptRequest,
                        lastResult,
                        attempts,
                        lastFailure
                );
            }
        }
        throw new AgentTurnExecutionException(
                "Agent turn validation failed after " + DEFAULT_MAX_ATTEMPTS + " attempts",
                attemptRequest,
                lastResult,
                DEFAULT_MAX_ATTEMPTS,
                lastFailure
        );
    }

    private void validateStrategicOutput(PlayerTurnContext context, AgentTurnResult result, PlayerAction action) {
        if (result == null || result.getMemoryUpdate() == null) {
            throw new IllegalStateException("Strategic output must include memoryUpdate");
        }
        validateStrategyContract(context, result, action);
        Map<Long, Object> availableEvidence = new java.util.LinkedHashMap<>();
        context.observations().events().forEach(event -> availableEvidence.put(event.sequence(), event));
        context.memoryState().worldFacts().forEach(fact -> addEvidence(availableEvidence, fact));
        context.memoryState().publicClaims().forEach(claim -> addEvidence(availableEvidence, claim));
        for (Long reference : result.getMemoryUpdate().getEvidenceReferences()) {
            if (reference == null || !availableEvidence.containsKey(reference)) {
                throw new IllegalStateException("Evidence reference is not visible to this agent: " + reference);
            }
        }
        result.getMemoryUpdate().getBeliefEvidenceReferences().forEach((playerId, references) -> {
            if (!result.getMemoryUpdate().getRoleBeliefs().containsKey(playerId)) {
                throw new IllegalStateException("Belief evidence has no corresponding role belief for " + playerId);
            }
            for (Long reference : references) {
                Object evidence = reference == null ? null : availableEvidence.get(reference);
                if (evidence == null) {
                    throw new IllegalStateException("Belief evidence is not visible to this agent: " + reference);
                }
                if (!evidenceMentionsPlayer(evidence, playerId)) {
                    throw new IllegalStateException("Belief evidence does not concern " + playerId + ": " + reference);
                }
            }
        });
        result.getMemoryUpdate().getRoleBeliefs().forEach((player, probability) -> {
            if (probability == null || probability < 0.0d || probability > 1.0d) {
                throw new IllegalStateException("Invalid role belief probability for " + player);
            }
            validateBeliefChange(context, result, player, probability);
        });
    }

    private void validateStrategyContract(PlayerTurnContext context, AgentTurnResult result, PlayerAction action) {
        Map<String, Object> strategy = result.getMemoryUpdate().getStrategyState();
        Map<String, Object> communication = result.getMemoryUpdate().getCommunicationPlan();
        requireNonBlank(strategy, "mode", "strategyState.mode");
        requireNonBlank(strategy, "objective", "strategyState.objective");
        if (action instanceof com.example.avalon.core.game.model.PublicSpeechAction speechAction) {
            requireNonBlank(communication, "speechAct", "communicationPlan.speechAct");
            Object plannedMessage = communication.get("publicMessage");
            if (plannedMessage == null || !speechAction.speechText().trim().equals(String.valueOf(plannedMessage).trim())) {
                throw new IllegalStateException("communicationPlan.publicMessage must match publicSpeech");
            }
            if (!speechAction.speechAct().equals(String.valueOf(communication.get("speechAct")))) {
                throw new IllegalStateException("communicationPlan.speechAct must match the public action");
            }
        }
        Object intentValue = strategy.get("deceptionIntent");
        String intent = intentValue == null ? "NONE" : String.valueOf(intentValue).trim().toUpperCase(Locale.ROOT);
        if (!RoleStrategyPolicy.permittedDeceptionIntents(context.roleId()).contains(intent)) {
            throw new IllegalStateException("Deception intent is not permitted for role " + context.roleId() + ": " + intent);
        }
        if ("MERLIN".equalsIgnoreCase(context.roleId())) {
            Object exposureRisk = strategy.get("exposureRisk");
            if (!(exposureRisk instanceof Number number)
                    || !Double.isFinite(number.doubleValue())
                    || number.doubleValue() < 0.0d || number.doubleValue() > 1.0d) {
                throw new IllegalStateException("Merlin exposureRisk must be a number between 0 and 1");
            }
        }
    }

    private void requireNonBlank(Map<String, Object> values, String key, String label) {
        Object value = values.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalStateException(label + " is required");
        }
    }

    private void validateBeliefChange(PlayerTurnContext context,
                                      AgentTurnResult result,
                                      String playerId,
                                      double proposedProbability) {
        java.util.Set<String> knownPlayers = context.publicState().players().stream()
                .map(com.example.avalon.core.game.model.PublicPlayerSummary::playerId)
                .collect(java.util.stream.Collectors.toSet());
        if (!knownPlayers.contains(playerId)) {
            throw new IllegalStateException("Role belief references an unknown player: " + playerId);
        }
        Double privateProbability = privateCampProbability(context, playerId);
        if (privateProbability != null) {
            if (Math.abs(proposedProbability - privateProbability) > 0.001d) {
                throw new IllegalStateException("Role belief contradicts private camp knowledge for " + playerId);
            }
            return;
        }
        Double prior = context.memoryState().roleBeliefs().get(playerId);
        List<Long> boundEvidence = result.getMemoryUpdate().getBeliefEvidenceReferences()
                .getOrDefault(playerId, List.of());
        boolean hasEvidence = !boundEvidence.isEmpty();
        double baseline = prior == null ? 0.5d : prior;
        double allowedDelta = prior == null
                ? INITIAL_PRIOR_MAX_DISTANCE
                : hasEvidence ? EVIDENCE_MAX_DELTA : NO_EVIDENCE_MAX_DELTA;
        if (Math.abs(proposedProbability - baseline) > allowedDelta + 0.000001d) {
            throw new IllegalStateException("Role belief changed too far without sufficient evidence for " + playerId);
        }
    }

    private Double privateCampProbability(PlayerTurnContext context, String playerId) {
        if (context.playerId().equals(playerId)) {
            return context.privateView().camp() == com.example.avalon.core.game.enums.Camp.EVIL ? 1.0d : 0.0d;
        }
        return context.privateView().knowledge().visiblePlayers().stream()
                .filter(player -> player.playerId().equals(playerId) && player.camp() != null)
                .map(player -> player.camp() == com.example.avalon.core.game.enums.Camp.EVIL ? 1.0d : 0.0d)
                .findFirst()
                .orElse(null);
    }

    private void addEvidence(Map<Long, Object> target, Map<String, Object> evidence) {
        Object value = evidence.get("sequence");
        if (value instanceof Number number) target.put(number.longValue(), evidence);
    }

    private boolean evidenceMentionsPlayer(Object evidence, String playerId) {
        if (evidence instanceof com.example.avalon.core.game.observation.ObservedGameEvent event) {
            return playerId.equals(event.actorPlayerId())
                    || containsPlayer(event.facts(), playerId)
                    || containsPlayer(event.mentions(), playerId);
        }
        return containsPlayer(evidence, playerId);
    }

    private boolean containsPlayer(Object value, String playerId) {
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .filter(entry -> !"sequence".equals(String.valueOf(entry.getKey())))
                    .anyMatch(entry -> containsPlayer(entry.getValue(), playerId));
        }
        if (value instanceof Iterable<?> values) {
            for (Object item : values) if (containsPlayer(item, playerId)) return true;
            return false;
        }
        return playerId.equals(value);
    }

    private AgentTurnRequest nextAttemptRequest(AgentTurnRequest request, RuntimeException failure) {
        AgentTurnRequest next = request.copy();
        String correctivePrompt = correctivePrompt(failure, next.getAllowedActions());
        if (correctivePrompt != null && !correctivePrompt.isBlank()) {
            next.setPromptText(appendPrompt(next.getPromptText(), correctivePrompt));
        }
        return next;
    }

    private boolean shouldRetry(RuntimeException failure) {
        if (failure instanceof CandidateKnowledgeAssertionException) {
            return true;
        }
        if (!(failure instanceof OpenAiCompatibleResponseException responseException)) {
            return true;
        }
        String failureDomain = stringValue(responseException.diagnostics().get("failureDomain"));
        if ("transport".equalsIgnoreCase(failureDomain)) {
            return false;
        }
        String finishReason = stringValue(responseException.diagnostics().get("finishReason"));
        String contentShape = stringValue(responseException.diagnostics().get("assistantContentShape"));
        String message = failure.getMessage() == null ? "" : failure.getMessage();
        return requiresCompressionRetry(finishReason, contentShape, message);
    }

    private String appendPrompt(String originalPrompt, String correctivePrompt) {
        if (originalPrompt == null || originalPrompt.isBlank()) {
            return correctivePrompt;
        }
        return originalPrompt + System.lineSeparator() + System.lineSeparator() + correctivePrompt;
    }

    private String correctivePrompt(RuntimeException failure, List<String> allowedActions) {
        if (failure instanceof CandidateKnowledgeAssertionException knowledgeAssertionException) {
            return """
                    上一轮输出把候选身份说成了确定事实，请重新生成并严格遵守：
                    - 只有 exactRoleId 明确告诉你的身份，才能写成确定事实
                    - 对 candidateRoleIds 只能写“怀疑 / 可能 / 更像 / 倾向 / 猜测”，不能写“P5是梅林”“P3是莫甘娜”
                    - 这条规则至少适用于 privateThought 和 auditReason.reasonSummary
                    - action 仍然必须合法，且 %s
                    - 违规片段：%s
                    """.formatted(
                    actionRequirement(allowedActions),
                    knowledgeAssertionException.violationSummary()
            ).strip();
        }
        if (!(failure instanceof OpenAiCompatibleResponseException responseException)) {
            return "上一轮战略输出未通过宿主校验：" + failure.getMessage()
                    + "。请保留基于可见证据的策略，修正字段后重新返回完整 memoryUpdate 和合法 action。";
        }
        String finishReason = stringValue(responseException.diagnostics().get("finishReason"));
        String contentShape = stringValue(responseException.diagnostics().get("assistantContentShape"));
        String message = failure.getMessage() == null ? "" : failure.getMessage();
        if (requiresCompressionRetry(finishReason, contentShape, message)) {
            return """
                    上一轮输出没有满足结构化要求。请压缩措辞后重新生成完整战略 JSON：
                    - 最终回复只能是一个 JSON 对象，首字符必须是 {，尾字符必须是 }
                    - memoryUpdate 和 action 都是必填对象；%s
                    - memoryUpdate 必须保留 roleBeliefs、strategyState、communicationPlan、evidenceReferences 和 observedThroughSequence
                    - publicSpeech 只有在当前阶段需要公开发言时才提供
                    - privateThought 可省略或写 null；如果提供，只写一句极短中文
                    - auditReason 可省略；数组中只保留最关键证据，不得编造不可见事件编号
                    - 不要输出 <think>、解释、Markdown、代码块、项目符号或长分析
                    """.formatted(actionRequirement(allowedActions)).strip();
        }
        return null;
    }

    private boolean requiresCompressionRetry(String finishReason, String contentShape, String message) {
        if ("length".equalsIgnoreCase(finishReason)) {
            return true;
        }
        if (contentShape == null || contentShape.isBlank()) {
            return message.contains("did not include an action object");
        }
        return switch (contentShape) {
            case "truncated_json_candidate",
                 "plain_text",
                 "markdown_explanation",
                 "reasoning_only",
                 "missing_content" -> true;
            default -> message.contains("did not include an action object");
        };
    }

    private String actionRequirement(List<String> allowedActions) {
        if (allowedActions == null || allowedActions.isEmpty()) {
            return "action.actionType 必须是当前允许的动作";
        }
        if (allowedActions.size() == 1) {
            return "action.actionType 必须严格等于 " + allowedActions.get(0);
        }
        return "action.actionType 只能从 " + allowedActions.stream()
                .map(action -> action.toUpperCase(Locale.ROOT))
                .toList();
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String string = String.valueOf(value);
        return string.isBlank() ? null : string;
    }
}
