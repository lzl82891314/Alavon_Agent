package com.example.avalon.testkit;

import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.player.enums.PlayerControllerType;
import com.example.avalon.core.role.enums.KnowledgeRuleType;
import com.example.avalon.core.role.model.KnowledgeRuleDefinition;
import com.example.avalon.core.role.model.RoleDefinition;
import com.example.avalon.core.setup.model.AssassinationRuleDefinition;
import com.example.avalon.core.setup.model.RoundTeamSizeRule;
import com.example.avalon.core.setup.model.RuleSetDefinition;
import com.example.avalon.core.setup.model.SetupTemplate;
import com.example.avalon.core.setup.model.VisibilityPolicyDefinition;
import com.example.avalon.runtime.model.GameSetup;
import com.example.avalon.runtime.model.PlayerRegistration;
import com.example.avalon.runtime.orchestrator.GameOrchestrator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ScriptedAvalonFixture {
    private ScriptedAvalonFixture() {
    }

    public static GameSetup classicFivePlayerSetup(long seed) {
        return classicSetup(5, seed);
    }

    public static GameSetup classicSetup(int playerCount, long seed) {
        RuleSetDefinition ruleSetDefinition = new RuleSetDefinition(
                "avalon-classic-" + playerCount + "p-v2",
                "Avalon Classic " + playerCount + " Players V2",
                "2.0.0",
                playerCount,
                playerCount,
                teamSizeRules(playerCount),
                failThresholds(playerCount),
                List.of("classic-" + playerCount + "p-v2"),
                new AssassinationRuleDefinition(true, "ASSASSIN", "MERLIN"),
                new VisibilityPolicyDefinition(true),
                true);
        SetupTemplate setupTemplate = new SetupTemplate(
                "classic-" + playerCount + "p-v2",
                playerCount,
                true,
                roleIds(playerCount));
        return new GameSetup(
                "scripted-classic-" + playerCount + "p-" + seed,
                "avalon-classic-" + playerCount + "p-v2",
                ruleSetDefinition,
                "classic-" + playerCount + "p-v2",
                setupTemplate,
                seed,
                classicRoleDefinitions(),
                scriptedPlayers(playerCount)
        );
    }

    public static GameOrchestrator orchestrator() {
        return new GameOrchestrator();
    }

    private static Map<String, RoleDefinition> classicRoleDefinitions() {
        return Map.of(
                "MERLIN", new RoleDefinition(
                        "MERLIN",
                        "Merlin",
                        Camp.GOOD,
                        "Knows most evil players, but must avoid being identified by the assassin.",
                        List.of(new KnowledgeRuleDefinition(KnowledgeRuleType.SEE_PLAYERS_BY_CAMP, Camp.EVIL, List.of(), List.of("MORDRED"))),
                        List.of(),
                        true,
                        true,
                        true,
                        false,
                        List.of("HIDDEN_HIGH_VALUE_TARGET")),
                "PERCIVAL", new RoleDefinition(
                        "PERCIVAL",
                        "Percival",
                        Camp.GOOD,
                        "Sees Merlin or Morgana as a possible Merlin.",
                        List.of(new KnowledgeRuleDefinition(KnowledgeRuleType.SEE_ROLE_AMBIGUITY, null, List.of("MERLIN", "MORGANA"), List.of())),
                        List.of(),
                        true,
                        true,
                        true,
                        false,
                        List.of()),
                "LOYAL_SERVANT", new RoleDefinition(
                        "LOYAL_SERVANT",
                        "Loyal Servant",
                        Camp.GOOD,
                        "Ordinary loyal good player with no special knowledge.",
                        List.of(),
                        List.of(),
                        true,
                        true,
                        true,
                        false,
                        List.of()),
                "MORGANA", new RoleDefinition(
                        "MORGANA",
                        "Morgana",
                        Camp.EVIL,
                        "Appears as Merlin to Percival.",
                        List.of(new KnowledgeRuleDefinition(KnowledgeRuleType.SEE_ALLIED_EVIL_PLAYERS, null, List.of(), List.of("OBERON"))),
                        List.of(),
                        true,
                        true,
                        true,
                        false,
                        List.of()),
                "ASSASSIN", new RoleDefinition(
                        "ASSASSIN",
                        "Assassin",
                        Camp.EVIL,
                        "Can assassinate Merlin at the end of the game if evil still has a chance.",
                        List.of(new KnowledgeRuleDefinition(KnowledgeRuleType.SEE_ALLIED_EVIL_PLAYERS, null, List.of(), List.of("OBERON"))),
                        List.of("ASSASSINATE"),
                        true,
                        true,
                        true,
                        true,
                        List.of()),
                "MORDRED", new RoleDefinition(
                        "MORDRED",
                        "Mordred",
                        Camp.EVIL,
                        "Hidden from Merlin while still coordinating with the evil team.",
                        List.of(new KnowledgeRuleDefinition(KnowledgeRuleType.SEE_ALLIED_EVIL_PLAYERS, null, List.of(), List.of("OBERON"))),
                        List.of(),
                        true,
                        true,
                        true,
                        false,
                        List.of("HIDDEN_FROM_MERLIN")),
                "OBERON", new RoleDefinition(
                        "OBERON",
                        "Oberon",
                        Camp.EVIL,
                        "Evil but isolated from the rest of the evil team.",
                        List.of(),
                        List.of(),
                        true,
                        true,
                        true,
                        false,
                        List.of("HIDDEN_FROM_EVIL"))
        );
    }

    private static List<PlayerRegistration> scriptedPlayers(int playerCount) {
        List<PlayerRegistration> players = new ArrayList<>();
        for (int seatNo = 1; seatNo <= playerCount; seatNo++) {
            players.add(new PlayerRegistration("P" + seatNo, seatNo, "P" + seatNo, PlayerControllerType.SCRIPTED));
        }
        return List.copyOf(players);
    }

    private static List<RoundTeamSizeRule> teamSizeRules(int playerCount) {
        return switch (playerCount) {
            case 5 -> List.of(
                    new RoundTeamSizeRule(1, 2),
                    new RoundTeamSizeRule(2, 3),
                    new RoundTeamSizeRule(3, 2),
                    new RoundTeamSizeRule(4, 3),
                    new RoundTeamSizeRule(5, 3));
            case 6 -> List.of(
                    new RoundTeamSizeRule(1, 2),
                    new RoundTeamSizeRule(2, 3),
                    new RoundTeamSizeRule(3, 4),
                    new RoundTeamSizeRule(4, 3),
                    new RoundTeamSizeRule(5, 4));
            case 7 -> List.of(
                    new RoundTeamSizeRule(1, 2),
                    new RoundTeamSizeRule(2, 3),
                    new RoundTeamSizeRule(3, 3),
                    new RoundTeamSizeRule(4, 4),
                    new RoundTeamSizeRule(5, 4));
            case 8, 9, 10 -> List.of(
                    new RoundTeamSizeRule(1, 3),
                    new RoundTeamSizeRule(2, 4),
                    new RoundTeamSizeRule(3, 4),
                    new RoundTeamSizeRule(4, 5),
                    new RoundTeamSizeRule(5, 5));
            default -> throw new IllegalArgumentException("Unsupported player count: " + playerCount);
        };
    }

    private static Map<Integer, Integer> failThresholds(int playerCount) {
        return switch (playerCount) {
            case 5, 6 -> Map.of(1, 1, 2, 1, 3, 1, 4, 1, 5, 1);
            case 7, 8, 9, 10 -> Map.of(1, 1, 2, 1, 3, 1, 4, 2, 5, 1);
            default -> throw new IllegalArgumentException("Unsupported player count: " + playerCount);
        };
    }

    private static List<String> roleIds(int playerCount) {
        return switch (playerCount) {
            case 5 -> List.of("MERLIN", "PERCIVAL", "LOYAL_SERVANT", "MORGANA", "ASSASSIN");
            case 6 -> List.of("MERLIN", "PERCIVAL", "LOYAL_SERVANT", "LOYAL_SERVANT", "MORGANA", "ASSASSIN");
            case 7 -> List.of("MERLIN", "PERCIVAL", "LOYAL_SERVANT", "LOYAL_SERVANT", "MORGANA", "ASSASSIN", "MORDRED");
            case 8 -> List.of("MERLIN", "PERCIVAL", "LOYAL_SERVANT", "LOYAL_SERVANT", "LOYAL_SERVANT", "MORGANA", "ASSASSIN", "MORDRED");
            case 9 -> List.of("MERLIN", "PERCIVAL", "LOYAL_SERVANT", "LOYAL_SERVANT", "LOYAL_SERVANT", "LOYAL_SERVANT", "MORGANA", "ASSASSIN", "MORDRED");
            case 10 -> List.of("MERLIN", "PERCIVAL", "LOYAL_SERVANT", "LOYAL_SERVANT", "LOYAL_SERVANT", "LOYAL_SERVANT", "MORGANA", "ASSASSIN", "MORDRED", "OBERON");
            default -> throw new IllegalArgumentException("Unsupported player count: " + playerCount);
        };
    }
}
