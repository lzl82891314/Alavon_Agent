package com.example.avalon.api.service;

import com.example.avalon.api.dto.ModelProfileRequest;
import com.example.avalon.api.dto.ModelProfileResponse;
import com.example.avalon.api.model.CatalogModelProfile;
import com.example.avalon.api.model.ModelProfileSource;
import com.example.avalon.config.model.AvalonConfigRegistry;
import com.example.avalon.config.model.LlmModelDefinition;
import com.example.avalon.persistence.model.ModelProfileRecord;
import com.example.avalon.persistence.store.ModelProfileStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class ModelProfileCatalogService {
    private final AvalonConfigRegistry configRegistry;
    private final ModelProfileStore modelProfileStore;
    private final ObjectMapper objectMapper;

    public ModelProfileCatalogService(AvalonConfigRegistry configRegistry, ModelProfileStore modelProfileStore) {
        this.configRegistry = configRegistry;
        this.modelProfileStore = modelProfileStore;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    public List<ModelProfileResponse> listAll() {
        return mergedProfiles().stream().map(this::toResponse).toList();
    }

    public ModelProfileResponse get(String modelId) {
        return toResponse(requireProfile(modelId));
    }

    public CatalogModelProfile requireCatalogProfile(String modelId) {
        return requireProfile(modelId);
    }

    public CatalogModelProfile requireEnabledProfile(String modelId) {
        CatalogModelProfile profile = requireProfile(modelId);
        if (!profile.enabled()) {
            throw new IllegalArgumentException("Model profile is disabled: " + modelId);
        }
        return profile;
    }

    public ModelProfileResponse create(ModelProfileRequest request) {
        validateUpsertRequest(request, true);
        String modelId = normalized(request.getModelId(), "modelId");
        if (configRegistry.findModelProfile(modelId).isPresent()) {
            throw new IllegalArgumentException("Cannot create managed model profile that conflicts with static model profile: " + modelId);
        }
        if (modelProfileStore.findByModelId(modelId).isPresent()) {
            throw new IllegalArgumentException("Managed model profile already exists: " + modelId);
        }
        Instant now = Instant.now();
        ModelProfileRecord saved = modelProfileStore.save(toRecord(modelId, request, now, now));
        return toResponse(toManagedProfile(saved));
    }

    public ModelProfileResponse update(String modelId, ModelProfileRequest request) {
        validateUpsertRequest(request, false);
        String normalizedModelId = normalized(modelId, "modelId");
        if (request.getModelId() != null && !request.getModelId().isBlank() && !normalizedModelId.equals(request.getModelId().trim())) {
            throw new IllegalArgumentException("Path modelId must match request modelId");
        }
        if (configRegistry.findModelProfile(normalizedModelId).isPresent()) {
            throw new IllegalArgumentException("Static model profile is read-only: " + normalizedModelId);
        }
        ModelProfileRecord existing = modelProfileStore.findByModelId(normalizedModelId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown managed model profile: " + normalizedModelId));
        Instant now = Instant.now();
        ModelProfileRecord saved = modelProfileStore.save(toRecord(normalizedModelId, request, existing.createdAt(), now));
        return toResponse(toManagedProfile(saved));
    }

    public void delete(String modelId) {
        String normalizedModelId = normalized(modelId, "modelId");
        if (configRegistry.findModelProfile(normalizedModelId).isPresent()) {
            throw new IllegalArgumentException("Static model profile is read-only: " + normalizedModelId);
        }
        if (modelProfileStore.findByModelId(normalizedModelId).isEmpty()) {
            throw new IllegalArgumentException("Unknown managed model profile: " + normalizedModelId);
        }
        modelProfileStore.deleteByModelId(normalizedModelId);
    }

    private CatalogModelProfile requireProfile(String modelId) {
        return mergedProfiles().stream()
                .filter(profile -> profile.modelId().equals(modelId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown model profile: " + modelId));
    }

    private List<CatalogModelProfile> mergedProfiles() {
        Map<String, CatalogModelProfile> profilesById = new LinkedHashMap<>();
        configRegistry.modelProfiles().stream()
                .map(this::toStaticProfile)
                .forEach(profile -> profilesById.put(profile.modelId(), profile));
        for (ModelProfileRecord record : modelProfileStore.findAll()) {
            if (profilesById.containsKey(record.modelId())) {
                throw new IllegalStateException("Managed model profile conflicts with static model profile: " + record.modelId());
            }
            profilesById.put(record.modelId(), toManagedProfile(record));
        }
        return new ArrayList<>(profilesById.values());
    }

    private ModelProfileRecord toRecord(String modelId, ModelProfileRequest request, Instant createdAt, Instant updatedAt) {
        return new ModelProfileRecord(
                modelId,
                normalized(request.getDisplayName(), "displayName"),
                normalized(request.getProvider(), "provider"),
                normalized(request.getModelName(), "modelName"),
                request.getTemperature(),
                request.getMaxTokens(),
                writeProviderOptions(request.getProviderOptions()),
                request.getEnabled() == null || request.getEnabled(),
                createdAt,
                updatedAt
        );
    }

    private void validateUpsertRequest(ModelProfileRequest request, boolean requireModelId) {
        Objects.requireNonNull(request, "request");
        if (requireModelId) {
            normalized(request.getModelId(), "modelId");
        }
        normalized(request.getDisplayName(), "displayName");
        normalized(request.getProvider(), "provider");
        normalized(request.getModelName(), "modelName");
        if (request.getTemperature() != null && request.getTemperature() < 0.0) {
            throw new IllegalArgumentException("temperature must be non-negative");
        }
        if (request.getMaxTokens() != null && request.getMaxTokens() <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
        validateProviderOptions(request.getProviderOptions());
    }

    private String normalized(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private String writeProviderOptions(Map<String, Object> providerOptions) {
        try {
            return objectMapper.writeValueAsString(providerOptions == null ? Map.of() : providerOptions);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("providerOptions must be valid JSON content");
        }
    }

    private void validateProviderOptions(Map<String, Object> providerOptions) {
        if (providerOptions == null || providerOptions.isEmpty()) {
            return;
        }
        Object baseUrlValue = providerOptions.get("baseUrl");
        if (baseUrlValue == null) {
            return;
        }
        String normalized = normalizeBaseUrl(String.valueOf(baseUrlValue));
        if (normalized.endsWith("/chat/completions")) {
            throw new IllegalArgumentException("providerOptions.baseUrl must be the API root, not the /chat/completions endpoint");
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> readProviderOptions(String providerOptionsJson) {
        try {
            return objectMapper.readValue(providerOptionsJson == null ? "{}" : providerOptionsJson, new TypeReference<Map<String, Object>>() { });
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to deserialize providerOptions", exception);
        }
    }

    private CatalogModelProfile toStaticProfile(LlmModelDefinition definition) {
        return new CatalogModelProfile(
                definition.modelId(),
                definition.displayName(),
                ModelProfileSource.STATIC,
                false,
                definition.enabled(),
                definition.provider(),
                definition.modelName(),
                definition.temperature(),
                definition.maxTokens(),
                definition.providerOptions()
        );
    }

    private CatalogModelProfile toManagedProfile(ModelProfileRecord record) {
        return new CatalogModelProfile(
                record.modelId(),
                record.displayName(),
                ModelProfileSource.MANAGED,
                true,
                record.enabled(),
                record.provider(),
                record.modelName(),
                record.temperature(),
                record.maxTokens(),
                readProviderOptions(record.providerOptionsJson())
        );
    }

    private ModelProfileResponse toResponse(CatalogModelProfile profile) {
        ModelProfileResponse response = new ModelProfileResponse();
        response.setModelId(profile.modelId());
        response.setDisplayName(profile.displayName());
        response.setSource(profile.source().name());
        response.setEditable(profile.editable());
        response.setEnabled(profile.enabled());
        response.setProvider(profile.provider());
        response.setModelName(profile.modelName());
        response.setTemperature(profile.temperature());
        response.setMaxTokens(profile.maxTokens());
        response.setProviderOptions(sanitizedProviderOptions(profile.providerOptions()));
        return response;
    }

    private Map<String, Object> sanitizedProviderOptions(Map<String, Object> providerOptions) {
        Map<String, Object> sanitized = new LinkedHashMap<>(providerOptions == null ? Map.of() : providerOptions);
        sanitized.remove("apiKey");
        return sanitized;
    }
}
