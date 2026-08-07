package com.example.avalon.runtime.coordination;

public record SubmissionResult(ActionBatch batch, boolean accepted, boolean idempotentReplay, String message) {}
