package com.otilm.mcp.tool;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

class PlainTextResultTest {

    @Test
    void returnsTextUnchanged() {
        assertThat(new PlainTextResult().convert("Line 1\n\"quoted\"", String.class)).isEqualTo("Line 1\n\"quoted\"");
    }

    @Test
    void everyToolReturnsPlainText() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
        List<Tool> tools = scanner
                .findCandidateComponents("com.otilm.mcp.tool")
                .stream()
                .map(candidate -> ClassUtils.resolveClassName(candidate.getBeanClassName(), null))
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .map(method -> method.getAnnotation(Tool.class))
                .filter(Objects::nonNull)
                .toList();

        assertThat(tools)
                .hasSize(35)
                .allSatisfy(
                        tool -> assertThat(tool.resultConverter()).as(tool.name()).isEqualTo(PlainTextResult.class));
    }
}
