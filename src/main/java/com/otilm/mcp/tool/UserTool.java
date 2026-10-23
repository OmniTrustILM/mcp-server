package com.otilm.mcp.tool;

import com.otilm.mcp.service.UserService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

@Component
public class UserTool {

    private final UserService userService;

    public UserTool(UserService userService) {
        this.userService = userService;
    }

    @McpTool(name = "list_users",
            description = "List ILM users with username, name, email and UUID, to find the owner or recipient a workflow should use.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listUsers() {
        return userService.listUsers();
    }

    @McpTool(name = "list_roles",
            description = "List ILM roles with name, UUID and description, to find the recipients a notification should reach.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listRoles() {
        return userService.listRoles();
    }
}
