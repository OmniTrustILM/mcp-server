package com.otilm.mcp.config;

import com.otilm.mcp.tool.CbomTool;
import com.otilm.mcp.tool.CertificateTool;
import com.otilm.mcp.tool.CryptoAssetTool;
import com.otilm.mcp.tool.InfrastructureTool;
import com.otilm.mcp.tool.KeyTool;
import com.otilm.mcp.tool.SearchTool;
import com.otilm.mcp.tool.SecretTool;
import com.otilm.mcp.tool.SigningTool;
import com.otilm.mcp.tool.VaultTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolConfiguration {

    @Bean
    public ToolCallbackProvider toolCallbackProvider(CertificateTool certificateTool, KeyTool keyTool,
            InfrastructureTool infrastructureTool, SearchTool searchTool, SecretTool secretTool, VaultTool vaultTool,
            CryptoAssetTool cryptoAssetTool, CbomTool cbomTool, SigningTool signingTool) {
        return MethodToolCallbackProvider
                .builder()
                .toolObjects(certificateTool, keyTool, infrastructureTool, searchTool, secretTool, vaultTool,
                        cryptoAssetTool, cbomTool, signingTool)
                .build();
    }
}
