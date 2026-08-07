package com.example.avalon.config.io;

import com.example.avalon.config.exception.ConfigLoadException;
import com.example.avalon.config.model.AvalonConfigRegistry;
import com.example.avalon.config.model.LlmModelDefinition;
import com.example.avalon.config.service.SetupValidationService;
import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.role.enums.KnowledgeRuleType;
import com.example.avalon.core.role.model.KnowledgeRuleDefinition;
import com.example.avalon.core.role.model.RoleDefinition;
import com.example.avalon.core.setup.model.AssassinationRuleDefinition;
import com.example.avalon.core.setup.model.RoundTeamSizeRule;
import com.example.avalon.core.setup.model.RuleSetDefinition;
import com.example.avalon.core.setup.model.SetupTemplate;
import com.example.avalon.core.setup.model.VisibilityPolicyDefinition;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class YamlConfigLoader {
    private static final String RULES_DIR = "rules";
    private static final String ROLES_DIR = "roles";
    private static final String SETUPS_DIR = "setups";
    private static final String MODEL_PROFILES_DIR = "model-profiles";

    private final SetupValidationService validationService;

    public YamlConfigLoader(SetupValidationService validationService) {
        this.validationService = Objects.requireNonNull(validationService, "validationService");
    }

    public AvalonConfigRegistry load(Path resourceRoot) {
        Objects.requireNonNull(resourceRoot, "resourceRoot");
        Path rulesDir = resourceRoot.resolve(RULES_DIR);
        Path rolesDir = resourceRoot.resolve(ROLES_DIR);
        Path setupsDir = resourceRoot.resolve(SETUPS_DIR);
        Path modelProfilesDir = resourceRoot.resolve(MODEL_PROFILES_DIR);

        AvalonConfigRegistry registry = new AvalonConfigRegistry(
                loadRuleSets(rulesDir),
                loadRoles(rolesDir),
                loadSetupTemplates(setupsDir),
                loadModelProfiles(modelProfilesDir)
        );
        validationService.validate(registry);
        return registry;
    }

    public AvalonConfigRegistry loadAndValidate(Path resourceRoot) {
        return load(resourceRoot);
    }

    private Map<String, RuleSetDefinition> loadRuleSets(Path directory) {
        return readYamlFiles(directory, true).stream()
                .map(path -> parseRuleSet(path, readYamlMap(path)))
                .collect(Collectors.toMap(
                        RuleSetDefinition::ruleSetId,
                        definition -> definition,
                        (left, right) -> {
                            throw new ConfigLoadException("Duplicate rule set id: " + left.ruleSetId());
                        },
                        LinkedHashMap::new
                ));
    }

    private Map<String, RoleDefinition> loadRoles(Path directory) {
        return readYamlFiles(directory, true).stream()
                .map(path -> parseRoleDefinition(path, readYamlMap(path)))
                .collect(Collectors.toMap(
                        RoleDefinition::roleId,
                        definition -> definition,
                        (left, right) -> {
                            throw new ConfigLoadException("Duplicate role id: " + left.roleId());
                        },
                        LinkedHashMap::new
                ));
    }

    private Map<String, SetupTemplate> loadSetupTemplates(Path directory) {
        return readYamlFiles(directory, true).stream()
                .map(path -> parseSetupTemplate(path, readYamlMap(path)))
                .collect(Collectors.toMap(
                        SetupTemplate::templateId,
                        definition -> definition,
                        (left, right) -> {
                            throw new ConfigLoadException("Duplicate setup template id: " + left.templateId());
                        },
                        LinkedHashMap::new
                ));
    }

    private Map<String, LlmModelDefinition> loadModelProfiles(Path directory) {
        return readYamlFiles(directory, false).stream()
                .map(path -> parseModelProfile(path, readYamlMap(path)))
                .collect(Collectors.toMap(
                        LlmModelDefinition::modelId,
                        definition -> definition,
                        (left, right) -> {
                            throw new ConfigLoadException("Duplicate model profile id: " + left.modelId());
                        },
                        LinkedHashMap::new
                ));
    }

    private List<Path> readYamlFiles(Path directory, boolean required) {
        if (!Files.isDirectory(directory)) {
            if (!required) {
                return List.of();
            }
            throw new ConfigLoadException("Missing config directory: " + directory);
        }
        try (var stream = Files.list(directory)) {
            return stream
                    .filter(path -> Files.isRegularFile(path) && isYaml(path))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .collect(Collectors.toCollection(ArrayList::new));
        } catch (IOException e) {
            throw new ConfigLoadException("Failed to list config directory: " + directory, e);
        }
    }

    private static boolean isYaml(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".yml") || fileName.endsWith(".yaml");
    }

    private Map<String, Object> readYamlMap(Path file) {
        try (InputStream inputStream = Files.newInputStream(file)) {
            Object parsed = new Yaml().load(inputStream);
            if (parsed == null) {
                throw new ConfigLoadException("Empty YAML file: " + file);
            }
            if (!(parsed instanceof Map<?, ?> rawMap)) {
                throw new ConfigLoadException("Expected YAML map at root: " + file);
            }
            Map<String, Object> map = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> map.put(String.valueOf(key), value));
            return map;
        } catch (IOException e) {
            throw new ConfigLoadException("Failed to read YAML file: " + file, e);
        } catch (RuntimeException e) {
            throw new ConfigLoadException("Failed to parse YAML file: " + file, e);
        }
    }

    private RuleSetDefinition parseRuleSet(Path file, Map<String, Object> yaml) {
        return new RuleSetDefinition(
                requiredString(yaml, "ruleSetId", file),
                requiredString(yaml, "name", file),
                requiredString(yaml, "version", file),
                requiredInt(yaml, "minPlayers", file),
                requiredInt(yaml, "maxPlayers", file),
                teamSizeRules(yaml.get("teamSizeRules"), file),
                missionThresholds(yaml.get("missionRule"), file),
                stringList(yaml.get("supportedSetupTemplateIds")),
                assassinationRule(yaml.get("assassinationRule"), file),
                visibilityPolicy(yaml.get("visibilityPolicy")),
                randomAssignment(yaml.get("setupRule"))
        );
    }

    private RoleDefinition parseRoleDefinition(Path file, Map<String, Object> yaml) {
        return new RoleDefinition(
                requiredString(yaml, "roleId", file),
                requiredString(yaml, "displayName", file),
                Camp.valueOf(requiredString(yaml, "camp", file).trim().toUpperCase(Locale.ROOT)),
                requiredString(yaml, "description", file),
                knowledgeRules(yaml.get("knowledgeRules"), file),
                stringList(yaml.get("actionCapabilities")),
                requiredBoolean(yaml, "canLead", file),
                requiredBoolean(yaml, "canVote", file),
                requiredBoolean(yaml, "canJoinMission", file),
                requiredBoolean(yaml, "canAssassinate", file),
                stringList(yaml.get("passiveTraits"))
        );
    }

    private SetupTemplate parseSetupTemplate(Path file, Map<String, Object> yaml) {
        return new SetupTemplate(
                requiredString(yaml, "templateId", file),
                requiredInt(yaml, "playerCount", file),
                requiredBoolean(yaml, "enabled", file),
                stringList(yaml.get("roleIds"))
        );
    }

    private LlmModelDefinition parseModelProfile(Path file, Map<String, Object> yaml) {
        return new LlmModelDefinition(
                requiredString(yaml, "modelId", file),
                requiredString(yaml, "displayName", file),
                requiredString(yaml, "provider", file),
                requiredString(yaml, "modelName", file),
                optionalDouble(yaml, "temperature", file),
                optionalInt(yaml, "maxTokens", file),
                asOptionalMap(yaml.get("providerOptions"), "providerOptions", file),
                requiredBoolean(yaml, "enabled", file)
        );
    }

    private List<RoundTeamSizeRule> teamSizeRules(Object value, Path file) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new ConfigLoadException("teamSizeRules must be a list: " + file);
        }
        List<RoundTeamSizeRule> result = new ArrayList<>();
        for (Object element : list) {
            Map<String, Object> map = asMap(element, "teamSizeRules entry", file);
            result.add(new RoundTeamSizeRule(
                    requiredInt(map, "round", file),
                    requiredInt(map, "teamSize", file)
            ));
        }
        return result;
    }

    private Map<Integer, Integer> missionThresholds(Object value, Path file) {
        Map<String, Object> map = asOptionalMap(value, "missionRule", file);
        if (map == null) {
            return Map.of();
        }
        Object thresholds = map.get("failThresholdByRound");
        if (thresholds == null) {
            return Map.of();
        }
        if (!(thresholds instanceof Map<?, ?> rawThresholds)) {
            throw new ConfigLoadException("missionRule.failThresholdByRound must be a map: " + file);
        }
        Map<Integer, Integer> result = new LinkedHashMap<>();
        rawThresholds.forEach((key, entryValue) ->
                result.put(asInt(key, "missionRule.failThresholdByRound.key", file), asInt(entryValue, "missionRule.failThresholdByRound." + key, file))
        );
        return result;
    }

    private Double optionalDouble(Map<String, Object> yaml, String key, Path file) {
        Object value = yaml.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new ConfigLoadException("Expected numeric " + key + " in " + file);
    }

    private Integer optionalInt(Map<String, Object> yaml, String key, Path file) {
        Object value = yaml.get(key);
        if (value == null) {
            return null;
        }
        return asInt(value, key, file);
    }

    private AssassinationRuleDefinition assassinationRule(Object value, Path file) {
        Map<String, Object> map = asOptionalMap(value, "assassinationRule", file);
        if (map == null) {
            return new AssassinationRuleDefinition(false, "", "");
        }
        return new AssassinationRuleDefinition(
                requiredBoolean(map, "enabled", file),
                requiredString(map, "assassinRoleId", file),
                requiredString(map, "merlinRoleId", file)
        );
    }

    private VisibilityPolicyDefinition visibilityPolicy(Object value) {
        Map<String, Object> map = asOptionalMap(value, "visibilityPolicy", null);
        if (map == null) {
            return new VisibilityPolicyDefinition(true);
        }
        Object raw = map.get("useRoleKnowledgeRules");
        return new VisibilityPolicyDefinition(raw == null || Boolean.parseBoolean(String.valueOf(raw)));
    }

    private boolean randomAssignment(Object value) {
        Map<String, Object> map = asOptionalMap(value, "setupRule", null);
        if (map == null) {
            return true;
        }
        Object raw = map.get("randomAssignment");
        return raw == null || Boolean.parseBoolean(String.valueOf(raw));
    }

    private List<KnowledgeRuleDefinition> knowledgeRules(Object value, Path file) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new ConfigLoadException("knowledgeRules must be a list: " + file);
        }
        List<KnowledgeRuleDefinition> result = new ArrayList<>();
        for (Object element : list) {
            Map<String, Object> map = asMap(element, "knowledgeRules entry", file);
            Camp targetCamp = null;
            Object rawTargetCamp = map.get("targetCamp");
            if (rawTargetCamp != null) {
                targetCamp = Camp.valueOf(String.valueOf(rawTargetCamp).trim().toUpperCase(Locale.ROOT));
            }
            KnowledgeRuleType type = normalizeKnowledgeRuleType(requiredString(map, "type", file), map, file);
            result.add(new KnowledgeRuleDefinition(
                    type,
                    targetCamp,
                    targetRoles(type, map, file),
                    stringList(map.get("exclusions"))
            ));
        }
        return result;
    }

    private KnowledgeRuleType normalizeKnowledgeRuleType(String rawType, Map<String, Object> map, Path file) {
        String normalized = rawType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SEE_PLAYERS_BY_CAMP" -> KnowledgeRuleType.SEE_PLAYERS_BY_CAMP;
            case "SEE_PLAYERS_BY_ROLE" -> KnowledgeRuleType.SEE_PLAYERS_BY_ROLE;
            case "SEE_ROLE_AMBIGUITY" -> KnowledgeRuleType.SEE_ROLE_AMBIGUITY;
            case "MISLEAD_PERCIVAL" -> throw new ConfigLoadException(
                    "Unsupported knowledge rule type '" + rawType + "' in " + file
                            + "; use SEE_ROLE_AMBIGUITY on Percival instead"
            );
            case "SEE_ALLIED_EVIL_PLAYERS" -> KnowledgeRuleType.SEE_ALLIED_EVIL_PLAYERS;
            default -> throw new ConfigLoadException("Unsupported knowledge rule type '" + rawType + "' in " + file);
        };
    }

    private List<String> targetRoles(KnowledgeRuleType type, Map<String, Object> map, Path file) {
        List<String> explicitTargetRoles = stringList(map.get("targetRoles"));
        if (type == KnowledgeRuleType.SEE_PLAYERS_BY_ROLE || type == KnowledgeRuleType.SEE_ROLE_AMBIGUITY) {
            if (explicitTargetRoles.isEmpty()) {
                throw new ConfigLoadException("knowledgeRules.targetRoles must not be empty for " + type + " in " + file);
            }
        }
        return explicitTargetRoles;
    }

    private Map<String, Object> asOptionalMap(Object value, String fieldName, Path file) {
        if (value == null) {
            return null;
        }
        return asMap(value, fieldName, file);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value, String fieldName, Path file) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            throw new ConfigLoadException(fieldName + " must be a map" + (file == null ? "" : ": " + file));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, entryValue) -> result.put(String.valueOf(key), entryValue));
        return result;
    }

    private String requiredString(Map<String, Object> yaml, String fieldName, Path file) {
        Object value = yaml.get(fieldName);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new ConfigLoadException("Missing required field '" + fieldName + "' in " + file);
        }
        return String.valueOf(value);
    }

    private int requiredInt(Map<String, Object> yaml, String fieldName, Path file) {
        Object value = yaml.get(fieldName);
        if (value == null) {
            throw new ConfigLoadException("Missing required field '" + fieldName + "' in " + file);
        }
        return asInt(value, fieldName, file);
    }

    private boolean requiredBoolean(Map<String, Object> yaml, String fieldName, Path file) {
        Object value = yaml.get(fieldName);
        if (value == null) {
            throw new ConfigLoadException("Missing required field '" + fieldName + "' in " + file);
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private int asInt(Object value, String fieldName, Path file) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new ConfigLoadException("Invalid integer for '" + fieldName + "' in " + file + ": " + value, e);
        }
    }

    private List<String> stringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new ConfigLoadException("Expected list value");
        }
        List<String> result = new ArrayList<>();
        for (Object element : list) {
            result.add(String.valueOf(element));
        }
        return List.copyOf(result);
    }
}
