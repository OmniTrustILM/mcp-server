package com.otilm.mcp.tool;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;

import static org.assertj.core.api.Assertions.assertThat;

class ToolSpecificationsTest {

    @Test
    void readOnlyKeepsOnlyReads() {
        assertThat(allowedNames(AccessLevel.READ_ONLY)).containsExactly("read_sample");
    }

    @Test
    void readWriteAddsWrites() {
        assertThat(allowedNames(AccessLevel.READ_WRITE)).containsExactlyInAnyOrder("read_sample", "write_sample");
    }

    @Test
    void fullAddsDestructiveAndUndeclaredTools() {
        assertThat(allowedNames(AccessLevel.FULL))
                .containsExactlyInAnyOrder("read_sample", "write_sample", "destroy_sample", "undeclared_sample");
    }

    @Test
    void sensitiveReadNeedsFullAccessWhileRetainingItsReadHint() {
        for (AccessLevel level : List.of(AccessLevel.READ_ONLY, AccessLevel.READ_WRITE)) {
            assertThat(ToolSpecifications.allowed(level, List.of(new MaterialTools()))).isEmpty();
        }
        assertThat(ToolSpecifications.allowed(AccessLevel.FULL, List.of(new MaterialTools())))
                .singleElement()
                .satisfies(specification -> {
                    assertThat(specification.tool().name()).isEqualTo("read_material");
                    assertThat(specification.tool().annotations().readOnlyHint()).isTrue();
                });
    }

    static class MaterialTools {

        @SensitiveOutput
        @McpTool(name = "read_material", description = "Returns explicitly requested material at full access.",
                annotations = @McpTool.McpAnnotations(readOnlyHint = true))
        public String readMaterial() {
            return "material";
        }
    }

    @Test
    void sensitiveReadWithAnImplicitToolNameAlsoNeedsFullAccess() {
        assertThat(ToolSpecifications.allowed(AccessLevel.READ_ONLY, List.of(new ImplicitMaterialTools()))).isEmpty();
        assertThat(ToolSpecifications.allowed(AccessLevel.FULL, List.of(new ImplicitMaterialTools())))
                .singleElement()
                .extracting(specification -> specification.tool().name())
                .isEqualTo("material");
    }

    static class ImplicitMaterialTools {

        @SensitiveOutput
        @McpTool(description = "An explicitly requested material read.",
                annotations = @McpTool.McpAnnotations(readOnlyHint = true))
        public String material() {
            return "material";
        }
    }

    private static List<String> allowedNames(AccessLevel level) {
        return ToolSpecifications
                .allowed(level, List.of(new SampleTools()))
                .stream()
                .map(specification -> specification.tool().name())
                .toList();
    }

    static class SampleTools {

        @McpTool(name = "read_sample", description = "Reads.",
                annotations = @McpTool.McpAnnotations(readOnlyHint = true))
        public String read() {
            return "read";
        }

        @McpTool(name = "write_sample", description = "Changes.",
                annotations = @McpTool.McpAnnotations(destructiveHint = false))
        public String write() {
            return "write";
        }

        @McpTool(name = "destroy_sample", description = "Deletes.",
                annotations = @McpTool.McpAnnotations(destructiveHint = true))
        public String destroy() {
            return "destroy";
        }

        @McpTool(name = "undeclared_sample", description = "States no hints.")
        public String undeclared() {
            return "undeclared";
        }
    }
}
