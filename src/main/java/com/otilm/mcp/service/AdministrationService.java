package com.otilm.mcp.service;

public interface AdministrationService {
    String getUser(String uuid);

    String createUser(String configuration);

    String updateUser(String uuid, String configuration);

    String setUserEnabled(String uuid, boolean enabled);

    String deleteUser(String uuid);

    String getUserRoles(String uuid);

    String setUserRole(String userUuid, String roleUuid, boolean member);

    String setUserRoles(String uuid, String roleUuids, boolean replace);

    String getUserPermissions(String uuid);

    String getGroup(String uuid);

    String updateGroup(String uuid, String configuration);

    String deleteGroup(String uuid);

    String getGroupUsers(String uuid);

    String setGroupMembership(String groupUuid, String userUuid, boolean member);

    String setGroupMembership(String groupUuid, String userUuid, boolean member, String customAttributes);

    String getRole(String uuid);

    String createRole(String configuration);

    String updateRole(String uuid, String configuration);

    String deleteRole(String uuid);

    String getRoleUsers(String uuid);

    String setRoleUsers(String uuid, String userUuids, boolean replace);

    String getRolePermissions(String uuid);

    String getRoleResourcePermissions(String roleUuid, String resourceUuid);

    String getRoleObjectPermissions(String roleUuid, String resourceUuid);

    String listPermissionResources();

    String listPermissionResourceObjects(String resourceUuid);

    String setRoleResourceGrant(String roleUuid, String resourceUuid, String configuration);

    String replaceRolePermissions(String roleUuid, String configuration, boolean replace);

    String setRoleObjectGrant(String roleUuid, String resourceUuid, String objectUuid, String configuration,
            boolean create);

    String removeRoleObjectGrant(String roleUuid, String resourceUuid, String objectUuid);
}
