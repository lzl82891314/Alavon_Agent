package com.example.avalon.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

enum AvalonLaunchMode {
    CONSOLE,
    SERVER;

    String[] launchArgs(String[] args) {
        List<String> values = new ArrayList<>(Arrays.asList(args));
        switch (this) {
            case CONSOLE -> {
                put(values, "avalon.console.enabled", "true");
                put(values, "spring.main.web-application-type", "none");
            }
            case SERVER -> {
                put(values, "avalon.console.enabled", "false");
                put(values, "spring.main.web-application-type", "servlet");
            }
        }
        return values.toArray(String[]::new);
    }

    static AvalonLaunchMode resolve(String[] args) {
        for (String arg : args) {
            if (arg == null || arg.isBlank()) {
                continue;
            }
            if (arg.startsWith("--avalon.mode=")) {
                String value = arg.substring("--avalon.mode=".length()).trim();
                if ("server".equalsIgnoreCase(value)) {
                    return SERVER;
                }
                if ("console".equalsIgnoreCase(value)) {
                    return CONSOLE;
                }
            }
        }
        return CONSOLE;
    }

    private void put(List<String> args, String key, String value) {
        String prefix = "--" + key + "=";
        for (int index = 0; index < args.size(); index++) {
            String current = args.get(index);
            if (current != null && current.startsWith(prefix)) {
                args.set(index, prefix + value);
                return;
            }
        }
        args.add(prefix + value);
    }
}
