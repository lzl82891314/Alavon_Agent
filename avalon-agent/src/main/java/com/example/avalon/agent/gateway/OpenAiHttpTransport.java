package com.example.avalon.agent.gateway;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public interface OpenAiHttpTransport {
    JsonNode postChatCompletion(URI uri, Map<String, String> headers, String requestBody, Duration timeout);
}
