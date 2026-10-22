package com.otilm.mcp.service;

import com.otilm.api.model.core.auth.RoleDto;
import com.otilm.api.model.core.auth.UserDto;
import com.otilm.mcp.client.IlmApiClient;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

import static com.otilm.mcp.service.TextFormat.field;
import static com.otilm.mcp.service.TextFormat.item;
import static com.otilm.mcp.service.TextFormat.titledList;

@Service
public class UserServiceImpl implements UserService {

    private static final String INDENT = "  ";

    private final IlmApiClient ilmApiClient;

    public UserServiceImpl(IlmApiClient ilmApiClient) {
        this.ilmApiClient = ilmApiClient;
    }

    @Override
    public String listUsers() {
        return ToolResponses.run("listing users", () -> {
            List<UserDto> users = ilmApiClient.listUsers();
            return titledList("Users (" + users.size() + ")", users, "No users found.", (sb, user) -> {
                item(sb, user.getUsername(), user.getUuid());
                field(sb, INDENT, "Name",
                        Stream
                                .of(user.getFirstName(), user.getLastName())
                                .filter(Objects::nonNull)
                                .collect(Collectors.joining(" ")));
                field(sb, INDENT, "Email", user.getEmail());
                field(sb, INDENT, "Enabled", user.getEnabled());
                field(sb, INDENT, "System User", Boolean.TRUE.equals(user.getSystemUser()) ? Boolean.TRUE : null);
            });
        });
    }

    @Override
    public String listRoles() {
        return ToolResponses.run("listing roles", () -> {
            List<RoleDto> roles = ilmApiClient.listRoles();
            return titledList("Roles (" + roles.size() + ")", roles, "No roles found.", (sb, role) -> {
                item(sb, role.getName(), role.getUuid());
                field(sb, INDENT, "Description", role.getDescription());
                field(sb, INDENT, "System Role", Boolean.TRUE.equals(role.getSystemRole()) ? Boolean.TRUE : null);
            });
        });
    }
}
