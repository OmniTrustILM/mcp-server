package com.otilm.mcp.config;

import com.otilm.mcp.tool.AccessLevel;
import com.otilm.mcp.tool.SearchTool;
import com.otilm.mcp.tool.ToolSpecifications;
import com.otilm.mcp.tool.Toolset;
import io.modelcontextprotocol.server.McpServerFeatures;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The only place tools are registered: Spring AI's own annotation scanning and tool-callback conversion are switched
 * off, so a tool reaches clients only when the access level and the toolsets allow it.
 */
@Configuration
@EnableConfigurationProperties(McpAccessProperties.class)
public class McpToolConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(McpToolConfiguration.class);

    @Bean
    public List<McpServerFeatures.SyncToolSpecification> toolSpecifications(McpAccessProperties access,
            IlmApiProperties ilmApi, McpServerProperties mcpServer, SearchTool searchTool, BeanFactory beanFactory) {
        AccessLevel level = access.accessLevel();
        Set<Toolset> toolsets = access.enabledToolsets();
        AccessChecks.verify(level, ilmApi.getAuth().getMethod(), mcpServer);
        List<Object> tools = Stream
                .concat(Stream.of(searchTool),
                        toolsets
                                .stream()
                                .flatMap(toolset -> toolset.toolClasses().stream())
                                .distinct()
                                .map(beanFactory::getBean))
                .toList();
        List<McpServerFeatures.SyncToolSpecification> specifications = ToolSpecifications.allowed(level, tools);
        String toolsetLabels = toolsets.stream().map(Toolset::label).collect(Collectors.joining(", "));
        logger
                .info("Registered {} tools for access level {} and toolsets {}", specifications.size(), level.label(),
                        toolsetLabels);
        return specifications;
    }
}
