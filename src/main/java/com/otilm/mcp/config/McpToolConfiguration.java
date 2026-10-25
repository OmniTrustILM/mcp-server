package com.otilm.mcp.config;

import com.otilm.mcp.tool.SearchTool;
import com.otilm.mcp.tool.ToolSpecifications;
import com.otilm.mcp.tool.Toolset;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
    @ConditionalOnProperty(name = "ilm.mcp.security.enabled", havingValue = "false", matchIfMissing = true)
    public List<McpServerFeatures.SyncToolSpecification> toolSpecifications(McpAccessProperties access,
            IlmApiProperties ilmApi, McpServerProperties mcpServer, SearchTool searchTool, BeanFactory beanFactory) {
        List<McpServerFeatures.SyncToolSpecification> specifications = ToolSpecifications
                .allowed(access.accessLevel(), toolObjects(access, ilmApi, mcpServer, searchTool, beanFactory));
        logRegistration(access, specifications.size());
        return specifications;
    }

    @Bean
    @ConditionalOnProperty(name = "ilm.mcp.security.enabled", havingValue = "true")
    public List<McpStatelessServerFeatures.SyncToolSpecification> statelessToolSpecifications(
            McpAccessProperties access, IlmApiProperties ilmApi, McpServerProperties mcpServer, SearchTool searchTool,
            BeanFactory beanFactory) {
        List<McpStatelessServerFeatures.SyncToolSpecification> specifications = ToolSpecifications
                .allowedStateless(access.accessLevel(),
                        toolObjects(access, ilmApi, mcpServer, searchTool, beanFactory));
        logRegistration(access, specifications.size());
        return specifications;
    }

    private List<Object> toolObjects(McpAccessProperties access, IlmApiProperties ilmApi, McpServerProperties mcpServer,
            SearchTool searchTool, BeanFactory beanFactory) {
        AccessChecks.verify(access.accessLevel(), ilmApi.getAuth().getMethod(), mcpServer);
        return Stream
                .concat(Stream.of(searchTool),
                        access
                                .enabledToolsets()
                                .stream()
                                .flatMap(toolset -> toolset.toolClasses().stream())
                                .distinct()
                                .map(beanFactory::getBean))
                .toList();
    }

    private void logRegistration(McpAccessProperties access, int count) {
        String labels = access.enabledToolsets().stream().map(Toolset::label).collect(Collectors.joining(", "));
        logger
                .info("Registered {} tools for access level {} and toolsets {}", count, access.accessLevel().label(),
                        labels);
    }
}
