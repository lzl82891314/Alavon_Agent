package com.example.avalon.agent.analysis;

import com.example.avalon.agent.model.AgentTurnRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Extracts bounded, public, sequence-addressable evidence without identity conclusions. */
@Component
public final class DeterministicStrategicEvidenceAnalyzer {
    private static final int MAX_TEAM_CANDIDATES = 8;

    public StrategicEvidenceContext analyze(AgentTurnRequest request) {
        List<Map<String, Object>> events = observedEvents(request);
        List<Map<String, Object>> votes = new ArrayList<>();
        List<Map<String, Object>> teams = new ArrayList<>();
        List<Map<String, Object>> missions = new ArrayList<>();
        List<Map<String, Object>> claims = publicClaims(request);
        List<String> latestTeam = List.of();
        long latestSequence = request == null ? 0L : request.getObservationToSequence();

        for (Map<String, Object> event : events) {
            long sequence = sequence(event);
            latestSequence = Math.max(latestSequence, sequence);
            String type = string(event.get("eventType"));
            Map<String, Object> facts = map(event.get("facts"));
            if ("TEAM_PROPOSED".equals(type)) {
                latestTeam = strings(facts.get("playerIds"));
                teams.add(teamHistory(sequence, event, latestTeam));
            } else if ("TEAM_VOTES_REVEALED".equals(type)) {
                Map<String, Object> publicVotes = map(facts.get("votes"));
                for (Map.Entry<String, Object> vote : publicVotes.entrySet()) {
                    Map<String, Object> evidence = new LinkedHashMap<>();
                    evidence.put("revealSequence", sequence);
                    evidence.put("proposalSequence", latestTeam.isEmpty() ? null : previousTeamSequence(teams));
                    evidence.put("voter", voterId(request, vote.getKey()));
                    evidence.put("vote", String.valueOf(vote.getValue()));
                    evidence.put("proposedTeam", latestTeam);
                    evidence.put("source", "PUBLIC_TEAM_VOTE_REVEAL");
                    votes.add(evidence);
                }
            } else if ("MISSION_SUCCESS".equals(type) || "MISSION_FAILED".equals(type)) {
                missions.add(missionConstraint(sequence, type, facts));
            }
        }

        List<Map<String, Object>> contradictions = contradictions(votes, claims);
        return new StrategicEvidenceContext(latestSequence, votes, teams, missions, contradictions,
                teamCandidates(request, teams, missions));
    }

