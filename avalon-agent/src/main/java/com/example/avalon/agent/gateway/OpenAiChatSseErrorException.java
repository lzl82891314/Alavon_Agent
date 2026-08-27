package com.example.avalon.agent.gateway;

final class OpenAiChatSseErrorException extends IllegalStateException {
    private final String code;
    private final String errorMessage;
    private final String metadata;

    OpenAiChatSseErrorException(String code, String errorMessage, String metadata, String payload) {
        super("Model stream returned an error: " + payload);
        this.code = code;
        this.errorMessage = errorMessage;
        this.metadata = metadata;
    }

    String code() {
        return code;
    }

    String errorMessage() {
        return errorMessage;
    }

    String metadata() {
        return metadata;
    }
}
