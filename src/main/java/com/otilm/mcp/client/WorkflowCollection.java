package com.otilm.mcp.client;

import java.util.Locale;

/**
 * The kinds of ILM workflow objects, each served under {@code /v1/workflows/<path>}.
 */
public enum WorkflowCollection {

    CONDITIONS,
    RULES,
    EXECUTIONS,
    ACTIONS,
    TRIGGERS;

    public String path() {
        return name().toLowerCase(Locale.ROOT);
    }
}
