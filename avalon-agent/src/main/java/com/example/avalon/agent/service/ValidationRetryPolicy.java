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
    private static final int MIN_CORRECTIVE_MAX_TOKENS = 320;
    private static final int CORRECTIVE_TOKEN_INCREMENT = 320;

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

    private AgentTurnRequest nextAttemptRequest(AgentTurnRequest request, RuntimeException failure) {
        AgentTurnRequest next = request.copy();
        String correctivePrompt = correctivePrompt(failure, next.getAllowedActions());
        if (correctivePrompt != null && !correctivePrompt.isBlank()) {
            next.setPromptText(appendPrompt(next.getPromptText(), correctivePrompt));
            next.setMaxTokens(raisedMaxTokens(next.getMaxTokens()));
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

    private int raisedMaxTokens(Integer currentMaxTokens) {
        int current = currentMaxTokens == null ? 0 : currentMaxTokens;
        return Math.max(current, MIN_CORRECTIVE_MAX_TOKENS) + CORRECTIVE_TOKEN_INCREMENT;
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
            return null;
        }
        String finishReason = stringValue(responseException.diagnostics().get("finishReason"));
        String contentShape = stringValue(responseException.diagnostics().get("assistantContentShape"));
        String message = failure.getMessage() == null ? "" : failure.getMessage();
        if (requiresCompressionRetry(finishReason, contentShape, message)) {
            return """
                    上一轮输出没有满足结构化要求。请重新生成最小合法 JSON，并优先先写 action：
                    - 最终回复只能是一个 JSON 对象，首字符必须是 {，尾字符必须是 }
                    - 第一层键顺序优先写 action，再写 publicSpeech、privateThought、auditReason、memoryUpdate
                    - action 必填，且 %s
                    - publicSpeech 只有在当前阶段需要公开发言时才提供
                    - privateThought 可省略或写 null；如果提供，只写一句极短中文
                    - auditReason 和 memoryUpdate 默认省略，除非确有必要
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
