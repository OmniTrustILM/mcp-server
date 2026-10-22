package com.otilm.mcp.tool;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * How far the operator lets clients change ILM. Each level also allows the tool kinds of the levels below it.
 */
public enum AccessLevel {

    READ_ONLY("read-only"),
    READ_WRITE("read-write"),
    FULL("full");

    private final String label;

    AccessLevel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean allows(ToolKind kind) {
        return switch (this) {
            case READ_ONLY -> kind == ToolKind.READ;
            case READ_WRITE -> kind != ToolKind.DESTRUCTIVE;
            case FULL -> true;
        };
    }

    public static AccessLevel parse(String value) {
        String given = value == null ? "" : value;
        String normalized = given.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return Arrays
                .stream(values())
                .filter(level -> level.label.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown access level '%s' in ilm.mcp.access (ILM_MCP_ACCESS). Valid values: %s."
                                .formatted(given, labels())));
    }

    private static String labels() {
        return Arrays.stream(values()).map(AccessLevel::label).collect(Collectors.joining(", "));
    }
}
