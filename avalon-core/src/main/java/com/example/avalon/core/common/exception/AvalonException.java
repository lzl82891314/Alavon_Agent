package com.example.avalon.core.common.exception;

public class AvalonException extends RuntimeException {
    public AvalonException(String message) {
        super(message);
    }

    public AvalonException(String message, Throwable cause) {
        super(message, cause);
    }
}

