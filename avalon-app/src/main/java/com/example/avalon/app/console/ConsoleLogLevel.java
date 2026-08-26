package com.example.avalon.app.console;

enum ConsoleLogLevel {
    INFO, DEBUG, TRACE;

    static ConsoleLogLevel parse(String value) {
        return value == null ? INFO : valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
