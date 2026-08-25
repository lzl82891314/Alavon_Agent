package com.example.avalon.agent.gateway;

@FunctionalInterface
public interface ModelStreamEventListener {
    void onModelStreamEvent(ModelStreamEvent event);
}
