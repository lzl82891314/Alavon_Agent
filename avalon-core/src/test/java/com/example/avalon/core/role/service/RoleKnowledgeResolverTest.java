package com.example.avalon.core.role.service;

import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.game.enums.PlayerConnectionState;
import com.example.avalon.core.game.model.GamePlayer;
import com.example.avalon.core.player.enums.PlayerControllerType;
import com.example.avalon.core.player.memory.PlayerPrivateKnowledge;
import com.example.avalon.core.role.enums.KnowledgeRuleType;
import com.example.avalon.core.role.model.KnowledgeRuleDefinition;
import com.example.avalon.core.role.model.RoleAssignment;
import com.example.avalon.core.role.model.RoleDefinition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleKnowledgeResolverTest {
    private final List<GamePlayer> players = List.of(
            player("P1", 1), player("P2", 2), player("P3", 3), player("P4", 4), player("P5", 5));
    private final List<RoleAssignment> assignments = List.of(
            assignment("P1", 1, "MERLIN", Camp.GOOD),
            assignment("P2", 2, "PERCIVAL", Camp.GOOD),
            assignment("P3", 3, "LOYAL_SERVANT", Camp.GOOD),
            assignment("P4", 4, "MORGANA", Camp.EVIL),
            assignment("P5", 5, "ASSASSIN", Camp.EVIL));
    private final Map<String, RoleDefinition> roles = roles();

    @Test
    void merlinSeesEvilAlignmentWithoutExactRoles() {
        PlayerPrivateKnowledge knowledge = resolve("P1", "MERLIN");

        assertEquals(2, knowledge.visiblePlayers().size());
        knowledge.visiblePlayers().forEach(player -> {
            assertEquals(Camp.EVIL, player.camp());
            assertNull(player.exactRoleId());
            assertTrue(player.candidateRoleIds().containsAll(List.of("ASSASSIN", "MORGANA")));
        });
    }

    @Test
    void evilPlayersSeeAlliedAlignmentWithoutExactRoles() {
        PlayerPrivateKnowledge knowledge = resolve("P5", "ASSASSIN");

        assertEquals(1, knowledge.visiblePlayers().size());
        assertEquals("P4", knowledge.visiblePlayers().get(0).playerId());
        assertEquals(Camp.EVIL, knowledge.visiblePlayers().get(0).camp());
        assertNull(knowledge.visiblePlayers().get(0).exactRoleId());
    }

    @Test
    void percivalCannotUseCampToResolveMerlinMorganaAmbiguity() {
        PlayerPrivateKnowledge knowledge = resolve("P2", "PERCIVAL");

        assertEquals(2, knowledge.visiblePlayers().size());
        knowledge.visiblePlayers().forEach(player -> {
            assertNull(player.exactRoleId());
            assertNull(player.camp());
            assertEquals(List.of("MERLIN", "MORGANA"), player.candidateRoleIds());
        });
    }

    private PlayerPrivateKnowledge resolve(String playerId, String roleId) {
        return new RoleKnowledgeResolver().resolve(playerId, players, assignments, roles, roles.get(roleId));
    }

    private GamePlayer player(String playerId, int seatNo) {
        return new GamePlayer("g1", playerId, seatNo, playerId, PlayerControllerType.SCRIPTED,
                "{}", PlayerConnectionState.CONNECTED);
    }

    private RoleAssignment assignment(String playerId, int seatNo, String roleId, Camp camp) {
        return new RoleAssignment("g1", playerId, seatNo, roleId, camp,
                new PlayerPrivateKnowledge(List.of(), List.of()), Instant.now());
    }

    private Map<String, RoleDefinition> roles() {
        return Map.of(
                "MERLIN", role("MERLIN", Camp.GOOD, new KnowledgeRuleDefinition(
                        KnowledgeRuleType.SEE_PLAYERS_BY_CAMP, Camp.EVIL, List.of(), List.of("MORDRED"))),
                "PERCIVAL", role("PERCIVAL", Camp.GOOD, new KnowledgeRuleDefinition(
                        KnowledgeRuleType.SEE_ROLE_AMBIGUITY, null, List.of("MERLIN", "MORGANA"), List.of())),
                "LOYAL_SERVANT", role("LOYAL_SERVANT", Camp.GOOD, null),
                "MORGANA", role("MORGANA", Camp.EVIL, new KnowledgeRuleDefinition(
                        KnowledgeRuleType.SEE_ALLIED_EVIL_PLAYERS, null, List.of(), List.of("OBERON"))),
                "ASSASSIN", role("ASSASSIN", Camp.EVIL, new KnowledgeRuleDefinition(
                        KnowledgeRuleType.SEE_ALLIED_EVIL_PLAYERS, null, List.of(), List.of("OBERON"))));
    }

    private RoleDefinition role(String roleId, Camp camp, KnowledgeRuleDefinition rule) {
        return new RoleDefinition(roleId, roleId, camp, roleId,
                rule == null ? List.of() : List.of(rule), List.of(), true, true, true, false, List.of());
    }
}
