package com.example.avalon.agent.gateway;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.agent.model.MemoryUpdate;
import com.example.avalon.agent.model.RawCompletionMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Offline strategic baseline used when no model provider is configured. */
@Component
public final class NoopAgentGateway implements AgentGateway {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Override
    public AgentTurnResult playTurn(AgentTurnRequest request) {
        Map<String, Double> beliefs = revisedBeliefs(request);
        String speech = strategicSpeech(request, beliefs);
        AgentTurnResult result = new AgentTurnResult();
        result.setPublicSpeech(speech);
        result.setActionJson(write(action(request, beliefs, speech)));
        result.setMemoryUpdate(memoryUpdate(request, beliefs, speech));
        RawCompletionMetadata metadata = new RawCompletionMetadata();
        metadata.setProvider("noop");
        metadata.setModelName("evidence-baseline-v1");
        metadata.setAttributes(Map.of("baseline", true));
        result.setModelMetadata(metadata);
        return result;
    }

    private Map<String, Double> revisedBeliefs(AgentTurnRequest request) {
        Map<String, Double> beliefs = new LinkedHashMap<>();
        Object existing = request.getMemory().get("roleBeliefs");
        if (existing instanceof Map<?, ?> values) {
            values.forEach((player, probability) -> {
                if (probability instanceof Number number) beliefs.put(String.valueOf(player), number.doubleValue());
            });
        }
        for (Map<String, Object> player : players(request)) {
            String playerId = String.valueOf(player.get("playerId"));
            beliefs.putIfAbsent(playerId, playerId.equals(request.getPlayerId())
                    ? ("EVIL".equals(String.valueOf(request.getPrivateKnowledge().get("camp"))) ? 1.0d : 0.0d)
                    : knownCampProbability(request, playerId));
        }
        for (Map<String, Object> event : request.getObservationDelta()) {
            String type = String.valueOf(event.get("eventType"));
            if ("MISSION_FAILED".equals(type)) {
                currentTeam(request).forEach(player -> beliefs.computeIfPresent(player,
                        (ignored, value) -> Math.min(0.9d, value + 0.15d)));
            }
        }
        return beliefs;
    }

    private double knownCampProbability(AgentTurnRequest request, String playerId) {
        Object visible = request.getPrivateKnowledge().get("visiblePlayers");
        if (visible instanceof List<?> players) {
            for (Object item : players) {
                if (item instanceof Map<?, ?> player && playerId.equals(String.valueOf(player.get("playerId")))) {
                    return "EVIL".equals(String.valueOf(player.get("camp"))) ? 1.0d : 0.0d;
                }
            }
        }
        return 0.4d;
    }

    private MemoryUpdate memoryUpdate(AgentTurnRequest request, Map<String, Double> beliefs, String speech) {
        MemoryUpdate update = new MemoryUpdate();
        update.setRoleBeliefs(beliefs);
        update.setEvidenceReferences(request.getObservationDelta().stream()
                .map(event -> event.get("sequence"))
                .filter(Number.class::isInstance).map(Number.class::cast).map(Number::longValue).toList());
        Map<String, List<Long>> beliefEvidence = new LinkedHashMap<>();
        for (String playerId : beliefs.keySet()) {
            List<Long> references = request.getObservationDelta().stream()
                    .filter(event -> containsPlayer(event, playerId))
                    .map(event -> event.get("sequence"))
                    .filter(Number.class::isInstance).map(Number.class::cast).map(Number::longValue).toList();
            if (!references.isEmpty()) beliefEvidence.put(playerId, references);
        }
        update.setBeliefEvidenceReferences(beliefEvidence);
        update.setStrategyMode("INFORMATION_SEEKING");
        update.setStrategyState(Map.of(
                "mode", "INFORMATION_SEEKING",
                "objective", "test the highest-risk public hypothesis",
                "unresolvedQuestions", List.of(),
                "publicCommitments", List.of(),
                "coverStory", Map.of(),
                "deceptionIntent", "NONE",
                "consistencyRisks", List.of()));
        update.setCommunicationPlan(Map.of(
                "speechAct", speechAct(request),
                "desiredAudienceBeliefs", Map.of(),
                "evidenceToMention", update.getEvidenceReferences(),
                "evidenceToWithhold", List.of(),
                "publicMessage", speech == null ? "" : speech));
        update.setLastSummary("Updated beliefs from visible rule events and selected an information-seeking action.");
        return update;
    }

    private Map<String, Object> action(AgentTurnRequest request, Map<String, Double> beliefs, String speech) {
        String type = request.getAllowedActions().get(0);
        return switch (type) {
            case "PUBLIC_SPEECH" -> Map.of(
                    "actionType", type, "speechText", speech, "speechAct", speechAct(request),
                    "mentions", mentions(request, beliefs), "replyToEventSequences", replies(request));
            case "TEAM_PROPOSAL" -> Map.of("actionType", type, "selectedPlayerIds", proposal(request, beliefs));
            case "TEAM_VOTE" -> Map.of("actionType", type, "vote", teamRisk(request, beliefs) <= 0.5d ? "APPROVE" : "REJECT");
            case "MISSION_ACTION" -> Map.of("actionType", type, "choice", missionChoice(request));
            case "ASSASSINATION" -> Map.of("actionType", type, "targetPlayerId", assassinationTarget(request, beliefs));
            default -> throw new IllegalStateException("Unsupported action type " + type);
        };
    }

