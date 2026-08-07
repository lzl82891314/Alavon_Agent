package com.example.avalon.agent.cognition;

public record PrivateCognitionDraft(BeliefState beliefs, StrategyState strategy,
                                    CommunicationPlan communication, long sourceSequence) {}
