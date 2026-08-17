package com.example.avalon.agent.strategy;

import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.core.game.model.PlayerTurnContext;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Produces enforceable role policy input; it never chooses an action for the model. */
@Component
public final class RoleStrategyPlanner {
    public Map<String, Object> plan(PlayerTurnContext context, PlayerAgentConfig profile) {
        String role = normalize(context.roleId());
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("policyId", "role-" + role.toLowerCase(Locale.ROOT) + "-v1");
        policy.put("role", role);
        policy.put("camp", context.privateView().camp().name());
        policy.put("objectives", objectives(role));
        policy.put("constraints", constraints(role));
        policy.put("permittedDeceptionIntents", RoleStrategyPolicy.permittedDeceptionIntents(role));
        policy.put("decisionQuestions", decisionQuestions(role, context.phase()));
        policy.put("existingCommitments", context.memoryState().commitments());
        policy.put("priorStrategy", context.memoryState().strategyState());
        policy.put("cognitionParameters", profile == null ? Map.of() : profile.getCognition());
        policy.put("communicationParameters", profile == null ? Map.of() : profile.getCommunication());
        policy.put("deceptionParameters", profile == null ? Map.of() : profile.getDeception());
        return policy;
    }

    private List<String> objectives(String role) {
        return switch (role) {
            case "MERLIN" -> List.of("raise the probability of good mission teams", "hide knowledge advantage from the assassin", "justify guidance with public evidence");
            case "PERCIVAL" -> List.of("distinguish Merlin from Morgana", "protect likely Merlin without confirming the protection", "notice unusually accurate low-evidence players");
            case "MORGANA" -> List.of("appear plausibly Merlin-like to Percival", "mix correct reads with controlled misinformation", "maintain a coherent public persona");
            case "ASSASSIN" -> List.of("preserve evil winning lines", "track Merlin candidates across the full public timeline", "avoid mechanical protection of allies");
            case "MORDRED" -> List.of("exploit invisibility to Merlin", "avoid patterns that reveal evil coordination", "choose sabotage timing by mission state");
            case "OBERON" -> List.of("infer allies without privileged coordination", "advance evil outcomes while remaining independent", "avoid assuming knowledge of other evil players");
            case "LOYAL_SERVANT" -> List.of("maximize information gain", "test competing hypotheses", "revise beliefs when public evidence changes");
            default -> List.of("advance the camp objective", "use only visible evidence", "preserve uncertainty when evidence is weak");
        };
    }

    private List<String> constraints(String role) {
        return switch (role) {
            case "MERLIN" -> List.of("do not express certainty that only private knowledge supports", "track exposureRisk in strategyState");
            case "PERCIVAL" -> List.of("candidate roles are ambiguous", "do not publicly confirm a protected target");
            case "OBERON" -> List.of("do not claim knowledge of evil teammates", "do not coordinate from hidden information");
            case "MORGANA", "ASSASSIN", "MORDRED" -> List.of("do not reveal the evil roster", "explain or deliberately manage departures from public commitments");
            default -> List.of("public claims are not world facts", "large belief changes require visible evidence");
        };
    }

    private List<String> decisionQuestions(String role, String phase) {
        List<String> common = List.of("Which new event changes a belief?", "Which competing hypothesis would this action distinguish?", "Does this contradict a prior public commitment?");
        if ("ASSASSINATION".equals(phase) && "ASSASSIN".equals(role)) {
            return List.of("Who was accurate before public evidence justified confidence?", "Who guided good teams while avoiding leadership visibility?", "Who may be baiting the assassination?");
        }
        return common;
    }

    private String normalize(String role) {
        return role == null ? "UNKNOWN" : role.trim().toUpperCase(Locale.ROOT);
    }
}
