package com.example.avalon.agent.gateway;

import java.util.List;
import java.util.Map;

public record SseHttpResponse(
        int statusCode,
        Map<String, List<String>> headers,
        int transportAttempts
) {
}
