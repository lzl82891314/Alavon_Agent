package com.example.avalon.agent.service;

import com.example.avalon.agent.gateway.AgentGateway;
import com.example.avalon.agent.gateway.OpenAiCompatibleResponseException;
import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.core.game.model.PlayerAction;
import com.example.avalon.core.game.model.PlayerTurnContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class ValidationRetryPolicy {
    private static final int DEFAULT_MAX_ATTEMPTS = 2;

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
                validateStrategicOutput(context, result);
                PlayerAction action = responseParser.parse(context, result);
                privateKnowledgeExpressionValidator.validate(context, result);
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

    private void validateStrategicOutput(PlayerTurnContext context, AgentTurnResult result) {
        if (result == null || result.getMemoryUpdate() == null) {
            throw new IllegalStateException("Strategic output must include memoryUpdate");
        }
        java.util.Set<Long> visibleSequences = context.observations().events().stream()
                .map(event -> event.sequence()).collect(java.util.stream.Collectors.toSet());
        java.util.Set<Long> retainedSequences = new java.util.HashSet<>();
        context.memoryState().worldFacts().forEach(fact -> addSequence(retainedSequences, fact.get("sequence")));
        context.memoryState().publicClaims().forEach(claim -> addSequence(retainedSequences, claim.get("sequence")));
        for (Long reference : result.getMemoryUpdate().getEvidenceReferences()) {
            if (reference == null || (!visibleSequences.contains(reference) && !retainedSequences.contains(reference))) {
                throw new IllegalStateException("Evidence reference is not visible to this agent: " + reference);
            }
        }
        result.getMemoryUpdate().getRoleBeliefs().forEach((player, probability) -> {
            if (probability == null || probability < 0.0d || probability > 1.0d) {
                throw new IllegalStateException("Invalid role belief probability for " + player);
            }
        });
    }

    private void addSequence(java.util.Set<Long> target, Object value) {
        if (value instanceof Number number) target.add(number.longValue());
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
