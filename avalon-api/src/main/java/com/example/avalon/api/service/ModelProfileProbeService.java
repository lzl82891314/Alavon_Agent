package com.example.avalon.api.service;

import com.example.avalon.agent.gateway.ModelProtocolAdapterRegistry;
import com.example.avalon.agent.gateway.OpenAiCompatibleSupport;
import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.api.dto.ModelProfileProbeCheckResponse;
import com.example.avalon.api.dto.ModelProfileProbeRequest;
import com.example.avalon.api.dto.ModelProfileProbeResponse;
import com.example.avalon.api.model.CatalogModelProfile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Executes diagnostic turns through the same protocol adapter used by a game. */
@Service
public class ModelProfileProbeService {
    private static final List<ProbeCheckType> DEFAULT_CHECKS = List.of(
            ProbeCheckType.CONNECTIVITY,
            ProbeCheckType.STRUCTURED_JSON
    );
    private static final Pattern STATUS_PATTERN = Pattern.compile("status\\s+(\\d{3})");

    private final ModelProfileCatalogService modelProfileCatalogService;
    private final ModelProtocolAdapterRegistry protocolAdapters;

    public ModelProfileProbeService(ModelProfileCatalogService modelProfileCatalogService,
                                    ModelProtocolAdapterRegistry protocolAdapters) {
        this.modelProfileCatalogService = modelProfileCatalogService;
        this.protocolAdapters = protocolAdapters;
    }

    public ModelProfileProbeResponse probe(String modelId, ModelProfileProbeRequest request) {
        CatalogModelProfile profile = modelProfileCatalogService.requireCatalogProfile(modelId);
        List<ProbeCheckType> checksToRun = normalizeChecks(request == null ? List.of() : request.getChecks());
        List<ModelProfileProbeCheckResponse> checks = new ArrayList<>();
        if (checksToRun.contains(ProbeCheckType.CONNECTIVITY)) {
            checks.add(runCheck(profile, ProbeCheckType.CONNECTIVITY));
        }
        if (checksToRun.contains(ProbeCheckType.STRUCTURED_JSON)) {
            checks.add(runCheck(profile, ProbeCheckType.STRUCTURED_JSON));
        }

        ModelProfileProbeResponse response = new ModelProfileProbeResponse();
        response.setModelId(profile.modelId());
        response.setProvider(profile.provider());
        response.setModelName(profile.modelName());
        response.setBaseUrl(OpenAiCompatibleSupport.stringOption(profile.providerOptions(), "baseUrl"));
        response.setChecks(checks);
        response.setReachable(checkResult(checks, ProbeCheckType.CONNECTIVITY));
        response.setStructuredCompatible(checkResult(checks, ProbeCheckType.STRUCTURED_JSON));
        response.setDiagnosis(diagnosis(response.getReachable(), response.getStructuredCompatible()));
        return response;
    }

    private ModelProfileProbeCheckResponse runCheck(CatalogModelProfile profile, ProbeCheckType checkType) {
        long startedAt = System.nanoTime();
        ModelProfileProbeCheckResponse response = new ModelProfileProbeCheckResponse();
        response.setCheckType(checkType.name());
        try {
            AgentTurnResult result = protocolAdapters.require(profile.protocol()).playTurn(probeRequest(profile));
            response.setSuccess(true);
            response.setHttpStatus(200);
            response.setLatencyMs(elapsedMillis(startedAt));
            response.setAssistantPreview(result.getPublicSpeech());
            response.setContentPresent(result.getActionJson() != null && !result.getActionJson().isBlank());
            response.setContentShape("PROTOCOL_NORMALIZED_ACTION");
            if (result.getModelMetadata() != null) {
                Object finishReason = result.getModelMetadata().getAttributes().get("finishReason");
                if (finishReason == null) {
                    finishReason = result.getModelMetadata().getAttributes().get("stopReason");
                }
                response.setFinishReason(finishReason == null ? null : String.valueOf(finishReason));
            }
            return response;
        } catch (RuntimeException exception) {
            response.setSuccess(false);
            response.setHttpStatus(extractStatus(exception.getMessage()));
            response.setLatencyMs(elapsedMillis(startedAt));
            response.setErrorMessage(exception.getMessage());
            return response;
        }
    }

    private AgentTurnRequest probeRequest(CatalogModelProfile profile) {
        AgentTurnRequest request = new AgentTurnRequest();
        request.setGameId("model-probe");
        request.setPhase("MODEL_PROBE");
        request.setPlayerId("probe");
        request.setModelId(profile.modelId());
        request.setProvider(profile.provider());
        request.setProtocol(profile.protocol());
        request.setModelName(profile.modelName());
        request.setTemperature(profile.temperature());
        request.setProviderOptions(profile.providerOptions());
        request.setPromptText("Return exactly one JSON object: {\"action\":{\"actionType\":\"PUBLIC_SPEECH\",\"speechText\":\"ok\"},\"publicSpeech\":\"ok\"}.");
        return request;
    }

    private List<ProbeCheckType> normalizeChecks(List<String> requestedChecks) {
        if (requestedChecks == null || requestedChecks.isEmpty()) {
            return DEFAULT_CHECKS;
        }
        Set<ProbeCheckType> checks = new LinkedHashSet<>();
        for (String requestedCheck : requestedChecks) {
            if (requestedCheck != null && !requestedCheck.isBlank()) {
                checks.add(ProbeCheckType.valueOf(requestedCheck.trim().toUpperCase(Locale.ROOT)));
            }
        }
        return checks.isEmpty() ? DEFAULT_CHECKS : List.copyOf(checks);
    }

    private Boolean checkResult(List<ModelProfileProbeCheckResponse> checks, ProbeCheckType type) {
        return checks.stream().filter(check -> type.name().equals(check.getCheckType())).findFirst()
                .map(ModelProfileProbeCheckResponse::isSuccess).orElse(null);
    }

    private String diagnosis(Boolean reachable, Boolean structuredCompatible) {
        if (Boolean.FALSE.equals(reachable)) return "NETWORK_OR_AUTH_FAILED";
        if (Boolean.TRUE.equals(reachable) && Boolean.FALSE.equals(structuredCompatible)) return "NETWORK_OK_BUT_STRUCTURED_JSON_FAILED";
        if (Boolean.FALSE.equals(structuredCompatible)) return "STRUCTURED_JSON_FAILED";
        return "OK";
    }

    private Integer extractStatus(String errorMessage) {
        if (errorMessage == null) return null;
        Matcher matcher = STATUS_PATTERN.matcher(errorMessage);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }

    private Long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private enum ProbeCheckType { CONNECTIVITY, STRUCTURED_JSON }
}
