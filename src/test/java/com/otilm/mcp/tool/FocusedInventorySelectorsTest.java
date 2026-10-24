package com.otilm.mcp.tool;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.mcp.annotation.McpTool;

import static org.assertj.core.api.Assertions.assertThat;

class FocusedInventorySelectorsTest {

    @ParameterizedTest
    @CsvSource({
            "CONNECTORS,list_connectors",
            "AUTHORITIES,list_authorities",
            "RA_PROFILES,list_ra_profiles",
            "TOKENS,list_token_instances",
            "ENTITIES,list_entities",
            "DISCOVERY,list_discoveries",
            "USERS,list_groups",
            "USERS,create_group"})
    void focusedSelectorsIncludeTheirExistingInventory(Toolset selector, String tool) {
        assertThat(names(selector)).contains(tool);
    }

    @Test
    void notificationAndDiscoveryMaintenanceStayInTheirOwnSelectors() {
        assertThat(names(Toolset.NOTIFICATIONS))
                .contains("get_notification_instance", "update_notification_instance", "update_notification_profile")
                .doesNotContain("update_trigger", "attach_scoped_trigger");
        assertThat(names(Toolset.DISCOVERY))
                .contains("list_discoveries", "start_discovery", "attach_scoped_trigger")
                .doesNotContain("get_notification_instance", "update_notification_instance",
                        "update_notification_profile");
    }

    @Test
    void aggregateAndOverlappingSelectorsExposeEachPublicNameOnce() {
        assertThat(names(Toolset.values())).doesNotHaveDuplicates();
        assertThat(names(Toolset.INFRASTRUCTURE, Toolset.CONNECTORS, Toolset.AUTHORITIES, Toolset.RA_PROFILES,
                Toolset.TOKENS, Toolset.ENTITIES, Toolset.DISCOVERY, Toolset.USERS, Toolset.NOTIFICATIONS,
                Toolset.WORKFLOWS)).doesNotHaveDuplicates();
        assertThat(names(Toolset.INFRASTRUCTURE))
                .contains("list_connectors", "list_authorities", "list_ra_profiles", "list_token_instances",
                        "list_entities", "list_discoveries", "list_groups", "create_group");
    }

    @Test
    void legacyInfrastructureJavaHelpersRemainWithoutMcpRegistration() throws Exception {
        assertThat(InfrastructureTool.class.getMethod("listAuthorities")).isNotNull();
        assertThat(InfrastructureTool.class.getMethod("listEntities", String.class)).isNotNull();
        assertThat(InfrastructureTool.class.getMethod("createGroup", String.class, String.class, String.class))
                .isNotNull();
        assertThat(Arrays
                .stream(InfrastructureTool.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(McpTool.class))).isEmpty();
    }

    private static List<String> names(Toolset... selectors) {
        return Stream
                .of(selectors)
                .flatMap(selector -> selector.toolClasses().stream())
                .distinct()
                .flatMap(type -> Arrays.stream(type.getMethods()))
                .map(method -> method.getAnnotation(McpTool.class))
                .filter(Objects::nonNull)
                .map(McpTool::name)
                .toList();
    }
}
