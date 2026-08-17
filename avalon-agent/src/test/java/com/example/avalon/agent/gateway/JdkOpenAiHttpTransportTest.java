package com.example.avalon.agent.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdkOpenAiHttpTransportTest {
    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void postsJsonAndParsesSuccessfulResponse() {
        server.createContext("/v1/chat/completions", exchange -> respond(exchange, 200,
                "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}"));

        JsonNode response = transport().postChatCompletion(endpoint(), Map.of("Authorization", "Bearer test"),
                "{\"model\":\"test\"}", Duration.ofSeconds(2));

        assertEquals("ok", response.at("/choices/0/message/content").asText());
    }

    @Test
    void exposesHttpStatusAndResponsePreviewForNonRetryableFailure() {
        server.createContext("/v1/chat/completions", exchange -> respond(exchange, 401, "unauthorized"));

        OpenAiCompatibleTransportException exception = assertThrows(
                OpenAiCompatibleTransportException.class,
                () -> transport().postChatCompletion(endpoint(), Map.of(), "{}", Duration.ofSeconds(2))
        );

        assertEquals("transport", exception.diagnostics().get("failureDomain"));
        assertEquals(401, exception.diagnostics().get("statusCode"));
        assertEquals("unauthorized", exception.diagnostics().get("bodyPreview"));
    }

    @Test
    void exposesNonJsonSuccessBody() {
        server.createContext("/v1/chat/completions", exchange -> respond(exchange, 200, "upstream html"));

        OpenAiCompatibleTransportException exception = assertThrows(
                OpenAiCompatibleTransportException.class,
                () -> transport().postChatCompletion(endpoint(), Map.of(), "{}", Duration.ofSeconds(2))
        );

        assertEquals("http_response", exception.diagnostics().get("failureDomain"));
        assertEquals(200, exception.diagnostics().get("statusCode"));
        assertEquals("upstream html", exception.diagnostics().get("bodyPreview"));
    }

    private JdkOpenAiHttpTransport transport() {
        return new JdkOpenAiHttpTransport();
    }

    private URI endpoint() {
        return URI.create("http://localhost:" + server.getAddress().getPort() + "/v1/chat/completions");
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (exchange) {
            exchange.getResponseBody().write(bytes);
        }
    }
}
