package com.example.avalon.api.service;

import com.example.avalon.agent.harness.AgentHarnessType;
import com.example.avalon.agent.model.ModelProfile;
import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.api.model.CatalogModelProfile;
import com.example.avalon.core.player.enums.PlayerControllerType;
import com.example.avalon.runtime.model.GameRuntimeState;
import com.example.avalon.runtime.model.LlmSelectionConfig;
import com.example.avalon.runtime.model.LlmSelectionMode;
import com.example.avalon.runtime.model.PlayerRegistration;
import com.example.avalon.runtime.service.ResolvedLlmConfigInitializer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class LlmSelectionResolutionService implements ResolvedLlmConfigInitializer {
    private final ModelProfileCatalogService modelProfileCatalogService;
    private final ObjectMapper objectMapper;

    public LlmSelectionResolutionService(ModelProfileCatalogService modelProfileCatalogService) {
        this.modelProfileCatalogService = modelProfileCatalogService;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Override
    public Map<String, Map<String, Object>> resolve(GameRuntimeState state) {
        LlmSelectionConfig selectionConfig = state.setup().llmSelectionConfig();
        if (selectionConfig.mode() == LlmSelectionMode.NONE) {
            return Map.of();
        }
        List<PlayerRegistration> llmPlayers = state.players().stream()
                .filter(player -> player.controllerType() == PlayerControllerType.LLM)
                .sorted((left, right) -> Integer.compare(left.seatNo(), right.seatNo()))
                .toList();
        if (llmPlayers.isEmpty()) {
            return Map.of();
        }
        return switch (selectionConfig.mode()) {
            case SEAT_BINDING -> resolveSeatBindings(llmPlayers, selectionConfig);
            case ROLE_BINDING -> resolveRoleBindings(state, llmPlayers, selectionConfig);
            case RANDOM_POOL -> resolveModelPool(llmPlayers, selectionConfig);
            case NONE -> Map.of();
        };
    }

    private Map<String, Map<String, Object>> resolveSeatBindings(List<PlayerRegistration> llmPlayers,
                                                                 LlmSelectionConfig selectionConfig) {
        Map<String, Map<String, Object>> resolvedConfigs = new LinkedHashMap<>();
        for (PlayerRegistration player : llmPlayers) {
            String modelId = selectionConfig.seatBindings().get(player.seatNo());
            if (modelId == null || modelId.isBlank()) {
                throw new IllegalArgumentException("Missing model binding for seat " + player.seatNo());
            }
            resolvedConfigs.put(player.playerId(), resolvedControllerConfig(player,
                    modelProfileCatalogService.requireEnabledProfile(modelId),
                    selectionConfig.seatHarnessBindings().get(player.seatNo())));
        }
        return resolvedConfigs;
    }

    private Map<String, Map<String, Object>> resolveRoleBindings(GameRuntimeState state,
                                                                 List<PlayerRegistration> llmPlayers,
                                                                 LlmSelectionConfig selectionConfig) {
        Map<String, Map<String, Object>> resolvedConfigs = new LinkedHashMap<>();
        for (PlayerRegistration player : llmPlayers) {
            String roleId = state.requireRoleAssignmentBySeat(player.seatNo()).roleId();
            String modelId = selectionConfig.roleBindings().get(roleId);
            if (modelId == null || modelId.isBlank()) {
                throw new IllegalArgumentException("Missing model binding for role " + roleId);
            }
            resolvedConfigs.put(player.playerId(), resolvedControllerConfig(player,
                    modelProfileCatalogService.requireEnabledProfile(modelId),
                    selectionConfig.roleHarnessBindings().get(roleId)));
        }
        return resolvedConfigs;
    }

    private Map<String, Map<String, Object>> resolveModelPool(List<PlayerRegistration> llmPlayers,
                                                              LlmSelectionConfig selectionConfig) {
        List<String> candidateIds = new ArrayList<>(new LinkedHashSet<>(selectionConfig.candidateModelIds()));
        if (candidateIds.isEmpty()) {
            throw new IllegalArgumentException("Random model pool must contain at least one modelId");
        }
        Map<String, Map<String, Object>> resolvedConfigs = new LinkedHashMap<>();
        for (int index = 0; index < llmPlayers.size(); index++) {
            PlayerRegistration player = llmPlayers.get(index);
            String modelId = candidateIds.get(index % candidateIds.size());
            CatalogModelProfile profile = modelProfileCatalogService.requireEnabledProfile(modelId);
            resolvedConfigs.put(player.playerId(), resolvedControllerConfig(player, profile, null));
        }
        return resolvedConfigs;
    }

    private Map<String, Object> resolvedControllerConfig(PlayerRegistration player, CatalogModelProfile profile,
                                                         String harnessType) {
        PlayerAgentConfig config = objectMapper.convertValue(player.controllerConfig(), PlayerAgentConfig.class);
        ModelProfile modelProfile = new ModelProfile();
        modelProfile.setModelId(profile.modelId());
        modelProfile.setProvider(profile.provider());
        modelProfile.setProtocol(profile.protocol());
        modelProfile.setModelName(profile.modelName());
        modelProfile.setTemperature(profile.temperature());
        modelProfile.setProviderOptions(profile.providerOptions());
        config.setModelProfile(modelProfile);
        if (harnessType != null && !harnessType.isBlank()) {
            config.setHarnessType(AgentHarnessType.valueOf(harnessType));
        }
        return objectMapper.convertValue(config, new TypeReference<Map<String, Object>>() { });
    }
}
