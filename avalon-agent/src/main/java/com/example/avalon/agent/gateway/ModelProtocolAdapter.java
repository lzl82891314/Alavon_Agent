package com.example.avalon.agent.gateway;

/**
 * Translates a protocol-specific model API into the protocol-neutral agent turn contract.
 */
public interface ModelProtocolAdapter extends AgentGateway {
    String protocolId();
}
