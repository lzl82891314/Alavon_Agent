package com.example.avalon.agent.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class JdkOpenAiHttpTransport implements OpenAiHttpTransport {
    private static final List<Integer> RETRYABLE_STATUS_CODES = List.of(429, 500, 502, 503, 504);
    private static final List<Duration> RETRY_BACKOFFS = List.of(Duration.ofMillis(500), Duration.ofMillis(1500));

    private final HttpClient httpClient = HttpClient.newBuilder().build();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Override
    public JsonNode postChatCompletion(URI uri, Map<String, String> headers, String requestBody, Duration timeout) {
        int maxAttempts = RETRY_BACKOFFS.size() + 1;
        OpenAiCompatibleTransportException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<String> response = send(uri, headers, requestBody, timeout);
                if (response.statusCode() >= 400) {
                    boolean retryable = isRetryableStatus(response.statusCode());
                    OpenAiCompatibleTransportException failure = statusFailure(
                            uri,
                            timeout,
                            attempt,
                            maxAttempts,
                            response,
                            retryable
                    );
                    if (!retryable || attempt >= maxAttempts) {
                        throw failure;
                    }
                    lastFailure = failure;
                    sleepBeforeRetry(uri, timeout, attempt, maxAttempts);
                    continue;
                }
                return parseJsonResponse(response, uri, timeout, attempt);
            } catch (OpenAiCompatibleTransportException exception) {
                boolean retryable = retryable(exception);
                if (!retryable || attempt >= maxAttempts) {
                    throw exception;
                }
                lastFailure = exception;
                sleepBeforeRetry(uri, timeout, attempt, maxAttempts);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw transportFailure(uri, timeout, attempt, maxAttempts, exception, false, null);
            } catch (IOException exception) {
                OpenAiCompatibleTransportException failure = transportFailure(
                        uri,
                        timeout,
                        attempt,
                        maxAttempts,
                        exception,
                        retryable(exception),
                        null
                );
                if (!retryable(failure) || attempt >= maxAttempts) {
                    throw failure;
                }
                lastFailure = failure;
                sleepBeforeRetry(uri, timeout, attempt, maxAttempts);
            }
        }
        throw lastFailure == null
                ? transportFailure(uri, timeout, maxAttempts, maxAttempts, null, false, null)
                : lastFailure;
    }

    private HttpResponse<String> send(URI uri,
                                      Map<String, String> headers,
                                      String requestBody,
                                      Duration timeout) throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Content-Type", "application/json");
        for (Map.Entry<String, String> header : headers.entrySet()) {
            requestBuilder.header(header.getKey(), header.getValue());
        }
        return httpClient.send(
                requestBuilder.POST(HttpRequest.BodyPublishers.ofString(requestBody)).build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private JsonNode parseJsonResponse(HttpResponse<String> response, URI uri, Duration timeout, int attempts) {
        try {
            return objectMapper.readTree(response.body());
        } catch (Exception exception) {
            String contentType = response.headers().firstValue("Content-Type").orElse("unknown");
            throw new OpenAiCompatibleTransportException(
                    "OpenAI-compatible response body was not valid JSON (status "
                            + response.statusCode()
                            + ", contentType="
                            + contentType
                            + ", bodyPreview="
                            + bodyPreview(response.body())
                            + ")",
                    exception,
                    diagnostics(
                            "http_response",
                            "non_json_success_body",
                            uri,
                            timeout,
                            attempts,
                            false,
                            response.statusCode(),
                            Map.of(
                                    "contentType", contentType,
                                    "bodyPreview", bodyPreview(response.body())
                            )
                    )
            );
        }
    }

    private OpenAiCompatibleTransportException statusFailure(URI uri,
                                                             Duration timeout,
                                                             int attempt,
                                                             int maxAttempts,
                                                             HttpResponse<String> response,
                                                             boolean retryable) {
        String preview = bodyPreview(response.body());
        return new OpenAiCompatibleTransportException(
                "OpenAI-compatible request failed with status "
                        + response.statusCode()
                        + " after "
                        + attempt
                        + "/"
                        + maxAttempts
                        + " attempts: "
                        + preview,
                null,
                diagnostics(
                        "transport",
                        retryable ? "retryable_http_status" : "http_status",
                        uri,
                        timeout,
                        attempt,
                        retryable,
                        response.statusCode(),
                        Map.of("bodyPreview", preview)
                )
        );
    }

    private OpenAiCompatibleTransportException transportFailure(URI uri,
                                                                Duration timeout,
                                                                int attempt,
                                                                int maxAttempts,
                                                                Exception exception,
                                                                boolean retryable,
                                                                Integer statusCode) {
        String rootClass = exception == null ? null : exception.getClass().getName();
        String rootMessage = exception == null ? null : exception.getMessage();
        Map<String, Object> extras = new LinkedHashMap<>();
        if (rootClass != null) {
            extras.put("rootExceptionClass", rootClass);
        }
        if (rootMessage != null && !rootMessage.isBlank()) {
            extras.put("rootExceptionMessage", rootMessage);
        }
        String message = "OpenAI-compatible HTTP transport failed after "
                + attempt
                + "/"
                + maxAttempts
                + " attempts"
                + (rootClass == null ? "" : " (" + rootClass + (rootMessage == null || rootMessage.isBlank() ? "" : ": " + rootMessage) + ")");
        return new OpenAiCompatibleTransportException(
                message,
                exception,
                diagnostics(
                        "transport",
                        failureKind(exception),
                        uri,
                        timeout,
                        attempt,
                        retryable,
                        statusCode,
                        extras
                )
        );
    }

    private Map<String, Object> diagnostics(String failureDomain,
                                            String failureKind,
                                            URI uri,
                                            Duration timeout,
                                            int attempts,
                                            boolean retryable,
                                            Integer statusCode,
                                            Map<String, Object> extras) {
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("failureDomain", failureDomain);
        diagnostics.put("failureKind", failureKind);
        diagnostics.put("requestHost", uri == null ? null : uri.getHost());
        diagnostics.put("uri", uri == null ? null : uri.toString());
        diagnostics.put("timeoutMs", timeout == null ? null : timeout.toMillis());
        diagnostics.put("transportAttempts", attempts);
        diagnostics.put("retryable", retryable);
        if (statusCode != null) {
            diagnostics.put("statusCode", statusCode);
        }
        if (extras != null && !extras.isEmpty()) {
            diagnostics.putAll(extras);
        }
        return diagnostics;
    }

    private boolean retryable(OpenAiCompatibleTransportException exception) {
        return Boolean.TRUE.equals(exception.diagnostics().get("retryable"));
    }

    private boolean retryable(Exception exception) {
        return exception instanceof HttpTimeoutException
                || exception instanceof ConnectException
                || exception instanceof IOException;
    }

    private boolean isRetryableStatus(int statusCode) {
        return RETRYABLE_STATUS_CODES.contains(statusCode);
    }

    private String failureKind(Exception exception) {
        if (exception instanceof HttpTimeoutException) {
            return "timeout";
        }
        if (exception instanceof ConnectException) {
            return "connect";
        }
        if (exception instanceof InterruptedException) {
            return "interrupted";
        }
        if (exception instanceof IOException) {
            return "io";
        }
        return "transport_error";
    }

    private void sleepBeforeRetry(URI uri, Duration timeout, int attempt, int maxAttempts) {
        if (attempt > RETRY_BACKOFFS.size()) {
            return;
        }
        try {
            Thread.sleep(RETRY_BACKOFFS.get(attempt - 1).toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw transportFailure(uri, timeout, attempt, maxAttempts, exception, false, null);
        }
    }

    private String bodyPreview(String body) {
        if (body == null || body.isBlank()) {
            return "<empty>";
        }
        String normalized = body.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 160) {
            return normalized;
        }
        return normalized.substring(0, 160) + "...";
    }
}
