package com.example.avalon.agent.service;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.ModelProfile;
import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.agent.analysis.DeterministicStrategicEvidenceAnalyzer;
import com.example.avalon.agent.analysis.StrategicEvidenceContext;
import com.example.avalon.agent.social.SocialInfluencePlanner;
import com.example.avalon.core.game.model.PlayerTurnContext;
import com.example.avalon.core.player.memory.VisiblePlayerInfo;
import com.example.avalon.agent.strategy.RoleStrategyPlanner;
import com.example.avalon.agent.strategy.StrategicActionEvaluator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AgentTurnRequestFactory {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final RoleStrategyPlanner roleStrategyPlanner;
    private final DeterministicStrategicEvidenceAnalyzer evidenceAnalyzer;
    private final SocialInfluencePlanner socialInfluencePlanner;
    private final StrategicActionEvaluator strategicActionEvaluator;
    private final MemoryContextProjector memoryContextProjector;

    public AgentTurnRequestFactory(RoleStrategyPlanner roleStrategyPlanner,
                                   DeterministicStrategicEvidenceAnalyzer evidenceAnalyzer,
                                   SocialInfluencePlanner socialInfluencePlanner,
                                   StrategicActionEvaluator strategicActionEvaluator) {
        this.roleStrategyPlanner = roleStrategyPlanner;
        this.evidenceAnalyzer = evidenceAnalyzer;
        this.socialInfluencePlanner = socialInfluencePlanner;
        this.strategicActionEvaluator = strategicActionEvaluator;
        this.memoryContextProjector = new MemoryContextProjector();
    }

    public AgentTurnRequest create(PlayerTurnContext context, PlayerAgentConfig agentConfig) {
        AgentTurnRequest request = new AgentTurnRequest();
        request.setGameId(context.gameId());
        request.setRoundNo(context.roundNo());
        request.setPhase(context.phase());
        request.setPlayerId(context.playerId());
        request.setSeatNo(context.seatNo());
        request.setRoleId(context.roleId());
        request.setModelId(modelProfile(agentConfig).getModelId());
        request.setProvider(provider(agentConfig));
        request.setProtocol(modelProfile(agentConfig).getProtocol());
        request.setModelName(modelName(agentConfig));
        request.setTemperature(modelProfile(agentConfig).getTemperature());
        request.setProviderOptions(modelProfile(agentConfig).getProviderOptions());
        request.setPrivateKnowledge(privateKnowledge(context));
        request.setPublicState(publicState(context));
        Map<String, Object> memory = memory(context);
        request.setMemory(memory);
        request.setObservationDelta(context.observations().events().stream()
                .map(event -> objectMapper.convertValue(event, new TypeReference<Map<String, Object>>() { }))
                .filter(event -> !memoryClaimSequences(memory).contains(sequenceOf(event)))
                .toList());
        request.setObservationFromSequence(context.observations().fromSequenceExclusive());
        request.setObservationToSequence(context.observations().toSequenceInclusive());
        request.setDiscussionDirective(objectMapper.convertValue(context.discussionDirective(),
                new TypeReference<Map<String, Object>>() { }));
        request.setAllowedActions(context.allowedActions().allowedActionTypes().stream().map(Enum::name).toList());
        Map<String, Object> strategyContext = new LinkedHashMap<>(roleStrategyPlanner.plan(context, agentConfig));
        StrategicEvidenceContext evidence = evidenceAnalyzer.analyze(request);
        strategyContext.putAll(evidence.asMap());
        strategyContext.putAll(strategicActionEvaluator.evaluate(request, evidence, context.memoryState().activePredictions()));
        strategyContext.put("audiencePlan", objectMapper.convertValue(socialInfluencePlanner.plan(request),
                new TypeReference<Map<String, Object>>() { }));
        request.setStrategyContext(strategyContext);
        request.setRulesSummary(context.rulesSummary());
        request.setOutputSchemaVersion(defaultString(agentConfig.getOutputSchemaVersion(), "v1"));
        return request;
    }

    private java.util.Set<Long> memoryClaimSequences(Map<String, Object> memory) {
        Object claims = memory.get("publicClaims");
        if (!(claims instanceof List<?> values)) return java.util.Set.of();
        return values.stream().filter(Map.class::isInstance).map(Map.class::cast)
                .map(this::sequenceOf).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
    }

    private Long sequenceOf(Map<?, ?> value) {
        Object sequence = value.get("sourceEventSequence");
        if (!(sequence instanceof Number)) sequence = value.get("sequence");
        return sequence instanceof Number number ? number.longValue() : null;
    }

    private Map<String, Object> privateKnowledge(PlayerTurnContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("camp", context.privateView().camp().name());
        payload.put("notes", context.privateView().knowledge().notes());
        payload.put("visiblePlayers", context.privateView().knowledge().visiblePlayers().stream()
                .map(this::visiblePlayerPayload)
                .toList());
        return payload;
    }

    private Map<String, Object> visiblePlayerPayload(VisiblePlayerInfo visiblePlayer) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("playerId", visiblePlayer.playerId());
        payload.put("seatNo", visiblePlayer.seatNo());
        payload.put("displayName", visiblePlayer.displayName());
        payload.put("exactRoleId", visiblePlayer.exactRoleId());
        payload.put("camp", visiblePlayer.camp() == null ? null : visiblePlayer.camp().name());
        payload.put("candidateRoleIds", List.copyOf(visiblePlayer.candidateRoleIds()));
        return payload;
    }

    private Map<String, Object> publicState(PlayerTurnContext context) {
        Map<String, Object> payload = objectMapper.convertValue(context.publicState(), new TypeReference<Map<String, Object>>() { });
        payload.put("teamSize", context.ruleSetDefinition().teamSizeForRound(context.roundNo()));
        payload.put("playerCount", context.setupTemplate().playerCount());
        payload.put("roleIds", context.setupTemplate().roleIds());
        return payload;
    }

    private Map<String, Object> memory(PlayerTurnContext context) {
        return memoryContextProjector.project(context.memoryState());
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String provider(PlayerAgentConfig agentConfig) {
        return defaultString(modelProfile(agentConfig).getProvider(), "noop");
    }

    private String modelName(PlayerAgentConfig agentConfig) {
        String modelName = modelProfile(agentConfig).getModelName();
        return modelName == null || modelName.isBlank() ? null : modelName;
    }

    private ModelProfile modelProfile(PlayerAgentConfig agentConfig) {
        if (agentConfig == null || agentConfig.getModelProfile() == null) {
            return new ModelProfile();
        }
        return agentConfig.getModelProfile();
    }
}
