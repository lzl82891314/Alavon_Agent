package com.example.avalon.agent.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class StrategicToolSupport {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private StrategicToolSupport() {
    }

    static ToolDescriptor descriptor(String name, String description, Map<String, Object> properties,
                                     String... required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (required.length > 0) schema.put("required", List.of(required));
        schema.put("additionalProperties", false);
        return new ToolDescriptor(name, description, schema, true, ToolResultVisibility.AGENT_PRIVATE);
    }

    static List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof Collection<?> values)) return List.of();
        return values.stream().map(StrategicToolSupport::objectMap).filter(map -> !map.isEmpty()).toList();
    }

    static Map<String, Object> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?>) return stringMap(value);
        try {
            return OBJECT_MAPPER.convertValue(value, new TypeReference<Map<String, Object>>() { });
        } catch (IllegalArgumentException exception) {
            return Map.of();
        }
    }

    static List<String> strings(Object value) {
        if (!(value instanceof Collection<?> values)) return List.of();
        return values.stream().map(String::valueOf).toList();
    }

    static List<Long> sequences(Collection<Map<String, Object>> values) {
        Set<Long> result = new LinkedHashSet<>();
        values.forEach(value -> {
            addSequence(result, value.get("sequence"));
            addSequence(result, value.get("sourceEventSequence"));
            addSequence(result, value.get("proposalSequence"));
            addSequence(result, value.get("revealSequence"));
            addSequence(result, value.get("claimSequence"));
            addSequence(result, value.get("evidenceSequence"));
            numbers(value.get("sourceSequences")).forEach(result::add);
            numbers(value.get("evidenceReferences")).forEach(result::add);
            numbers(value.get("supportingEvidenceReferences")).forEach(result::add);
            numbers(value.get("discriminatingObservationReferences")).forEach(result::add);
            numbers(value.get("observedSequences")).forEach(result::add);
        });
        return List.copyOf(result);
    }

    static List<Long> numbers(Object value) {
        if (!(value instanceof Collection<?> values)) return List.of();
        List<Long> result = new ArrayList<>();
        values.stream().filter(Number.class::isInstance).map(Number.class::cast)
                .map(Number::longValue).filter(number -> number > 0).forEach(result::add);
        return List.copyOf(result);
    }

    static Integer optionalInteger(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);
        if (value == null) return null;
        if (!(value instanceof Number number)) throw new IllegalArgumentException(name + " must be an integer");
        return number.intValue();
    }

    static String optionalString(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);
        if (value == null) return null;
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException(name + " must be a non-blank string");
        return text;
    }

    static List<String> optionalStrings(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);
        if (value == null) return List.of();
        if (!(value instanceof Collection<?> values) || values.stream().anyMatch(item -> !(item instanceof String))) {
            throw new IllegalArgumentException(name + " must be an array of strings");
        }
        return values.stream().map(String.class::cast).toList();
    }

    static void rejectUnknownArguments(Map<String, Object> arguments, String... allowed) {
        Set<String> names = Set.of(allowed);
        arguments.keySet().stream().filter(name -> !names.contains(name)).findFirst()
                .ifPresent(name -> {
                    throw new IllegalArgumentException("Unknown tool argument: " + name);
                });
    }

    private static void addSequence(Set<Long> target, Object value) {
        if (value instanceof Number number && number.longValue() > 0) target.add(number.longValue());
    }
}
