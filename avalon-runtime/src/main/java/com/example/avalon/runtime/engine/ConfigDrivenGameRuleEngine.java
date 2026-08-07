package com.example.avalon.runtime.engine;

import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.setup.model.AssassinationRuleDefinition;
import com.example.avalon.runtime.model.GameRuntimeState;

public class ConfigDrivenGameRuleEngine implements GameRuleEngine {
    @Override
    public int missionFailThresholdForRound(GameRuntimeState state) {
        return state.setup().ruleSetDefinition().failThresholdForRound(state.roundNo());
    }

    @Override
    public boolean shouldEnterAssassination(GameRuntimeState state) {
        AssassinationRuleDefinition assassinationRule = state.setup().ruleSetDefinition().assassinationRule();
        return assassinationRule != null
                && assassinationRule.enabled()
                && state.approvedMissionRounds().size() >= 3
                && state.failedMissionRounds().size() < 3;
    }

    @Override
    public String resolveWinner(GameRuntimeState state) {
        if (state.failedMissionRounds().size() >= 3) {
            return Camp.EVIL.name();
        }
        if (state.approvedMissionRounds().size() >= 3 && !shouldEnterAssassination(state)) {
            return Camp.GOOD.name();
        }
        return null;
    }
}
