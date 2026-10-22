package com.otilm.mcp.config;

import com.otilm.mcp.tool.AccessLevel;
import com.otilm.mcp.tool.Toolset;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ilm.mcp")
public record McpAccessProperties(String access, List<String> toolsets) {

    public AccessLevel accessLevel() {
        return AccessLevel.parse(access);
    }

    public Set<Toolset> enabledToolsets() {
        return Toolset.parse(toolsets);
    }
}
