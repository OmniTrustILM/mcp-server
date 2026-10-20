package com.otilm.mcp;

import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.service.CertificateService;
import com.otilm.mcp.service.InfrastructureService;
import com.otilm.mcp.service.KeyService;
import com.otilm.mcp.service.SearchService;
import com.otilm.mcp.service.SecretService;
import com.otilm.mcp.service.VaultService;
import com.otilm.mcp.tool.CertificateTool;
import com.otilm.mcp.tool.InfrastructureTool;
import com.otilm.mcp.tool.KeyTool;
import com.otilm.mcp.tool.SearchTool;
import com.otilm.mcp.tool.SecretTool;
import com.otilm.mcp.tool.VaultTool;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class McpServerIntegrationTest {

    @Autowired
    IlmApiClient ilmApiClient;

    @Autowired
    CertificateService certificateService;

    @Autowired
    KeyService keyService;

    @Autowired
    InfrastructureService infrastructureService;

    @Autowired
    SearchService searchService;

    @Autowired
    SecretService secretService;

    @Autowired
    VaultService vaultService;

    @Autowired
    CertificateTool certificateTool;

    @Autowired
    KeyTool keyTool;

    @Autowired
    InfrastructureTool infrastructureTool;

    @Autowired
    SearchTool searchTool;

    @Autowired
    SecretTool secretTool;

    @Autowired
    VaultTool vaultTool;

    @Test
    void contextLoads() {
        assertThat(ilmApiClient).isNotNull();
        assertThat(certificateService).isNotNull();
        assertThat(keyService).isNotNull();
        assertThat(infrastructureService).isNotNull();
        assertThat(searchService).isNotNull();
        assertThat(secretService).isNotNull();
        assertThat(vaultService).isNotNull();
        assertThat(certificateTool).isNotNull();
        assertThat(keyTool).isNotNull();
        assertThat(infrastructureTool).isNotNull();
        assertThat(searchTool).isNotNull();
        assertThat(secretTool).isNotNull();
        assertThat(vaultTool).isNotNull();
    }

}
