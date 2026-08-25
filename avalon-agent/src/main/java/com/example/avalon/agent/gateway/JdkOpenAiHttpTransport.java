package com.example.avalon.agent.gateway;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Component
public class JdkOpenAiHttpTransport implements OpenAiHttpTransport {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdkOpenAiHttpTransport.class);
    private static final List<Integer> RETRYABLE_STATUS_CODES = List.of(429, 500, 502, 503, 504);
    private static final List<Duration> RETRY_BACKOFFS = List.of(Duration.ofMillis(500));

    private final HttpClient httpClient = HttpClient.newBuilder().build();

    @Override
    public SseHttpResponse postEventStream(URI uri,
                                           Map<String, String> headers,
                                           String requestBody,
                                           Duration timeout,
                                           Consumer<SseFrame> frameConsumer) {
        int maxAttempts = RETRY_BACKOFFS.size() + 1;
        OpenAiCompatibleTransportException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            AtomicBoolean frameReceived = new AtomicBoolean();
            try {
                HttpResponse<InputStream> response = sendStream(uri, headers, requestBody, timeout);
                LOGGER.info("http_model_stream_response host={} path={} attempt={} status={} contentType={}",
                        uri.getHost(), uri.getPath(), attempt, response.statusCode(),
                        response.headers().firstValue("Content-Type").orElse("unknown"));
                if (response.statusCode() >= 400) {
                    String body = readBody(response.body());
                    boolean retryable = isRetryableStatus(response.statusCode());
                    OpenAiCompatibleTransportException failure = streamStatusFailure(
                            uri, timeout, attempt, maxAttempts, response.statusCode(), body, retryable);
                    if (!retryable || attempt >= maxAttempts) {
                        throw failure;
                    }
                    lastFailure = failure;
                    sleepBeforeRetry(uri, timeout, attempt, maxAttempts);
                    continue;
                }
                consumeStreamWithDeadline(response, timeout, attempt, frame -> {
                    frameReceived.set(true);
                    frameConsumer.accept(frame);
                });
                return new SseHttpResponse(response.statusCode(), response.headers().map(), attempt);
            } catch (OpenAiCompatibleTransportException exception) {
                LOGGER.error("http_model_stream_failure host={} path={} attempt={} error={}",
                        uri.getHost(), uri.getPath(), attempt, exception.getMessage());
                boolean retryable = retryable(exception) && !frameReceived.get();
                if (!retryable || attempt >= maxAttempts) {
                    throw exception;
                }
                lastFailure = exception;
                sleepBeforeRetry(uri, timeout, attempt, maxAttempts);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw transportFailure(uri, timeout, attempt, maxAttempts, exception, false, null);
            } catch (IOException exception) {
                LOGGER.error("http_model_stream_io_failure host={} path={} attempt={} receivedFrame={} error={}",
                        uri.getHost(), uri.getPath(), attempt, frameReceived.get(), exception.getMessage());
                OpenAiCompatibleTransportException failure = streamFailure(
                        uri, timeout, attempt, maxAttempts, exception, !frameReceived.get());
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

    private HttpResponse<InputStream> sendStream(URI uri,
                                                 Map<String, String> headers,
                                                 String requestBody,
                                                 Duration timeout) throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream");
        for (Map.Entry<String, String> header : headers.entrySet()) {
            requestBuilder.header(header.getKey(), header.getValue());
        }
        return httpClient.send(
                requestBuilder.POST(HttpRequest.BodyPublishers.ofString(requestBody)).build(),
                HttpResponse.BodyHandlers.ofInputStream()
        );
    }

    private void consumeStreamWithDeadline(HttpResponse<InputStream> response,
                                           Duration timeout,
                                           int attempt,
                                           Consumer<SseFrame> frameConsumer) throws IOException, InterruptedException {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        Future<?> future = executor.submit(() -> {
            try {
                consumeStream(response, timeout, attempt, frameConsumer);
            } catch (IOException exception) {
                throw new StreamReadException(exception);
            }
        });
        try {
            future.get(Math.max(1L, timeout.toMillis()), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            HttpTimeoutException timeoutException = new HttpTimeoutException(
                    "Model SSE stream read timed out after " + timeout.toMillis() + " ms");
            timeoutException.initCause(exception);
            try {
                response.body().close();
            } catch (IOException closeFailure) {
                timeoutException.addSuppressed(closeFailure);
            }
            future.cancel(true);
            throw timeoutException;
        } catch (InterruptedException exception) {
            try {
                response.body().close();
            } catch (IOException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            future.cancel(true);
            throw exception;
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof StreamReadException streamReadException) {
                throw streamReadException.ioException();
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IOException("Model SSE stream reader failed", cause);
        } finally {
            executor.shutdownNow();
        }
    }

    private void consumeStream(HttpResponse<InputStream> response,
                               Duration timeout,
                               int attempt,
                               Consumer<SseFrame> frameConsumer) throws IOException {
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (!contentType.toLowerCase(java.util.Locale.ROOT).contains("text/event-stream")) {
            String body = readBody(response.body());
            throw new OpenAiCompatibleTransportException(
                    "Model stream returned unexpected content type: " + contentType,
                    null,
                    diagnostics(
                            "http_response",
                            "unexpected_content_type",
                            response.uri(),
                            timeout,
                            attempt,
                            false,
                            response.statusCode(),
                            Map.of(
                                    "contentType", contentType,
                                    "bodyPreview", bodyPreview(body)
                            )
                    )
            );
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String event = "";
            StringBuilder data = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    emitFrame(frameConsumer, event, data);
                    event = "";
                    data.setLength(0);
                } else if (line.startsWith("event:")) {
                    event = fieldValue(line);
                } else if (line.startsWith("data:")) {
                    if (!data.isEmpty()) {
                        data.append('\n');
                    }
                    data.append(fieldValue(line));
                }
            }
            emitFrame(frameConsumer, event, data);
        }
    }

    private void emitFrame(Consumer<SseFrame> frameConsumer, String event, StringBuilder data) {
        if (!data.isEmpty()) {
            frameConsumer.accept(new SseFrame(event, data.toString()));
        }
    }

    private String fieldValue(String line) {
        String value = line.substring(line.indexOf(':') + 1);
        return value.startsWith(" ") ? value.substring(1) : value;
    }

    private String readBody(InputStream body) throws IOException {
        if (body == null) {
            return "";
        }
        try (body) {
            return new String(body.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private OpenAiCompatibleTransportException streamStatusFailure(URI uri,
                                                                   Duration timeout,
                                                                   int attempt,
                                                                   int maxAttempts,
                                                                   int statusCode,
                                                                   String body,
                                                                   boolean retryable) {
        String preview = bodyPreview(body);
        return new OpenAiCompatibleTransportException(
                "Model SSE request failed with status " + statusCode + " after " + attempt + "/"
                        + maxAttempts + " attempts: " + preview,
                null,
                diagnostics(
                        "transport",
                        retryable ? "retryable_http_status" : "http_status",
                        uri,
                        timeout,
                        attempt,
                        retryable,
                        statusCode,
                        Map.of("bodyPreview", preview)
                )
        );
    }

    private OpenAiCompatibleTransportException streamFailure(URI uri,
                                                              Duration timeout,
                                                              int attempt,
                                                              int maxAttempts,
                                                              IOException exception,
                                                              boolean retryable) {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("rootExceptionClass", exception.getClass().getName());
        if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
            extras.put("rootExceptionMessage", exception.getMessage());
        }
        return new OpenAiCompatibleTransportException(
                "Model SSE stream failed after " + attempt + "/" + maxAttempts + " attempts"
                        + (retryable ? " before the first frame" : " after streaming started"),
                exception,
                diagnostics(
                        "transport",
                        retryable ? failureKind(exception) : "stream_interrupted",
                        uri,
                        timeout,
                        attempt,
                        retryable,
                        null,
                        extras
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

    private static final class StreamReadException extends RuntimeException {
        private StreamReadException(IOException cause) {
            super(cause);
        }

        private IOException ioException() {
            return (IOException) getCause();
        }
    }
}
