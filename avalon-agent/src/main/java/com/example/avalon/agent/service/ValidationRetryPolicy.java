package com.example.avalon.agent.service;

import com.example.avalon.agent.gateway.AgentGateway;
import com.example.avalon.agent.gateway.OpenAiCompatibleResponseException;
import com.example.avalon.agent.gateway.OpenAiCompatibleTransportException;
import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.agent.model.MemoryUpdate;
import com.example.avalon.agent.strategy.RoleStrategyPolicy;
import com.example.avalon.core.game.model.PlayerAction;
import com.example.avalon.core.game.model.PlayerTurnContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class ValidationRetryPolicy {
    private static final Logger LOGGER = LoggerFactory.getLogger(ValidationRetryPolicy.class);
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final int DEFAULT_MAX_ATTEMPTS = DEFAULT_MAX_RETRIES + 1;
    private static final String OPTIONAL_SECTION_WARNINGS = "optionalSectionWarnings";
    private static final Pattern CHINESE_CHARACTER = Pattern.compile("[\\p{IsHan}]");
    private static final Pattern ENGLISH_WORD = Pattern.compile("[A-Za-z]{2,}");
    private static final Pattern PLAYER_ID = Pattern.compile("(?i)\\bP\\d+\\b");
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
                validateRequiredOutput(context, result, action);
                validateOptionalOutput(context, result, action);
                return new ValidatedAgentTurn(result, action, attempts, attemptRequest.copy());
            } catch (RuntimeException exception) {
                lastFailure = exception;
                boolean retryable = attempts < DEFAULT_MAX_ATTEMPTS && shouldRetry(exception);
                LOGGER.warn("agent_validation_failed gameId={} playerId={} phase={} modelId={} attempt={} retryable={} error={}",
                        request.getGameId(), request.getPlayerId(), request.getPhase(), request.getModelId(),
                        attempts, retryable, exception.getMessage());
                if (retryable) {
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

    private void validateRequiredOutput(PlayerTurnContext context, AgentTurnResult result, PlayerAction action) {
        if (action instanceof com.example.avalon.core.game.model.PublicSpeechAction speechAction) {
            requireSimplifiedChinese(speechAction.speechText(), "Public speech");
        }
        privateKnowledgeExpressionValidator.validatePublicAction(context, result, action);
    }

    private void validateOptionalOutput(PlayerTurnContext context, AgentTurnResult result, PlayerAction action) {
        try {
            privateKnowledgeExpressionValidator.validatePrivateSections(context, result);
        } catch (RuntimeException exception) {
            result.setPrivateThought(null);
            result.setAuditReason(null);
            discardOptionalSection(context, result, "privateThought/auditReason", exception);
        }
        if (result.getMemoryUpdate() == null) {
            recordMissingMemorySections(result);
            recordPrivateActionAssessment(context, result, action);
            return;
        }
        validateMemoryNotes(context, result);
        validateEvidenceAssessments(context, result);
        validateBeliefUpdate(context, result);
        validateStrategyState(context, result);
        validateCommunicationPlan(context, result, action);
        recordPrivateActionAssessment(context, result, action);
    }

    private void validateEvidenceAssessments(PlayerTurnContext context, AgentTurnResult result) {
        MemoryUpdate update = result.getMemoryUpdate();
        if (update.getEvidenceReferences().isEmpty()) {
            recordSectionStatus(result, "evidenceAssessments", "NOT_PROVIDED",
                    "memoryUpdate.evidenceReferences", null, null);
            return;
        }
        try {
            validateVisibleEvidence(context, update.getEvidenceReferences());
            recordSectionAccepted(result, "evidenceAssessments", "memoryUpdate.evidenceReferences");
        } catch (RuntimeException exception) {
            update.setEvidenceReferences(List.of());
            discardCognitionSection(context, result, "evidenceAssessments",
                    "memoryUpdate.evidenceReferences", exception);
        }
    }

    private void validateBeliefUpdate(PlayerTurnContext context, AgentTurnResult result) {
        MemoryUpdate update = result.getMemoryUpdate();
        if (update.getRoleBeliefs().isEmpty() && update.getBeliefEvidenceReferences().isEmpty()) {
            recordSectionStatus(result, "beliefUpdate", "NOT_PROVIDED",
                    "memoryUpdate.roleBeliefs", null, null);
            return;
        }
        try {
            validateBeliefs(context, result);
            recordSectionAccepted(result, "beliefUpdate", "memoryUpdate.roleBeliefs");
        } catch (RuntimeException exception) {
            update.setRoleBeliefs(Map.of());
            update.setBeliefEvidenceReferences(Map.of());
            discardCognitionSection(context, result, "beliefUpdate",
                    "memoryUpdate.roleBeliefs", exception);
        }
    }

    private void validateStrategyState(PlayerTurnContext context, AgentTurnResult result) {
        MemoryUpdate update = result.getMemoryUpdate();
        boolean provided = !update.getStrategyState().isEmpty()
                || update.getStrategyMode() != null || update.getLastSummary() != null;
        if (!provided) {
            recordSectionStatus(result, "strategyState", "NOT_PROVIDED",
                    "memoryUpdate.strategyState", null, null);
            return;
        }
        try {
            validateStrategyContract(context, update);
            recordSectionAccepted(result, "strategyState", "memoryUpdate.strategyState");
        } catch (RuntimeException exception) {
            update.setStrategyState(Map.of());
            update.setStrategyMode(null);
            update.setLastSummary(null);
            discardCognitionSection(context, result, "strategyState",
                    "memoryUpdate.strategyState", exception);
        }
    }

    private void validateCommunicationPlan(PlayerTurnContext context, AgentTurnResult result, PlayerAction action) {
        MemoryUpdate update = result.getMemoryUpdate();
        if (update.getCommunicationPlan().isEmpty()) {
            recordSectionStatus(result, "communicationPlan", "NOT_PROVIDED",
                    "memoryUpdate.communicationPlan", null, null);
            return;
        }
        try {
            validateCommunicationContract(update, action);
            recordSectionAccepted(result, "communicationPlan", "memoryUpdate.communicationPlan");
        } catch (RuntimeException exception) {
            update.setCommunicationPlan(Map.of());
            discardCognitionSection(context, result, "communicationPlan",
                    "memoryUpdate.communicationPlan", exception);
        }
    }

    private void validateMemoryNotes(PlayerTurnContext context, AgentTurnResult result) {
        MemoryUpdate update = result.getMemoryUpdate();
        boolean provided = !update.getSuspicionDelta().isEmpty() || !update.getTrustDelta().isEmpty()
                || !update.getObservationsToAdd().isEmpty() || !update.getCommitmentsToAdd().isEmpty()
                || !update.getInferredFactsToAdd().isEmpty();
        if (provided) {
            recordSectionAccepted(result, "memoryNotes", "memoryUpdate");
        } else {
            recordSectionStatus(result, "memoryNotes", "NOT_PROVIDED", "memoryUpdate", null, null);
        }
    }

    private void validateVisibleEvidence(PlayerTurnContext context, List<Long> references) {
        Map<Long, Object> availableEvidence = new java.util.LinkedHashMap<>();
        context.observations().events().forEach(event -> availableEvidence.put(event.sequence(), event));
        context.memoryState().worldFacts().forEach(fact -> addEvidence(availableEvidence, fact));
        context.memoryState().publicClaims().forEach(claim -> addEvidence(availableEvidence, claim));
        for (Long reference : references) {
            if (reference == null || !availableEvidence.containsKey(reference)) {
                throw new IllegalStateException("Evidence reference is not visible to this agent: " + reference);
            }
        }
    }

    private void validateBeliefs(PlayerTurnContext context, AgentTurnResult result) {
        MemoryUpdate update = result.getMemoryUpdate();
        Map<Long, Object> availableEvidence = new java.util.LinkedHashMap<>();
        context.observations().events().forEach(event -> availableEvidence.put(event.sequence(), event));
        context.memoryState().worldFacts().forEach(fact -> addEvidence(availableEvidence, fact));
        context.memoryState().publicClaims().forEach(claim -> addEvidence(availableEvidence, claim));
        update.getBeliefEvidenceReferences().forEach((playerId, references) -> {
            if (!update.getRoleBeliefs().containsKey(playerId)) {
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
        update.getRoleBeliefs().forEach((player, probability) -> {
            if (probability == null || probability < 0.0d || probability > 1.0d) {
                throw new IllegalStateException("Invalid role belief probability for " + player);
            }
            validateBeliefChange(context, result, player, probability);
        });
    }

    private void validateStrategyContract(PlayerTurnContext context, MemoryUpdate update) {
        Map<String, Object> strategy = update.getStrategyState();
        requireNonBlank(strategy, "mode", "strategyState.mode");
        requireNonBlank(strategy, "objective", "strategyState.objective");
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

    private void validateCommunicationContract(MemoryUpdate update, PlayerAction action) {
        Map<String, Object> communication = update.getCommunicationPlan();
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
    }

    private void recordMissingMemorySections(AgentTurnResult result) {
        boolean malformedMemory = optionalWarningFor(result, "memoryUpdate");
        for (String section : List.of("memoryNotes", "evidenceAssessments", "beliefUpdate",
                "strategyState", "communicationPlan")) {
            String status = malformedMemory ? "DISCARDED" : "NOT_PROVIDED";
            recordSectionStatus(result, section, status, "memoryUpdate",
                    malformedMemory ? "deserialization_failed" : null, null);
        }
    }

    private void recordPrivateActionAssessment(PlayerTurnContext context, AgentTurnResult result, PlayerAction action) {
        if (!(action instanceof com.example.avalon.core.game.model.TeamVoteAction)
                && !(action instanceof com.example.avalon.core.game.model.MissionAction)) {
            recordSectionStatus(result, "privateActionAssessment", "NOT_PROVIDED",
                    "privateActionAssessment", null, null);
            return;
        }
        Map<String, Object> assessment = new LinkedHashMap<>();
        assessment.put("actionType", action.actionType().name());
        if (action instanceof com.example.avalon.core.game.model.TeamVoteAction vote) {
            assessment.put("selectedCandidate", vote.vote().name());
        } else if (action instanceof com.example.avalon.core.game.model.MissionAction mission) {
            assessment.put("selectedCandidate", mission.choice().name());
        }
        MemoryUpdate update = result.getMemoryUpdate();
        if (update != null) {
            assessment.put("decisiveEvidenceRefs", List.copyOf(update.getEvidenceReferences()));
            Object objective = update.getStrategyState().get("objective");
            if (objective != null) assessment.put("primaryObjective", String.valueOf(objective));
        }
        assessment.put("validAtSequence", context.observations().toSequenceInclusive());
        result.setPrivateActionAssessment(Map.copyOf(assessment));
        recordSectionAccepted(result, "privateActionAssessment", "privateActionAssessment");
    }

    private void discardCognitionSection(PlayerTurnContext context, AgentTurnResult result,
                                         String section, String field, RuntimeException failure) {
        discardOptionalSection(context, result, field, failure);
        recordSectionStatus(result, section, "DISCARDED", field, "semantic_validation_failed",
                failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
    }

    private void recordSectionAccepted(AgentTurnResult result, String section, String field) {
        recordSectionStatus(result, section, "ACCEPTED", field, null, null);
    }

    private void recordSectionStatus(AgentTurnResult result, String section, String status,
                                     String field, String reason, String message) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("status", status);
        detail.put("field", field);
        if (reason != null) detail.put("reason", reason);
        if (message != null) detail.put("message", message);
        result.getCognitionSectionStatuses().put(section, Map.copyOf(detail));
        List<String> accepted = result.getCognitionSectionStatuses().entrySet().stream()
                .filter(entry -> "ACCEPTED".equals(entry.getValue().get("status")))
                .map(Map.Entry::getKey)
                .toList();
        result.setAcceptedCognitionSections(accepted);
        result.setCognitionDegraded(result.getCognitionSectionStatuses().values().stream()
                .anyMatch(value -> "DISCARDED".equals(value.get("status"))));
    }

    private boolean optionalWarningFor(AgentTurnResult result, String fieldPrefix) {
        Object warnings = result.getModelMetadata().getAttributes().get(OPTIONAL_SECTION_WARNINGS);
        if (!(warnings instanceof Iterable<?> values)) return false;
        for (Object value : values) {
            if (value instanceof Map<?, ?> warning
                    && String.valueOf(warning.get("field")).startsWith(fieldPrefix)) return true;
        }
        return false;
    }

    private void discardOptionalSection(PlayerTurnContext context, AgentTurnResult result,
                                        String section, RuntimeException failure) {
        Map<String, Object> attributes = result.getModelMetadata().getAttributes();
        Object existing = attributes.get(OPTIONAL_SECTION_WARNINGS);
        List<Map<String, Object>> warnings = new java.util.ArrayList<>();
        if (existing instanceof Iterable<?> values) {
            for (Object value : values) {
                if (value instanceof Map<?, ?> map) {
                    Map<String, Object> copy = new java.util.LinkedHashMap<>();
                    map.forEach((key, item) -> copy.put(String.valueOf(key), item));
                    warnings.add(Map.copyOf(copy));
                }
            }
        }
        Map<String, Object> warning = new java.util.LinkedHashMap<>();
        warning.put("field", section);
        warning.put("reason", "semantic_validation_failed");
        warning.put("message", failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
        warnings.add(Map.copyOf(warning));
        attributes.put(OPTIONAL_SECTION_WARNINGS, List.copyOf(warnings));
        LOGGER.warn("agent_optional_section_discarded gameId={} playerId={} phase={} section={} error={}",
                context.gameId(), context.playerId(), context.phase(), section, warning.get("message"));
    }

    private void requireNonBlank(Map<String, Object> values, String key, String label) {
        Object value = values.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalStateException(label + " is required");
        }
    }

    private void requireSimplifiedChinese(String value, String label) {
        String withoutPlayerIds = PLAYER_ID.matcher(value == null ? "" : value).replaceAll("");
        if (!CHINESE_CHARACTER.matcher(withoutPlayerIds).find()
                || ENGLISH_WORD.matcher(withoutPlayerIds).find()) {
            throw new IllegalStateException(label + " must use Simplified Chinese");
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
        String correctivePrompt = correctivePrompt(failure, next);
        if (correctivePrompt != null && !correctivePrompt.isBlank()) {
            next.setPromptText(appendPrompt(next.getPromptText(), correctivePrompt));
        }
        return next;
    }

    private boolean shouldRetry(RuntimeException failure) {
        if (failure instanceof CandidateKnowledgeAssertionException) {
            return true;
        }
        if (failure instanceof OpenAiCompatibleTransportException transportException) {
            return "stream_interrupted".equalsIgnoreCase(
                    stringValue(transportException.diagnostics().get("failureKind")));
        }
        if (!(failure instanceof OpenAiCompatibleResponseException responseException)) {
            return true;
        }
        String failureDomain = stringValue(responseException.diagnostics().get("failureDomain"));
        String failureKind = stringValue(responseException.diagnostics().get("failureKind"));
        if ("stream_interrupted".equalsIgnoreCase(failureKind)) {
            return true;
        }
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

    private String correctivePrompt(RuntimeException failure, AgentTurnRequest request) {
        List<String> allowedActions = request.getAllowedActions();
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
            if (failure.getMessage() != null && failure.getMessage().endsWith(" must use Simplified Chinese")) {
                return "上一轮公开发言包含英文句子。重新生成时，publicSpeech 必须使用简体中文；"
                        + "只允许 P1 之类的玩家编号以及 JSON 字段名和枚举保留英文。请返回合法 action。";
            }
            if (failure.getMessage() != null
                    && failure.getMessage().startsWith("Role belief contradicts private camp knowledge for ")) {
                return "上一轮 roleBeliefs 与私有确定知识冲突。重新生成时必须逐字遵守这些固定值："
                        + privateCampBeliefs(request)
                        + "。这些值不需要公开说出，但 memoryUpdate.roleBeliefs 中不得改成中间概率。"
                        + " action 仍然必须合法，且 " + actionRequirement(allowedActions) + "。";
            }
            if (failure.getMessage() != null
                    && failure.getMessage().startsWith("Role belief changed too far without sufficient evidence for ")) {
                String playerId = failure.getMessage().substring(failure.getMessage().lastIndexOf(' ') + 1);
                return "上一轮 roleBeliefs 中 " + playerId + " 的变化超过宿主允许范围。"
                        + "重新生成时必须把 " + playerId + " 限制在 " + beliefRange(request, playerId)
                        + "；其他没有直接证据的未知玩家也应保持既有值。"
                        + " action 仍然必须合法，且 " + actionRequirement(allowedActions) + "。";
            }
            return "上一轮战略输出未通过宿主校验：" + failure.getMessage()
                    + "。请修正并重新返回合法 action；memoryUpdate 是可选认知草稿，无法保证合法时应省略。";
        }
        String finishReason = stringValue(responseException.diagnostics().get("finishReason"));
        String contentShape = stringValue(responseException.diagnostics().get("assistantContentShape"));
        String message = failure.getMessage() == null ? "" : failure.getMessage();
        if (requiresCompressionRetry(finishReason, contentShape, message)) {
            return """
                    上一轮输出没有满足结构化要求。请压缩措辞后重新生成完整战略 JSON：
                    - 最终回复只能是一个 JSON 对象，首字符必须是 {，尾字符必须是 }
                    - action 是必填对象；%s
                    - memoryUpdate 是可选认知草稿；若提供，应包含有效的 roleBeliefs、strategyState、communicationPlan 和 evidenceReferences
                    - publicSpeech 只有在当前阶段需要公开发言时才提供
                    - privateThought 可省略或写 null；如果提供，只写一句极短中文
                    - auditReason 可省略；数组中只保留最关键证据，不得编造不可见事件编号
                    - 不要输出 <think>、解释、Markdown、代码块、项目符号或长分析
                    """.formatted(actionRequirement(allowedActions)).strip();
        }
        return null;
    }

    private String privateCampBeliefs(AgentTurnRequest request) {
        Map<String, Double> beliefs = new java.util.LinkedHashMap<>();
        Object ownCamp = request.getPrivateKnowledge().get("camp");
        Double ownBelief = campBelief(ownCamp);
        if (ownBelief != null) {
            beliefs.put(request.getPlayerId(), ownBelief);
        }
        Object visiblePlayers = request.getPrivateKnowledge().get("visiblePlayers");
        if (visiblePlayers instanceof Iterable<?> players) {
            for (Object player : players) {
                if (!(player instanceof Map<?, ?> values)) {
                    continue;
                }
                Object playerId = values.get("playerId");
                Double belief = campBelief(values.get("camp"));
                if (playerId != null && belief != null) {
                    beliefs.put(String.valueOf(playerId), belief);
                }
            }
        }
        return beliefs.toString();
    }

    private String beliefRange(AgentTurnRequest request, String playerId) {
        double baseline = 0.5d;
        Object roleBeliefs = request.getMemory().get("roleBeliefs");
        if (roleBeliefs instanceof Map<?, ?> values && values.get(playerId) instanceof Number number) {
            baseline = number.doubleValue();
        }
        double delta = roleBeliefs instanceof Map<?, ?> values && values.containsKey(playerId)
                ? NO_EVIDENCE_MAX_DELTA
                : INITIAL_PRIOR_MAX_DISTANCE;
        return "[%.2f, %.2f]".formatted(
                Math.max(0.0d, baseline - delta),
                Math.min(1.0d, baseline + delta));
    }

    private Double campBelief(Object camp) {
        if (camp == null) {
            return null;
        }
        return switch (String.valueOf(camp).toUpperCase(Locale.ROOT)) {
            case "GOOD" -> 0.0d;
            case "EVIL" -> 1.0d;
            default -> null;
        };
    }

    private boolean requiresCompressionRetry(String finishReason, String contentShape, String message) {
        if ("length".equalsIgnoreCase(finishReason)) {
            return true;
        }
        if (message.contains("not valid JSON")) {
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
                 "reasoning_json_object",
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
