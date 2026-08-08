package com.example.avalon.testkit;

public record StrategicBehaviorReport(
        int publicSpeechCount,
        int challengeCount,
        double challengeTargetRate,
        int targetedResponseCount,
        double targetedResponseReferenceRate,
        int beliefRevisionCount,
        double evidenceGroundedBeliefRevisionRate,
        int deceptionPlanCount,
        double narrativeConsistencyRate,
        boolean evilAgentObserved,
        int validationFailureCount
) {
    public boolean meetsProtocolGate() {
        return challengeCount > 0
                && challengeTargetRate >= 0.95d
                && targetedResponseCount > 0
                && targetedResponseReferenceRate >= 0.95d
                && validationFailureCount == 0;
    }

    public boolean meetsStrategicGate() {
        return meetsProtocolGate()
                && beliefRevisionCount > 0
                && evidenceGroundedBeliefRevisionRate >= 0.90d
                && (!evilAgentObserved || deceptionPlanCount > 0)
                && narrativeConsistencyRate >= 0.90d;
    }

    public boolean meetsStructuralGate() {
        return meetsStrategicGate();
    }
}
