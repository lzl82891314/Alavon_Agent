package com.example.avalon.runtime.service;

import com.example.avalon.core.game.model.AllowedActionSet;
import com.example.avalon.core.game.model.PlayerTurnContext;
import com.example.avalon.core.game.model.PublicGameSnapshot;
import com.example.avalon.core.game.model.PublicPlayerSummary;
import com.example.avalon.core.player.memory.PlayerMemoryState;
import com.example.avalon.core.player.memory.PlayerPrivateView;
import com.example.avalon.core.role.model.RoleAssignment;
import com.example.avalon.runtime.engine.VisibilityService;
import com.example.avalon.runtime.model.GameRuntimeState;
import com.example.avalon.runtime.model.PlayerRegistration;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TurnContextBuilder {
    private final VisibilityService visibilityService;
    private final RuntimeCoreContextFactory contextFactory;
    private final com.example.avalon.core.game.rule.GameRuleEngine coreRuleEngine;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public TurnContextBuilder(VisibilityService visibilityService) {
        this(visibilityService, new RuntimeCoreContextFactory(), new com.example.avalon.core.game.rule.ClassicAvalonRuleEngine());
    }

    TurnContextBuilder(VisibilityService visibilityService,
                       RuntimeCoreContextFactory contextFactory,
                       com.example.avalon.core.game.rule.GameRuleEngine coreRuleEngine) {
        this.visibilityService = visibilityService;
        this.contextFactory = contextFactory;
        this.coreRuleEngine = coreRuleEngine;
    }

    public PlayerTurnContext build(GameRuntimeState state, PlayerRegistration player) {
        RoleAssignment assignment = state.requireRoleAssignmentBySeat(player.seatNo());
        PlayerPrivateView privateView = visibilityService.buildPrivateView(state, assignment);
        AllowedActionSet allowedActions = coreRuleEngine.allowedActions(contextFactory.toRuleContext(state), player.playerId());
        return new PlayerTurnContext(
                state.generatedGameId(),
                state.roundNo(),
                state.phase().name(),
                player.playerId(),
                player.seatNo(),
                assignment.roleId(),
                publicSnapshot(state),
                privateView,
                memoryState(state, player, assignment),
                allowedActions,
                state.runtimeRuleSetDefinition(),
                state.runtimeSetupTemplate(),
                rulesSummary(state));
    }

    private PlayerMemoryState memoryState(GameRuntimeState state,
                                          PlayerRegistration player,
                                          RoleAssignment assignment) {
        Map<String, Object> payload = new LinkedHashMap<>(state.memoryOf(player.playerId()));
        payload.putIfAbsent("gameId", state.generatedGameId());
        payload.putIfAbsent("playerId", player.playerId());
        payload.putIfAbsent("version", 0L);
        payload.putIfAbsent("roleId", assignment.roleId());
        payload.putIfAbsent("camp", assignment.camp().name());
        payload.putIfAbsent("strategyMode", "NEUTRAL");
        payload.putIfAbsent("updatedAt", state.updatedAt());
        return objectMapper.convertValue(payload, PlayerMemoryState.class);
    }

    private PublicGameSnapshot publicSnapshot(GameRuntimeState state) {
        List<String> currentTeamPlayerIds = state.currentProposalTeam().stream()
                .map(seat -> state.playerBySeat(seat).playerId())
                .toList();
        List<PublicPlayerSummary> players = state.players().stream()
                .map(player -> new PublicPlayerSummary(
                        state.generatedGameId(),
                        player.playerId(),
                        player.seatNo(),
                        player.displayName(),
                        player.controllerType(),
                        com.example.avalon.core.game.enums.PlayerConnectionState.DISCONNECTED))
                .collect(Collectors.toList());
        return new PublicGameSnapshot(
                state.generatedGameId(),
                state.status(),
                state.phase(),
                state.roundNo(),
                state.failedTeamVoteCount(),
                state.approvedMissionRounds().size(),
                state.failedMissionRounds().size(),
                state.currentLeaderSeat(),
                currentTeamPlayerIds,
                state.winnerCamp(),
                state.winnerCamp() == null ? null : state.winnerCamp().name(),
                players,
                state.updatedAt());
    }
    private String rulesSummary(GameRuntimeState state) {
        String teamSizes = state.setup().ruleSetDefinition().teamSizeRules().stream()
                .sorted(java.util.Comparator.comparingInt(com.example.avalon.core.setup.model.RoundTeamSizeRule::round))
                .map(rule -> String.valueOf(rule.teamSize()))
                .collect(Collectors.joining("/"));
        String failThresholds = state.setup().ruleSetDefinition().teamSizeRules().stream()
                .sorted(java.util.Comparator.comparingInt(com.example.avalon.core.setup.model.RoundTeamSizeRule::round))
                .map(rule -> String.valueOf(state.setup().ruleSetDefinition().failThresholdForRound(rule.round())))
                .collect(Collectors.joining("/"));
        boolean assassinationEnabled = state.setup().ruleSetDefinition().assassinationRule() != null
                && state.setup().ruleSetDefinition().assassinationRule().enabled();
        return "标准阿瓦隆 %d 人局；任务人数=%s；失败阈值=%s；刺杀阶段=%s。".formatted(
                state.playerCount(),
                teamSizes,
                failThresholds,
                assassinationEnabled ? "开启" : "关闭"
        );
    }
}
