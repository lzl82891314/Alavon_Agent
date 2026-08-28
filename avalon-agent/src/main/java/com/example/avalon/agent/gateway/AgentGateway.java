package com.example.avalon.agent.gateway;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.agent.model.AgentModelTurn;

public interface AgentGateway {
    AgentTurnResult playTurn(AgentTurnRequest request);

    default AgentModelTurn nextTurn(AgentTurnRequest request) {
        if (request != null && !request.getTools().isEmpty()) {
            throw new ModelToolCallingUnsupportedException(request.getProtocol());
        }
        return AgentModelTurn.completed(playTurn(request));
    }
}

