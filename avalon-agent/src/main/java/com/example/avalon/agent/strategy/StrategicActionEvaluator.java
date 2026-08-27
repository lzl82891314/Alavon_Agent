package com.example.avalon.agent.strategy;

import com.example.avalon.agent.analysis.StrategicEvidenceContext;
import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.core.player.memory.BehaviorPrediction;
import com.example.avalon.core.player.memory.PossibleWorld;
import com.example.avalon.core.player.memory.StrategicActionCandidate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Builds comparable, legal action alternatives from visible evidence without selecting an action. */
@Component
public final class StrategicActionEvaluator {
    public Map<String, Object> evaluate(AgentTurnRequest request, StrategicEvidenceContext evidence,
                                        List<BehaviorPrediction> persistedPredictions) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("predictionFeedback", predictionFeedback(request, persistedPredictions));
        result.put("actionCandidates", actionCandidates(request, evidence));
        return result;
    }

    private List<Map<String, Object>> predictionFeedback(AgentTurnRequest request,
                                                           List<BehaviorPrediction> predictions) {
        if (predictions == null || predictions.isEmpty()) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (BehaviorPrediction prediction : predictions) {
            List<Map<String, Object>> relevant = visibleEvents(request).stream()
                    .filter(event -> Objects.equals(prediction.subjectPlayerId(), string(event.get("actorPlayerId")))
                            || prediction.discriminatingObservationReferences().contains(sequence(event)))
                    .toList();
            String status = feedbackStatus(request, prediction, relevant);
            Map<String, Object> feedback = new LinkedHashMap<>();
            feedback.put("predictionId", prediction.predictionId());
            feedback.put("priorStatus", prediction.status());
            feedback.put("status", status);
            feedback.put("observedSequences", relevant.stream().map(this::sequence).filter(value -> value > 0).toList());
            feedback.put("reason", feedbackReason(status));
            result.add(feedback);
        }
        return List.copyOf(result);
    }

    private String feedbackStatus(AgentTurnRequest request, BehaviorPrediction prediction,
                                  List<Map<String, Object>> relevant) {
        if (request.getObservationToSequence() > prediction.validThroughSequence()) return "EXPIRED";
        if (relevant.isEmpty()) return "PENDING";
        Set<String> expected = prediction.expectedBehaviors().stream()
                .map(this::normalize).filter(value -> !value.isEmpty()).collect(java.util.stream.Collectors.toSet());
        if (expected.isEmpty()) return "INCONCLUSIVE";
        Set<String> observed = relevant.stream().flatMap(event -> eventSignatures(event).stream())
                .collect(java.util.stream.Collectors.toSet());
        if (observed.stream().anyMatch(expected::contains)) return "SUPPORTED";
        if (observed.stream().anyMatch(value -> contradictsExpected(value, expected))) return "CONTRADICTED";
        return "INCONCLUSIVE";
    }

    private String feedbackReason(String status) {
        return switch (status) {
            case "SUPPORTED" -> "A newly visible event matches a normalized expected behavior.";
            case "CONTRADICTED" -> "A newly visible event contains a rule-level opposite of an expected public choice.";
            case "EXPIRED" -> "The prediction validity window ended before a decisive visible match.";
            case "PENDING" -> "No newly visible event from the prediction subject is available.";
            default -> "Newly visible behavior cannot be compared to the prediction without adding an identity inference.";
        };
    }

    private List<StrategicActionCandidate> actionCandidates(AgentTurnRequest request,
                                                             StrategicEvidenceContext evidence) {
        List<StrategicActionCandidate> result = new ArrayList<>();
        for (String type : request.getAllowedActions()) {
            result.addAll(candidatesFor(type, request, evidence));
        }
        return List.copyOf(result);
    }

    private List<StrategicActionCandidate> candidatesFor(String type, AgentTurnRequest request,
                                                          StrategicEvidenceContext evidence) {
        return switch (type) {
            case "TEAM_PROPOSAL" -> teamCandidates(request, evidence);
            case "TEAM_VOTE" -> candidates(type, List.of(Map.of("vote", "APPROVE"), Map.of("vote", "REJECT")), evidence);
            case "MISSION_ACTION" -> missionCandidates(request, evidence);
            case "ASSASSINATION" -> assassinationCandidates(request, evidence);
            case "PUBLIC_SPEECH" -> speechCandidates(request, evidence);
            default -> List.of();
        };
    }

    private List<StrategicActionCandidate> teamCandidates(AgentTurnRequest request,
                                                           StrategicEvidenceContext evidence) {
        List<Map<String, Object>> actions = evidence.teamCandidates().stream()
                .map(item -> strings(item.get("team")))
                .filter(team -> team.size() == number(request.getPublicState().get("teamSize")))
                .distinct().limit(2)
                .map(team -> Map.<String, Object>of("selectedPlayerIds", team,
                        "followUpObservation", "Compare the public vote and mission result against this team."))
                .toList();
        return candidates("TEAM_PROPOSAL", actions, evidence);
    }

    private List<StrategicActionCandidate> missionCandidates(AgentTurnRequest request,
                                                              StrategicEvidenceContext evidence) {
        String camp = string(request.getPrivateKnowledge().get("camp"));
        List<Map<String, Object>> actions = new ArrayList<>();
        actions.add(Map.of("choice", "SUCCESS", "followUpObservation", "Observe the public mission result and subsequent explanations."));
        if ("EVIL".equals(camp)) {
            actions.add(Map.of("choice", "FAIL", "followUpObservation", "Observe the public mission result and subsequent explanations."));
        } else {
            actions.add(Map.of("choice", "SUCCESS", "communicationStrategy", "request public accountability after the result"));
        }
        return candidates("MISSION_ACTION", actions, evidence);
    }

    private List<StrategicActionCandidate> assassinationCandidates(AgentTurnRequest request,
                                                                    StrategicEvidenceContext evidence) {
        List<Map<String, Object>> actions = playerIds(request).stream()
                .filter(playerId -> !Objects.equals(playerId, request.getPlayerId())).limit(2)
                .map(playerId -> Map.<String, Object>of("targetPlayerId", playerId,
                        "followUpObservation", "This terminal action should be compared against alternative worlds first."))
                .toList();
        return candidates("ASSASSINATION", actions, evidence);
    }

    private List<StrategicActionCandidate> speechCandidates(AgentTurnRequest request,
                                                             StrategicEvidenceContext evidence) {
        List<String> acts = strings(request.getDiscussionDirective().get("allowedSpeechActs"));
        if (acts.isEmpty()) return List.of();
        List<Map<String, Object>> actions = acts.stream().distinct().limit(2)
                .map(act -> Map.<String, Object>of("speechAct", act,
                        "communicationStrategy", "state the visible evidence and request a falsifiable response"))
                .toList();
        if (actions.size() == 1) {
            actions = List.of(actions.get(0), Map.of("speechAct", acts.get(0),
                    "communicationStrategy", "state an alternative explanation and request a falsifiable response"));
        }
        return candidates("PUBLIC_SPEECH", actions, evidence);
    }

    private List<StrategicActionCandidate> candidates(String type, List<Map<String, Object>> actions,
                                                       StrategicEvidenceContext evidence) {
        List<Long> references = evidenceReferences(evidence);
        Map<String, Double> worlds = worldOutcomes(evidence.possibleWorlds());
        List<StrategicActionCandidate> result = new ArrayList<>();
        for (int index = 0; index < actions.size(); index++) {
            double evidenceFactor = Math.min(0.30d, references.size() * 0.05d);
            double informationGain = "PUBLIC_SPEECH".equals(type) || "TEAM_PROPOSAL".equals(type)
                    ? 0.45d + evidenceFactor : 0.25d + evidenceFactor;
            double executionRisk = "ASSASSINATION".equals(type) ? 0.70d : 0.20d + index * 0.05d;
            result.add(new StrategicActionCandidate(type + "-" + (index + 1), type, actions.get(index), worlds,
                    0.50d, Math.min(1.0d, informationGain), 0.15d + index * 0.05d,
                    0.10d + index * 0.05d, executionRisk, references, references));
        }
        return List.copyOf(result);
    }

    private Map<String, Double> worldOutcomes(List<PossibleWorld> worlds) {
        if (worlds == null || worlds.isEmpty()) return Map.of("PUBLIC_BASELINE", 0.50d);
        Map<String, Double> result = new LinkedHashMap<>();
        worlds.stream().sorted(Comparator.comparing(PossibleWorld::worldId))
                .forEach(world -> result.put(world.worldId(), 0.50d));
        return Map.copyOf(result);
    }

    private List<Long> evidenceReferences(StrategicEvidenceContext evidence) {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        evidence.evidenceAssessments().forEach(item -> {
            if (item.evidenceSequence() > 0) result.add(item.evidenceSequence());
        });
        evidence.teamCandidates().forEach(item -> numbers(item.get("sourceSequences")).forEach(result::add));
        return result.stream().limit(12).toList();
    }

    private List<Map<String, Object>> visibleEvents(AgentTurnRequest request) {
        return request.getObservationDelta() == null ? List.of() : request.getObservationDelta();
    }

    private Collection<String> eventSignatures(Map<String, Object> event) {
        Set<String> result = new LinkedHashSet<>();
        String type = string(event.get("eventType"));
        if (type != null) result.add(normalize(type));
        Map<String, Object> facts = map(event.get("facts"));
        for (String key : List.of("speechAct", "vote", "choice", "result")) {
            String value = string(facts.get(key));
            if (value != null) {
                result.add(normalize(value));
                if (type != null) result.add(normalize(type + "_" + value));
            }
        }
        return result;
    }

    private List<String> playerIds(AgentTurnRequest request) {
        Object raw = request.getPublicState().get("players");
        if (!(raw instanceof List<?> players)) return List.of();
        return players.stream().filter(Map.class::isInstance).map(Map.class::cast)
                .map(player -> string(player.get("playerId"))).filter(Objects::nonNull).sorted().toList();
    }

    private List<String> strings(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        return values.stream().map(String::valueOf).toList();
    }

    private List<Long> numbers(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        return values.stream().filter(Number.class::isInstance).map(Number.class::cast)
                .map(Number::longValue).filter(number -> number > 0).toList();
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private long sequence(Map<String, Object> event) {
        Object value = event.get("sequence");
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private String string(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_|_$", "");
    }

    private boolean contradictsExpected(String observed, Set<String> expected) {
        return ("APPROVE".equals(observed) && expected.contains("REJECT"))
                || ("REJECT".equals(observed) && expected.contains("APPROVE"))
                || ("SUCCESS".equals(observed) && expected.contains("FAIL"))
                || ("FAIL".equals(observed) && expected.contains("SUCCESS"));
    }
}