    private String strategicSpeech(AgentTurnRequest request, Map<String, Double> beliefs) {
        if (!request.getAllowedActions().contains("PUBLIC_SPEECH")) return null;
        String stage = String.valueOf(request.getDiscussionDirective().get("stage"));
        String target = highestRiskOther(request, beliefs);
        return switch (stage) {
            case "CHALLENGE_WINDOW" -> target + "，你的公开立场与当前队伍选择之间有什么可验证的依据？";
            case "TARGETED_RESPONSES" -> "我回应这项质疑：我的判断只基于已公开的提案、投票和任务结果；新证据出现时我会修正。";
            case "LEADER_SYNTHESIS" -> "目前应优先选择公开风险较低且能区分主要假设的队伍，争议点会由本轮结果继续验证。";
            default -> "我不会把任何人的公开主张直接当成身份事实；本轮重点比较组队、投票与后续任务结果是否一致。";
        };
    }

    private String speechAct(AgentTurnRequest request) {
        Object allowed = request.getDiscussionDirective().get("allowedSpeechActs");
        if (allowed instanceof List<?> values && !values.isEmpty()) return String.valueOf(values.get(0));
        return "STATE_OPINION";
    }

    private List<String> mentions(AgentTurnRequest request, Map<String, Double> beliefs) {
        return "CHALLENGE_WINDOW".equals(String.valueOf(request.getDiscussionDirective().get("stage")))
                ? List.of(highestRiskOther(request, beliefs)) : List.of();
    }

    private List<Long> replies(AgentTurnRequest request) {
        Object sequence = request.getDiscussionDirective().get("replyToEventSequence");
        return sequence instanceof Number number ? List.of(number.longValue()) : List.of();
    }

    private List<String> proposal(AgentTurnRequest request, Map<String, Double> beliefs) {
        int teamSize = ((Number) request.getPublicState().getOrDefault("teamSize", 2)).intValue();
        return players(request).stream().map(player -> String.valueOf(player.get("playerId")))
                .sorted(Comparator.comparingDouble(player -> beliefs.getOrDefault(player, 0.5d)))
                .limit(teamSize).toList();
    }

    private double teamRisk(AgentTurnRequest request, Map<String, Double> beliefs) {
        List<String> team = currentTeam(request);
        return team.isEmpty() ? 1.0d : team.stream().mapToDouble(player -> beliefs.getOrDefault(player, 0.5d)).average().orElse(1.0d);
    }

    private String missionChoice(AgentTurnRequest request) {
        if (!"EVIL".equals(String.valueOf(request.getPrivateKnowledge().get("camp")))) return "SUCCESS";
        long evilOnTeam = currentTeam(request).stream().filter(thisPlayer -> knownEvil(request, thisPlayer)).count();
        return evilOnTeam > 1 && request.getRoundNo() == 1 ? "SUCCESS" : "FAIL";
    }

    private boolean knownEvil(AgentTurnRequest request, String playerId) {
        if (playerId.equals(request.getPlayerId())) return true;
        Object visible = request.getPrivateKnowledge().get("visiblePlayers");
        if (!(visible instanceof List<?> players)) return false;
        return players.stream().filter(Map.class::isInstance).map(Map.class::cast)
                .anyMatch(player -> playerId.equals(String.valueOf(player.get("playerId")))
                        && "EVIL".equals(String.valueOf(player.get("camp"))));
    }

    private String assassinationTarget(AgentTurnRequest request, Map<String, Double> beliefs) {
        return players(request).stream().map(player -> String.valueOf(player.get("playerId")))
                .filter(player -> !player.equals(request.getPlayerId()))
                .min(Comparator.comparingDouble(player -> beliefs.getOrDefault(player, 0.5d))).orElseThrow();
    }

    private String highestRiskOther(AgentTurnRequest request, Map<String, Double> beliefs) {
        return beliefs.entrySet().stream().filter(entry -> !entry.getKey().equals(request.getPlayerId()))
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey)
                .orElseGet(() -> players(request).stream().map(player -> String.valueOf(player.get("playerId")))
                        .filter(player -> !player.equals(request.getPlayerId())).findFirst().orElse(request.getPlayerId()));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> players(AgentTurnRequest request) {
        return (List<Map<String, Object>>) request.getPublicState().getOrDefault("players", List.of());
    }

    @SuppressWarnings("unchecked")
    private List<String> currentTeam(AgentTurnRequest request) {
        return (List<String>) request.getPublicState().getOrDefault("currentTeamPlayerIds", List.of());
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Cannot serialize baseline action", exception); }
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
}
