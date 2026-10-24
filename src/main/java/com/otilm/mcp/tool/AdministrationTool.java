package com.otilm.mcp.tool;

import com.otilm.mcp.service.AdministrationService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class AdministrationTool {
    private final AdministrationService service;

    public AdministrationTool(AdministrationService service) {
        this.service = service;
    }

    @McpTool(name = "get_user",
            description = "Get user metadata, certificate identifiers, groups and roles. Custom attribute and certificate content is omitted.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getUser(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.getUser(uuid);
    }

    @McpTool(name = "create_user",
            description = "Create an ILM user with supported certificate identity, groups and custom attributes. Returns metadata only.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createUser(@McpToolParam(
            description = "JSON object: username (required for creation), enabled (creation only), description, firstName, lastName, email, groupUuids, certificateUuid or certificateData. customAttributes and certificateCustomAttributes are plain attribute-name to value objects.") String configuration) {
        return service.createUser(configuration);
    }

    @McpTool(name = "update_user",
            description = "Update user metadata, certificate identity, groups or custom values. Omitted readable fields and groups are retained; populated protected attributes require fresh values before a replacement.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateUser(@McpToolParam(description = "Canonical object UUID") String uuid, @McpToolParam(
            description = "JSON object: username (required for creation), enabled (creation only), description, firstName, lastName, email, groupUuids, certificateUuid or certificateData. customAttributes and certificateCustomAttributes are plain attribute-name to value objects.") String configuration) {
        return service.updateUser(uuid, configuration);
    }

    @McpTool(name = "enable_user", description = "Enable an ILM user.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String enableUser(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.setUserEnabled(uuid, true);
    }

    @McpTool(name = "disable_user", description = "Disable an ILM user.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String disableUser(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.setUserEnabled(uuid, false);
    }

    @McpTool(name = "delete_user",
            description = "Delete an ILM user. Platform authorization and dependency checks apply.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteUser(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.deleteUser(uuid);
    }

    @McpTool(name = "get_user_roles", description = "List roles assigned to an ILM user.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getUserRoles(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.getUserRoles(uuid);
    }

    @McpTool(name = "set_user_role",
            description = "Add or remove one user-role assignment using the targeted endpoint; unrelated assignments are retained.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String setUserRole(@McpToolParam(description = "User UUID") String userUuid,
            @McpToolParam(description = "Role UUID") String roleUuid,
            @McpToolParam(description = "True to add; false to remove") boolean member) {
        return service.setUserRole(userUuid, roleUuid, member);
    }

    @McpTool(name = "set_user_roles",
            description = "Replace the complete role list for a user. Requires explicit replace=true; an empty list removes all assignments.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String setUserRoles(@McpToolParam(description = "Canonical object UUID") String uuid,
            @McpToolParam(description = "JSON array of role UUIDs") String roleUuids,
            @McpToolParam(description = "Must be true to explicitly replace the entire list") boolean replace) {
        return service.setUserRoles(uuid, roleUuids, replace);
    }

    @McpTool(name = "get_user_permissions", description = "Inspect effective user resource and object permissions.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getUserPermissions(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.getUserPermissions(uuid);
    }

    @McpTool(name = "get_group",
            description = "Get group metadata. Use existing list_groups and create_group for listing and creation.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getGroup(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.getGroup(uuid);
    }

    @McpTool(name = "update_group",
            description = "Update group metadata and schema-validated custom attributes while retaining omitted values.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateGroup(@McpToolParam(description = "Canonical object UUID") String uuid, @McpToolParam(
            description = "JSON object with description, email and customAttributes (plain attribute-name to value object). Omitted fields retain existing values; group names cannot change. Protected custom values require fresh input.") String configuration) {
        return service.updateGroup(uuid, configuration);
    }

    @McpTool(name = "delete_group", description = "Delete one group; platform dependency checks apply.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteGroup(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.deleteGroup(uuid);
    }

    @McpTool(name = "get_group_users", description = "List users belonging to one group.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getGroupUsers(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.getGroupUsers(uuid);
    }

    @McpTool(name = "set_group_membership",
            description = "Add or remove one user from a group. Retains other groups and user metadata; populated protected user custom attributes require fresh values in customAttributes.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String setGroupMembership(@McpToolParam(description = "Group UUID") String groupUuid,
            @McpToolParam(description = "User UUID") String userUuid,
            @McpToolParam(description = "True to add; false to remove") boolean member,
            @McpToolParam(
                    description = "Optional plain attribute-name to fresh custom value JSON object; required for protected user attributes",
                    required = false) String customAttributes) {
        return service.setGroupMembership(groupUuid, userUuid, member, customAttributes);
    }

    @McpTool(name = "get_role",
            description = "Get role metadata and system-role status. Existing list_roles lists all roles.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getRole(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.getRole(uuid);
    }

    @McpTool(name = "create_role", description = "Create an ILM role with metadata and schema-validated custom values.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createRole(@McpToolParam(
            description = "JSON object with name, description, email and customAttributes (plain attribute-name to value object). Omitted fields retain existing values; protected custom values require fresh input.") String configuration) {
        return service.createRole(configuration);
    }

    @McpTool(name = "update_role",
            description = "Update role metadata and custom values, preserving omitted readable fields and requiring fresh protected values.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateRole(@McpToolParam(description = "Canonical object UUID") String uuid, @McpToolParam(
            description = "JSON object with name, description, email and customAttributes (plain attribute-name to value object). Omitted fields retain existing values; protected custom values require fresh input.") String configuration) {
        return service.updateRole(uuid, configuration);
    }

    @McpTool(name = "delete_role",
            description = "Delete an ILM role. Platform authorization and dependency checks apply.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteRole(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.deleteRole(uuid);
    }

    @McpTool(name = "get_role_users", description = "List users assigned to a role.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getRoleUsers(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.getRoleUsers(uuid);
    }

    @McpTool(name = "set_role_users",
            description = "Replace the complete list of users assigned to a role. Requires replace=true; use set_user_role for a targeted membership change.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String setRoleUsers(@McpToolParam(description = "Canonical object UUID") String uuid,
            @McpToolParam(description = "JSON array of user UUIDs") String userUuids,
            @McpToolParam(description = "Must be true to explicitly replace the entire list") boolean replace) {
        return service.setRoleUsers(uuid, userUuids, replace);
    }

    @McpTool(name = "get_role_permissions",
            description = "Inspect role resource grants and per-object allow and deny actions.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getRolePermissions(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.getRolePermissions(uuid);
    }

    @McpTool(name = "get_role_resource_permissions",
            description = "Inspect one role resource grant and its object permissions.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getRoleResourcePermissions(@McpToolParam(description = "Role UUID") String roleUuid,
            @McpToolParam(description = "Resource UUID from list_permission_resources") String resourceUuid) {
        return service.getRoleResourcePermissions(roleUuid, resourceUuid);
    }

    @McpTool(name = "get_role_object_permissions",
            description = "List object permission grants for one resource of a role.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getRoleObjectPermissions(@McpToolParam(description = "Role UUID") String roleUuid,
            @McpToolParam(description = "Resource UUID from list_permission_resources") String resourceUuid) {
        return service.getRoleObjectPermissions(roleUuid, resourceUuid);
    }

    @McpTool(name = "list_permission_resources",
            description = "List permission resources, UUIDs, supported actions and object-access capability available on the connected ILM.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listPermissionResources() {
        return service.listPermissionResources();
    }

    @McpTool(name = "list_permission_resource_objects",
            description = "List available objects for a permission resource supporting object access.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listPermissionResourceObjects(
            @McpToolParam(description = "Resource UUID from list_permission_resources") String resourceUuid) {
        return service.listPermissionResourceObjects(resourceUuid);
    }

    @McpTool(name = "set_role_resource_grant",
            description = "Replace the actions of one role resource grant while retaining other resources and existing object grants. Refuses system roles and unavailable actions.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String setRoleResourceGrant(@McpToolParam(description = "Role UUID") String roleUuid,
            @McpToolParam(description = "Resource UUID from list_permission_resources") String resourceUuid,
            @McpToolParam(
                    description = "JSON object with allowAllActions boolean and the complete actions string array for this resource.") String configuration) {
        return service.setRoleResourceGrant(roleUuid, resourceUuid, configuration);
    }

    @McpTool(name = "replace_role_permissions",
            description = "Explicitly replace the complete permission composition of a role, including allowAllResources and every resource/object grant. Refuses system roles and unavailable actions.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String replaceRolePermissions(@McpToolParam(description = "Role UUID") String roleUuid, @McpToolParam(
            description = "JSON object: allowAllResources boolean, resources array of {name,allowAllActions,actions,objects}; objects contain uuid, optional name, allow and deny action arrays.") String configuration,
            @McpToolParam(description = "Must be true to explicitly replace the entire list") boolean replace) {
        return service.replaceRolePermissions(roleUuid, configuration, replace);
    }

    @McpTool(name = "add_role_object_permission",
            description = "Add one object allow/deny grant to a role resource. Does not replace unrelated grants.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String addRoleObjectPermission(@McpToolParam(description = "Role UUID") String roleUuid,
            @McpToolParam(description = "Resource UUID from list_permission_resources") String resourceUuid,
            @McpToolParam(description = "Target object UUID") String objectUuid, @McpToolParam(
                    description = "JSON object with complete allow and deny action arrays, optional name.") String configuration) {
        return service.setRoleObjectGrant(roleUuid, resourceUuid, objectUuid, configuration, true);
    }

    @McpTool(name = "update_role_object_permission",
            description = "Replace one object allow/deny grant on a role resource. Does not replace unrelated grants.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateRoleObjectPermission(@McpToolParam(description = "Role UUID") String roleUuid,
            @McpToolParam(description = "Resource UUID from list_permission_resources") String resourceUuid,
            @McpToolParam(description = "Target object UUID") String objectUuid, @McpToolParam(
                    description = "JSON object with complete allow and deny action arrays, optional name.") String configuration) {
        return service.setRoleObjectGrant(roleUuid, resourceUuid, objectUuid, configuration, false);
    }

    @McpTool(name = "remove_role_object_permission",
            description = "Remove one object permission grant from a role resource.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String removeRoleObjectGrant(@McpToolParam(description = "Role UUID") String roleUuid,
            @McpToolParam(description = "Resource UUID from list_permission_resources") String resourceUuid,
            @McpToolParam(description = "Target object UUID") String objectUuid) {
        return service.removeRoleObjectGrant(roleUuid, resourceUuid, objectUuid);
    }
}
