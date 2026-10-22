package com.otilm.mcp.tool;

import java.util.List;

final class ToolNames {

    private ToolNames() {
    }

    static List<String> allowed(AccessLevel level, Object toolObject) {
        return ToolSpecifications
                .allowed(level, List.of(toolObject))
                .stream()
                .map(specification -> specification.tool().name())
                .toList();
    }
}
