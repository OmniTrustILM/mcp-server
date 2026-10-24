package com.otilm.mcp.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurationSupportTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void metadataUrlsDropEmbeddedCredentialsQueriesAndFragments() {
        String output = ConfigurationSupport
                .describe("Connector",
                        JSON
                                .readTree(
                                        """
                                                {"url":"https://operator:private-sentinel@example.test:8443/path?token=private-sentinel#private-sentinel",
                                                 "invalidUrl":"private-sentinel"}
                                                """),
                        "url", "invalidUrl");
        assertThat(output).contains("https://example.test:8443/path").doesNotContain("private-sentinel", "operator");
    }

    @Test
    void malformedInputDoesNotRevealItsContents() {
        assertThatThrownBy(() -> ConfigurationSupport.object("{\"password\":\"private-sentinel\"", "configuration"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON object")
                .hasMessageNotContaining("private-sentinel")
                .hasNoCause();
        assertThatThrownBy(() -> ConfigurationSupport.object("[]", "configuration"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesRequiredAndCanonicalUuidInputsWithoutEchoingInvalidValues() {
        assertThat(ConfigurationSupport.required(" example ", "name")).isEqualTo("example");
        assertThat(ConfigurationSupport.uuid("00000000-0000-4000-8000-000000000001", "uuid"))
                .isEqualTo("00000000-0000-4000-8000-000000000001");
        assertThatThrownBy(() -> ConfigurationSupport.required(" ", "name")).hasMessageContaining("name");
        assertThatThrownBy(() -> ConfigurationSupport.uuid("private-sentinel", "uuid"))
                .hasMessageContaining("UUID")
                .hasMessageNotContaining("private-sentinel");
    }

    @Test
    void descriptionsUseOnlyChosenSafeMetadata() {
        String result = ConfigurationSupport.describe("Credential", JSON.readTree("""
                {"uuid":"00000000-0000-4000-8000-000000000001","name":"Example","enabled":true,
                 "authAttributes":[{"data":"private-sentinel"}],"keyData":"private-sentinel",
                 "details":{"state":"ready","password":"private-sentinel"}}
                """), "uuid", "name", "enabled", "authAttributes", "keyData", "details");
        assertThat(result)
                .contains("Example", "ready", "true", "00000000-0000-4000-8000-000000000001")
                .doesNotContain("private-sentinel", "authAttributes", "keyData", "password", "{\"");
    }

    @Test
    void listsExplainPaginatedItemsAndDoNotRevealNestedSecrets() {
        String result = ConfigurationSupport.list("Users", JSON.readTree("""
                {"totalItems":2,"pageNumber":1,"totalPages":1,
                 "users":[{"name":"One","uuid":"first","secret":"private-sentinel"},
                          {"name":"Two","uuid":"second"}]}
                """), "name", "uuid", "secret");
        assertThat(result).contains("One", "Two", "first", "second", "2").doesNotContain("private-sentinel");
    }

    @Test
    void runSuppressesUnexpectedExceptionTextButKeepsSafeInputErrors() {
        assertThat(ConfigurationSupport.run("testing", () -> {
            throw new RuntimeException("private-sentinel");
        })).contains("testing").doesNotContain("private-sentinel");
        assertThat(ConfigurationSupport.run("testing", () -> {
            throw new IllegalArgumentException("name is required");
        })).isEqualTo("name is required");
    }
}
