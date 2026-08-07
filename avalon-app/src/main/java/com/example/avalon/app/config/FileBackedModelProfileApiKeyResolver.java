package com.example.avalon.app.config;

import com.example.avalon.agent.gateway.ModelProfileApiKeyResolver;
import com.example.avalon.agent.gateway.ModelProfileSecretSupport;
import com.example.avalon.agent.gateway.OpenAiCompatibleSupport;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

final class FileBackedModelProfileApiKeyResolver implements ModelProfileApiKeyResolver {
    private static final String ROOT_KEY = "modelProfileApiKeys";

    private final Map<String, String> fileApiKeys;
    private final Function<String, String> propertyLookup;
    private final Function<String, String> environmentLookup;

    FileBackedModelProfileApiKeyResolver(Path secretsFile,
                                         Function<String, String> propertyLookup,
                                         Function<String, String> environmentLookup) {
        Objects.requireNonNull(secretsFile, "secretsFile");
        this.fileApiKeys = loadFileApiKeys(secretsFile.toAbsolutePath().normalize());
        this.propertyLookup = Objects.requireNonNull(propertyLookup, "propertyLookup");
        this.environmentLookup = Objects.requireNonNull(environmentLookup, "environmentLookup");
    }

    @Override
    public String resolveApiKey(String modelId, Map<String, Object> providerOptions) {
        String inlineApiKey = trimToNull(OpenAiCompatibleSupport.stringOption(providerOptions, "apiKey"));
        if (inlineApiKey != null) {
            return inlineApiKey;
        }

        if (modelId != null && !modelId.isBlank()) {
            String fileApiKey = trimToNull(fileApiKeys.get(modelId));
            if (fileApiKey != null) {
                return fileApiKey;
            }

            String propertyApiKey = trimToNull(propertyLookup.apply(ModelProfileSecretSupport.dedicatedPropertyName(modelId)));
            if (propertyApiKey != null) {
                return propertyApiKey;
            }

            String dedicatedEnvApiKey = trimToNull(environmentLookup.apply(ModelProfileSecretSupport.dedicatedEnvironmentVariableName(modelId)));
            if (dedicatedEnvApiKey != null) {
                return dedicatedEnvApiKey;
            }
        }

        String apiKeyEnv = trimToNull(OpenAiCompatibleSupport.stringOption(providerOptions, "apiKeyEnv"));
        if (apiKeyEnv != null) {
            String legacyEnvApiKey = trimToNull(environmentLookup.apply(apiKeyEnv));
            if (legacyEnvApiKey != null) {
                return legacyEnvApiKey;
            }
        }

        return trimToNull(environmentLookup.apply(ModelProfileSecretSupport.DEFAULT_SHARED_ENV_VAR));
    }

    private Map<String, String> loadFileApiKeys(Path path) {
        if (!Files.exists(path)) {
            return Map.of();
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Model profile secrets path is not a file: " + path);
        }
        try (InputStream inputStream = Files.newInputStream(path)) {
            Object parsed = new Yaml().load(inputStream);
            if (!(parsed instanceof Map<?, ?> rawRoot)) {
                throw new IllegalStateException("Model profile secrets file must contain a YAML map: " + path);
            }
            Object rawSecrets = rawRoot.get(ROOT_KEY);
            if (!(rawSecrets instanceof Map<?, ?> rawSecretsMap)) {
                throw new IllegalStateException("Model profile secrets file must define " + ROOT_KEY + " as a map: " + path);
            }
            Map<String, String> secrets = new LinkedHashMap<>();
            rawSecretsMap.forEach((rawModelId, rawApiKey) -> {
                String modelId = String.valueOf(rawModelId);
                String apiKey = trimToNull(rawApiKey == null ? null : String.valueOf(rawApiKey));
                if (apiKey != null) {
                    secrets.put(modelId, apiKey);
                }
            });
            return Map.copyOf(secrets);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read model profile secrets file: " + path, exception);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalStateException) {
                throw exception;
            }
            throw new IllegalStateException("Failed to parse model profile secrets file: " + path, exception);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