    private Map<String, Object> teamHistory(long sequence, Map<String, Object> event, List<String> team) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("proposalSequence", sequence);
        result.put("leader", event.get("actorPlayerId"));
        result.put("team", team);
        result.put("source", "PUBLIC_TEAM_PROPOSAL");
        return result;
    }

    private Map<String, Object> missionConstraint(long sequence, String type, Map<String, Object> facts) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sequence", sequence);
        result.put("roundNo", facts.get("roundNo"));
        result.put("team", strings(facts.get("teamPlayerIds")));
        result.put("result", type.endsWith("SUCCESS") ? "SUCCESS" : "FAILED");
        if (facts.containsKey("fails")) result.put("publicFailCount", facts.get("fails"));
        result.put("constraint", type.endsWith("SUCCESS")
                ? "All eligible mission choices were SUCCESS"
                : "At least one eligible evil player was in the mission; fail source is undisclosed");
        result.put("source", "PUBLIC_MISSION_RESULT");
        return result;
    }

    private List<Map<String, Object>> contradictions(List<Map<String, Object>> votes,
                                                      List<Map<String, Object>> claims) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> vote : votes) {
            String voter = string(vote.get("voter"));
            String actual = string(vote.get("vote"));
            for (Map<String, Object> claim : claims) {
                if (!Objects.equals(voter, claim.get("actorPlayerId"))) continue;
                if (Objects.equals(claim.get("position"), actual)) continue;
                List<String> claimedTeam = strings(claim.get("team"));
                List<String> proposedTeam = strings(vote.get("proposedTeam"));
                Map<String, Object> candidate = new LinkedHashMap<>();
                candidate.put("sequence", vote.get("revealSequence"));
                candidate.put("claimSequence", claim.get("sequence"));
                candidate.put("playerId", voter);
                candidate.put("claimedPosition", claim.get("position"));
                candidate.put("observedVote", actual);
                candidate.put("teamMatch", !claimedTeam.isEmpty() && sameTeam(proposedTeam, claimedTeam));
                candidate.put("status", "POSSIBLE_PUBLIC_CONTRADICTION");
                candidate.put("source", "PUBLIC_CLAIM_AND_VOTE");
                result.add(candidate);
            }
        }
        return result;
    }

    private List<Map<String, Object>> teamCandidates(AgentTurnRequest request,
                                                       List<Map<String, Object>> history,
                                                       List<Map<String, Object>> missions) {
        int size = Math.toIntExact(number(request == null ? null : request.getPublicState().get("teamSize"), 0));
        if (size <= 0) return List.of();
        List<String> players = players(request);
        List<List<String>> candidates = new ArrayList<>();
        for (Map<String, Object> item : history) addCandidate(candidates, strings(item.get("team")), size);
        if (request != null) addCandidate(candidates,
                strings(request.getPublicState().get("currentTeamPlayerIds")), size);
        combinations(players, size, 0, new ArrayList<>(), candidates);
        List<Map<String, Object>> result = new ArrayList<>();
        for (List<String> team : candidates.stream().limit(MAX_TEAM_CANDIDATES).toList()) {
            long failed = missions.stream().filter(item -> "FAILED".equals(item.get("result"))
                    && overlaps(team, strings(item.get("team")))).count();
            long successful = missions.stream().filter(item -> "SUCCESS".equals(item.get("result"))
                    && overlaps(team, strings(item.get("team")))).count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("team", team);
            item.put("historicalFailedMissionCount", failed);
            item.put("historicalSuccessfulMissionCount", successful);
            item.put("informationCoverage", team.stream().filter(player -> hasMixedHistory(player, missions)).count());
            item.put("relativeFeatures", List.of("history_overlap", "mission_outcome_constraints", "player_coverage"));
            item.put("sourceSequences", sourceSequences(history, missions, team));
            result.add(item);
        }
        return result;
    }

    private boolean hasMixedHistory(String player, List<Map<String, Object>> missions) {
        boolean success = missions.stream().anyMatch(item -> "SUCCESS".equals(item.get("result"))
                && strings(item.get("team")).contains(player));
        boolean failed = missions.stream().anyMatch(item -> "FAILED".equals(item.get("result"))
                && strings(item.get("team")).contains(player));
        return success && failed;
    }

    private List<Long> sourceSequences(List<Map<String, Object>> history,
                                       List<Map<String, Object>> missions, List<String> team) {
        List<Long> sequences = new ArrayList<>();
        history.stream().filter(item -> sameTeam(team, strings(item.get("team"))))
                .map(item -> number(item.get("proposalSequence"), 0)).filter(value -> value > 0)
                .forEach(sequences::add);
        missions.stream().filter(item -> overlaps(team, strings(item.get("team"))))
                .map(item -> number(item.get("sequence"), 0)).filter(value -> value > 0)
                .forEach(sequences::add);
        return sequences.stream().distinct().sorted().toList();
    }

    private List<Map<String, Object>> publicClaims(AgentTurnRequest request) {
        if (request == null) return List.of();
        List<Map<String, Object>> claims = new ArrayList<>(observedEvents(request).stream()
                .filter(event -> "PLAYER_ACTION".equals(event.get("eventType")))
                .filter(event -> event.get("facts") instanceof Map<?, ?>)
                .map(event -> claim(event, map(event.get("facts"))))
                .filter(Objects::nonNull).toList());
        Object remembered = request.getMemory().get("publicClaims");
        if (remembered instanceof List<?> values) {
            values.stream().filter(Map.class::isInstance).map(Map.class::cast)
                    .map(this::rememberedClaim).filter(Objects::nonNull).forEach(claims::add);
        }
        return claims.stream().collect(java.util.stream.Collectors.toMap(
                claim -> number(claim.get("sequence"), 0) + ":" + claim.get("actorPlayerId"),
                claim -> claim, (left, right) -> left, LinkedHashMap::new)).values().stream().toList();
    }

    private List<Map<String, Object>> observedEvents(AgentTurnRequest request) {
        if (request == null) return List.of();
        List<Map<String, Object>> events = new ArrayList<>();
        Object remembered = request.getMemory().get("worldFacts");
        if (remembered instanceof List<?> values) {
            values.stream().filter(Map.class::isInstance).map(Map.class::cast)
                    .map(this::stringMap).forEach(events::add);
        }
        events.addAll(request.getObservationDelta());
        return events.stream().filter(event -> sequence(event) > 0)
                .collect(java.util.stream.Collectors.toMap(this::sequence, event -> event,
                        (rememberedEvent, latestEvent) -> latestEvent, LinkedHashMap::new))
                .values().stream().sorted(java.util.Comparator.comparingLong(this::sequence)).toList();
    }

    private Map<String, Object> rememberedClaim(Map<?, ?> value) {
        Map<String, Object> facts = map(value.get("facts"));
        String statement = string(value.get("statement"));
        if (statement == null) statement = string(value.get("utterance"));
        if (statement == null) statement = string(facts.get("speech"));
        String position = position(statement);
        String actor = string(value.get("actorPlayerId"));
        if (actor == null) actor = string(facts.get("actorPlayerId"));
        long sequence = number(value.get("sourceEventSequence"), number(value.get("sequence"), 0));
        if (position == null || actor == null || sequence <= 0) return null;
        Map<String, Object> claim = new LinkedHashMap<>();
        claim.put("sequence", sequence);
        claim.put("actorPlayerId", actor);
        claim.put("position", position);
        claim.put("team", strings(value.get("playerIds")));
        return claim;
    }

    private Map<String, Object> claim(Map<String, Object> event, Map<String, Object> facts) {
        String act = string(facts.get("speechAct"));
        String text = string(facts.get("speech"));
        String position = position(text);
        if (position == null || act == null) return null;
        Map<String, Object> claim = new LinkedHashMap<>();
        claim.put("sequence", sequence(event));
        claim.put("actorPlayerId", event.get("actorPlayerId"));
        claim.put("position", position);
        claim.put("team", strings(facts.get("playerIds")));
        return claim;
    }

    private String position(String text) {
        if (text == null) return null;
        String value = text.toUpperCase();
        if (value.contains("REJECT") || text.contains("反对") || text.contains("拒绝")) return "REJECT";
        if (value.contains("APPROVE") || text.contains("赞成") || text.contains("同意") || text.contains("支持")) return "APPROVE";
        return null;
    }

    private void combinations(List<String> players, int size, int start, List<String> current,
                              List<List<String>> result) {
        if (result.size() >= MAX_TEAM_CANDIDATES || current.size() == size) {
            if (current.size() == size) addCandidate(result, current, size);
            return;
        }
        for (int index = start; index < players.size() && result.size() < MAX_TEAM_CANDIDATES; index++) {
            current.add(players.get(index));
            combinations(players, size, index + 1, current, result);
            current.remove(current.size() - 1);
        }
    }

    private void addCandidate(List<List<String>> result, List<String> team, int requiredSize) {
        if (team == null || team.size() != requiredSize
                || result.stream().anyMatch(existing -> sameTeam(existing, team))) return;
        result.add(team.stream().sorted().toList());
    }

    private boolean sameTeam(List<String> left, List<String> right) {
        return left.size() == right.size() && left.stream().sorted().toList().equals(right.stream().sorted().toList());
    }

    private boolean overlaps(List<String> left, List<String> right) {
        return left.stream().anyMatch(right::contains);
    }

    private long previousTeamSequence(List<Map<String, Object>> teams) {
        return teams.isEmpty() ? 0L : number(teams.get(teams.size() - 1).get("proposalSequence"), 0);
    }

    private List<String> players(AgentTurnRequest request) {
        if (request == null) return List.of();
        Object values = request.getPublicState().get("players");
        if (!(values instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(Map.class::cast)
                .map(item -> string(item.get("playerId"))).filter(Objects::nonNull).sorted().toList();
    }

    private String voterId(AgentTurnRequest request, String voteKey) {
        if (request == null) return voteKey;
        Object values = request.getPublicState().get("players");
        if (!(values instanceof List<?> list)) return voteKey;
        return list.stream().filter(Map.class::isInstance).map(Map.class::cast)
                .filter(item -> Objects.equals(String.valueOf(item.get("seatNo")), voteKey))
                .map(item -> string(item.get("playerId"))).filter(Objects::nonNull)
                .findFirst().orElse(voteKey);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(String::valueOf).toList();
    }

    private String string(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }

    private long sequence(Map<String, Object> event) {
        return number(event.get("sequence"), 0);
    }

    private long number(Object value, long fallback) {
        return value instanceof Number number ? number.longValue() : fallback;
    }
}
