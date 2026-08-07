package com.example.avalon.api.dto;

import java.util.ArrayList;
import java.util.List;

public class ModelProfileProbeRequest {
    private List<String> checks = new ArrayList<>();

    public List<String> getChecks() {
        return checks;
    }

    public void setChecks(List<String> checks) {
        this.checks = checks == null ? new ArrayList<>() : new ArrayList<>(checks);
    }
}
