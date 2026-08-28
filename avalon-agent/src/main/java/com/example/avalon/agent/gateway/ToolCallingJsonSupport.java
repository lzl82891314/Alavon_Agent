package com.example.avalon.agent.gateway;

import com.example.avalon.agent.tool.ToolCall;
import com.example.avalon.agent.tool.ToolDescriptor;
import com.example.avalon.agent.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;

final class ToolCallingJsonSupport {
    private ToolCallingJsonSupport() {
    }

    static void addOpenAiTools(ObjectMapper json, ObjectNode root, Iterable<ToolDescriptor> descriptors) {
        ArrayNode tools = root.putArray("tools");
        for (ToolDescriptor descriptor : descriptors) {
            ObjectNode function = tools.addObject().put("type", "function").putObject("function");
            function.put("name", descriptor.name());
            function.put("description", descriptor.description());
            function.set("parameters", json.valueToTree(descriptor.inputSchema()));
        }
        root.put("tool_choice", "auto");
    }

    static void addResponsesTools(ObjectMapper json, ObjectNode root, Iterable<ToolDescriptor> descriptors) {
        ArrayNode tools = root.putArray("tools");
        for (ToolDescriptor descriptor : descriptors) {
            ObjectNode function = tools.addObject().put("type", "function");
            function.put("name", descriptor.name());
            function.put("description", descriptor.description());
            function.set("parameters", json.valueToTree(descriptor.inputSchema()));
        }
        root.put("tool_choice", "auto");
    }

    static void addAnthropicTools(ObjectMapper json, ObjectNode root, Iterable<ToolDescriptor> descriptors) {
        ArrayNode tools = root.putArray("tools");
        for (ToolDescriptor descriptor : descriptors) {
            ObjectNode tool = tools.addObject();
            tool.put("name", descriptor.name());
            tool.put("description", descriptor.description());
            tool.set("input_schema", json.valueToTree(descriptor.inputSchema()));
        }
        root.putObject("tool_choice").put("type", "auto");
    }

    static Map<String, Object> arguments(ObjectMapper json, JsonNode value) {
        try {
            JsonNode parsed = value == null || value.isNull() ? json.createObjectNode()
                    : value.isTextual() ? json.readTree(value.asText("{}")) : value;
            if (parsed == null || !parsed.isObject()) {
                throw new IllegalStateException("Tool arguments must be a JSON object");
            }
            Map<String, Object> arguments = json.convertValue(parsed,
                    new TypeReference<LinkedHashMap<String, Object>>() { });
            return Map.copyOf(arguments);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Tool arguments were not valid JSON", exception);
        }
    }

    static String resultJson(ObjectMapper json, ToolResult result) {
        try {
            return json.writeValueAsString(Map.of(
                    "status", result.status().name(),
                    "content", result.content(),
                    "sourceSequences", result.sourceSequences(),
                    "errorType", result.errorType() == null ? "" : result.errorType(),
                    "errorMessage", result.errorMessage() == null ? "" : result.errorMessage()));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize tool result", exception);
        }
    }

    static ObjectNode openAiToolCall(ObjectMapper json, ToolCall call) {
        ObjectNode node = json.createObjectNode();
        node.put("id", call.callId());
        node.put("type", "function");
        ObjectNode function = node.putObject("function");
        function.put("name", call.toolName());
        try {
            function.put("arguments", json.writeValueAsString(call.arguments()));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize tool arguments", exception);
        }
        return node;
    }
}
