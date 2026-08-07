package com.example.avalon.agent.service;

final class CandidateKnowledgeAssertionException extends IllegalStateException {
    private final String violationSummary;

    CandidateKnowledgeAssertionException(String message, String violationSummary) {
        super(message);
        this.violationSummary = violationSummary;
    }

    String violationSummary() {
        return violationSummary;
    }
}
