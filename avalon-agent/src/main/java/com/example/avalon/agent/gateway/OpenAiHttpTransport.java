package com.example.avalon.agent.gateway;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

public interface OpenAiHttpTransport {
    SseHttpResponse postEventStream(URI uri,
                                    Map<String, String> headers,
                                    String requestBody,
                                    Duration timeout,
                                    Consumer<SseFrame> frameConsumer);
}
