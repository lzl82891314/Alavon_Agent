package com.example.avalon.app.console;

import java.util.List;

record ConsoleDecisionReport(
        String gameId,
        String status,
        String phase,
        String winnerCamp,
        Integer roundNo,
        List<ConsoleDecisionPlayer> players,
        List<ConsoleDecisionSection> sections
) {
    ConsoleDecisionReport {
        players = players == null ? List.of() : List.copyOf(players);
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}

record ConsoleDecisionPlayer(
        String playerId,
        Integer seatNo,
        String displayName,
        String roleId,
        String camp,
        String privateKnowledgeSummary
) {
}

record ConsoleDecisionSection(
        String key,
        String title,
        String leaderPlayerId,
        List<String> teamPlayerIds,
        List<ConsoleDecisionVote> votes,
        boolean voteRejected,
        String missionOutcome,
        Long missionFailCount,
        String pauseReason,
        String winnerCamp,
        List<ConsoleDecisionRow> rows
) {
    ConsoleDecisionSection {
        teamPlayerIds = teamPlayerIds == null ? List.of() : List.copyOf(teamPlayerIds);
        votes = votes == null ? List.of() : List.copyOf(votes);
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}

record ConsoleDecisionVote(String playerId, String vote) {
}

record ConsoleDecisionRow(
        Long eventSeqNo,
        String phase,
        String playerId,
        String roleId,
        String actionType,
        String actionDetail,
        String publicSpeech,
        String privateThought,
        String note,
        boolean failed
) {
}
