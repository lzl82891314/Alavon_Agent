package com.example.avalon.testkit;

import com.example.avalon.runtime.model.GameEvent;
import com.example.avalon.runtime.model.RuntimeAuditEntry;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Scores observable strategic behavior without interpreting private chain of thought. */
public final class StrategicBehaviorEvaluator {
    private final com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper()
            .findAndRegisterModules();

    public StrategicBehaviorReport evaluate(Collection<GameEvent> events,
                                             Collection<RuntimeAuditEntry> audits) {
        List<GameEvent> speeches = events.stream()
                .filter(event -> "PLAYER_ACTION".equals(event.type()))
                .filter(event -> "PUBLIC_SPEECH".equals(event.payload().get("actionType")))
                .toList();
        List<GameEvent> challenges = speeches.stream()
                .filter(event -> List.of("QUESTION", "CHALLENGE_CONSISTENCY")
                        .contains(String.valueOf(event.payload().get("speechAct"))))
                .toList();
        List<GameEvent> responses = speeches.stream()
                .filter(event -> List.of("ANSWER", "DEFEND_SELF", "REVISE_POSITION")
                        .contains(String.valueOf(event.payload().get("speechAct"))))
                .toList();

        int beliefRevisions = 0;
        int groundedBeliefRevisions = 0;
        int deceptionPlans = 0;
        int narrativeChecks = 0;
        int consistentNarratives = 0;
        boolean evilAgentObserved = false;
        int validationFailures = 0;
        for (RuntimeAuditEntry audit : audits) {
            if (audit.errorMessage() != null || Boolean.FALSE.equals(audit.validationResult().get("valid"))) {
                validationFailures++;
            }
            Map<String, Object> update = map(audit.rawModelResponse().get("memoryUpdate"));
            Map<String, Object> proposedBeliefs = map(update.get("roleBeliefs"));
            Map<String, Object> inputMemory = map(audit.inputContext().get("memory"));
            Map<String, Object> priorBeliefs = map(inputMemory.get("roleBeliefs"));
            Map<String, Object> beliefBindings = map(update.get("beliefEvidenceReferences"));
            for (Map.Entry<String, Object> belief : proposedBeliefs.entrySet()) {
                if (priorBeliefs.containsKey(belief.getKey())
                        && !java.util.Objects.equals(priorBeliefs.get(belief.getKey()), belief.getValue())) {
                    beliefRevisions++;
                    if (!list(beliefBindings.get(belief.getKey())).isEmpty()) groundedBeliefRevisions++;
                }
            }
            Map<String, Object> strategy = map(update.get("strategyState"));
            String intent = String.valueOf(strategy.getOrDefault("deceptionIntent", "NONE"));
            if (!"NONE".equals(intent) || !map(strategy.get("coverStory")).isEmpty()) deceptionPlans++;
            Map<String, Object> priorStrategy = map(inputMemory.get("strategyState"));
            Map<String, Object> priorCoverStory = map(priorStrategy.get("coverStory"));
            if (!priorCoverStory.isEmpty()) {
                narrativeChecks++;
                Map<String, Object> nextCoverStory = map(strategy.get("coverStory"));
                Object revisionReason = strategy.get("revisionReason");
                if (priorCoverStory.equals(nextCoverStory)
                        || (revisionReason != null && !String.valueOf(revisionReason).isBlank())) {
                    consistentNarratives++;
                }
            }
            Map<String, Object> privateKnowledge = map(audit.inputContext().get("privateKnowledge"));
            if ("EVIL".equals(String.valueOf(privateKnowledge.get("camp")))) evilAgentObserved = true;
        }

        return new StrategicBehaviorReport(
                speeches.size(),
                challenges.size(),
                rate(challenges.stream().filter(event -> !list(event.payload().get("mentions")).isEmpty()).count(), challenges.size()),
                responses.size(),
                rate(responses.stream().filter(event -> !list(event.payload().get("replyToEventSequences")).isEmpty()).count(), responses.size()),
                beliefRevisions,
                rate(groundedBeliefRevisions, beliefRevisions),
                deceptionPlans,
                narrativeChecks == 0 ? 1.0d : rate(consistentNarratives, narrativeChecks),
                evilAgentObserved,
                validationFailures);
    }

    private double rate(long numerator, long denominator) {
        return denominator == 0 ? 0.0d : (double) numerator / denominator;
    }

    private Map<String, Object> map(Object value) {
        if (value == null) return Map.of();
        Map<?, ?> source;
        if (value instanceof Map<?, ?> existing) source = existing;
        else {
            try {
                source = json.convertValue(value, Map.class);
            } catch (IllegalArgumentException exception) {
                return Map.of();
            }
        }
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        source.forEach((key, nested) -> result.put(String.valueOf(key), nested));
        return result;
    }

    private List<?> list(Object value) {
        return value instanceof List<?> values ? values : List.of();
    }
}
