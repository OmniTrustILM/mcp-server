package com.otilm.mcp.service;

import com.otilm.mcp.client.ConfigurationApiClient;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static com.otilm.mcp.service.AdministrationInput.JSON;
import static com.otilm.mcp.service.AdministrationInput.allow;
import static com.otilm.mcp.service.AdministrationInput.bool;
import static com.otilm.mcp.service.AdministrationInput.configuration;
import static com.otilm.mcp.service.AdministrationInput.id;
import static com.otilm.mcp.service.AdministrationInput.replaceIntent;
import static com.otilm.mcp.service.AdministrationInput.select;
import static com.otilm.mcp.service.AdministrationInput.strings;
import static com.otilm.mcp.service.AdministrationInput.text;
import static com.otilm.mcp.service.AdministrationInput.uuids;
import static com.otilm.mcp.service.ConfigurationSupport.describe;
import static com.otilm.mcp.service.ConfigurationSupport.list;
import static com.otilm.mcp.service.ConfigurationSupport.object;
import static com.otilm.mcp.service.ConfigurationSupport.required;
import static com.otilm.mcp.service.ConfigurationSupport.run;

@Service
public class AdministrationServiceImpl implements AdministrationService {
    private static final String OBJECTS_ENDPOINT = "/objects";
    private static final String USERS_ENDPOINT = "/users";
    private static final String USER_2 = "User ";
    private static final String ACTIONS = "actions";
    private static final String ALLOW = "allow";
    private static final String ALLOW_ALL_ACTIONS = "allowAllActions";
    private static final String ALLOW_ALL_RESOURCES = "allowAllResources";
    private static final String CERTIFICATE_CUSTOM_ATTRIBUTES = "certificateCustomAttributes";
    private static final String CERTIFICATE_DATA = "certificateData";
    private static final String CERTIFICATE_UUID = "certificateUuid";
    private static final String CUSTOM_ATTRIBUTES = "customAttributes";
    private static final String DESCRIPTION = "description";
    private static final String EMAIL = "email";
    private static final String ENABLED = "enabled";
    private static final String FIRST_NAME = "firstName";
    private static final String GROUP_UUIDS = "groupUuids";
    private static final String GROUPS = "groups";
    private static final String LAST_NAME = "lastName";
    private static final String OBJECTS = "objects";
    private static final String RESOURCES = "resources";
    private static final String ROLES_2 = "roles";
    private static final String USERNAME = "username";

    private static final String USERS = "/v1/users";
    private static final String USER = USERS + "/{uuid}";
    private static final String GROUP = "/v1/groups/{uuid}";
    private static final String ROLES = "/v1/roles";
    private static final String ROLE = ROLES + "/{uuid}";
    private static final String PERMISSIONS = ROLE + "/permissions";
    private static final String RESOURCE = PERMISSIONS + "/{resource}";
    private static final String OBJECT = RESOURCE + "/objects/{object}";
    private static final String CATALOG = "/v1/auth/resources";
    private static final String[] USER_FIELDS = {
            "uuid",
            USERNAME,
            FIRST_NAME,
            LAST_NAME,
            DESCRIPTION,
            EMAIL,
            ENABLED,
            "systemUser"};
    private static final String[] NAMED_FIELDS = {"uuid", "name", DESCRIPTION, EMAIL, "systemRole"};
    private final ConfigurationApiClient client;

    public AdministrationServiceImpl(ConfigurationApiClient client) {
        this.client = client;
    }

    @Override
    public String getUser(String uuid) {
        return run("reading user", () -> {
            JsonNode user = client.get(USER, id(uuid));
            String details = describe("User", user, USER_FIELDS) + list("Groups", user.path(GROUPS), "uuid", "name")
                    + list("Roles", user.path(ROLES_2), "uuid", "name");
            JsonNode certificate = user.path("certificate");
            return certificate.isObject()
                    ? details + describe("User certificate", certificate, "uuid", "fingerprint")
                    : details;
        });
    }

    @Override
    public String createUser(String configuration) {
        return run("creating user", () -> {
            ObjectNode changes = userInput(configuration, true);
            required(changes.path(USERNAME).asString(null), USERNAME);
            return describe("Created user", client.post(USERS, userBody(changes, JSON.createObjectNode())),
                    USER_FIELDS);
        });
    }

    @Override
    public String updateUser(String uuid, String configuration) {
        return run("updating user", () -> {
            id(uuid);
            ObjectNode changes = userInput(configuration, false);
            ObjectNode body = userBody(changes, client.get(USER, uuid));
            return "Updated user " + uuid + ".\n" + describe("User", client.put(USER, body, uuid), USER_FIELDS);
        });
    }

    @Override
    public String setUserEnabled(String uuid, boolean enabled) {
        return run("changing user enabled state", () -> USER_2 + id(uuid) + ".\n"
                + describe("User", client.patch(USER + (enabled ? "/enable" : "/disable"), null, uuid), USER_FIELDS));
    }

    @Override
    public String deleteUser(String uuid) {
        return run("deleting user", () -> {
            client.delete(USER, id(uuid));
            return "Deleted user " + uuid + ".";
        });
    }

    @Override
    public String getUserRoles(String uuid) {
        return run("reading user roles", () -> list("User roles", client.get(USER + "/roles", id(uuid)), NAMED_FIELDS));
    }

    @Override
    public String setUserRole(String userUuid, String roleUuid, boolean member) {
        return run("changing user role membership", () -> {
            id(userUuid);
            id(roleUuid);
            String path = USER + "/roles/{role}";
            if (member) {
                client.put(path, null, userUuid, roleUuid);
            } else {
                client.delete(path, userUuid, roleUuid);
            }
            return USER_2 + userUuid + (member ? " added to" : " removed from") + " role " + roleUuid + ".";
        });
    }

    @Override
    public String setUserRoles(String uuid, String roleUuids, boolean replace) {
        return run("replacing user roles", () -> {
            id(uuid);
            replaceIntent(replace);
            ArrayNode roles = uuids(roleUuids);
            client.patch(USER + "/roles", roles, uuid);
            return "Replaced all role assignments for user " + uuid + ".";
        });
    }

    @Override
    public String getUserPermissions(String uuid) {
        return run("reading user permissions", () -> permissionText(client.get(USER + "/permissions", id(uuid))));
    }

    @Override
    public String getGroup(String uuid) {
        return namedRead("Group", GROUP, uuid);
    }

    @Override
    public String updateGroup(String uuid, String configuration) {
        return updateNamed("group", GROUPS, GROUP, uuid, configuration);
    }

    @Override
    public String deleteGroup(String uuid) {
        return remove("group", GROUP, uuid);
    }

    @Override
    public String getGroupUsers(String uuid) {
        return run("reading group users",
                () -> list("Group users", client.get(GROUP + USERS_ENDPOINT, id(uuid)), "name", "uuid"));
    }

    @Override
    public String setGroupMembership(String groupUuid, String userUuid, boolean member) {
        return setGroupMembership(groupUuid, userUuid, member, null);
    }

    @Override
    public String setGroupMembership(String groupUuid, String userUuid, boolean member, String customAttributes) {
        return run("changing group membership", () -> {
            id(groupUuid);
            id(userUuid);
            JsonNode current = client.get(USER, userUuid);
            ArrayNode groups = JSON.createArrayNode();
            for (JsonNode group : current.path(GROUPS)) {
                if (!groupUuid.equals(group.path("uuid").asString())) {
                    groups.add(id(group.path("uuid").asString(null)));
                }
            }
            if (member) {
                groups.add(groupUuid);
            }
            ObjectNode changes = JSON.createObjectNode();
            changes.set(GROUP_UUIDS, groups);
            changes.set(CUSTOM_ATTRIBUTES, object(customAttributes, "Custom attributes"));
            client.put(USER, userBody(changes, current), userUuid);
            return USER_2 + userUuid + (member ? " added to" : " removed from") + " group " + groupUuid + ".";
        });
    }

    @Override
    public String getRole(String uuid) {
        return namedRead("Role", ROLE, uuid);
    }

    @Override
    public String createRole(String configuration) {
        return run("creating role", () -> {
            ObjectNode body = namedBody(ROLES_2, namedInput(configuration), JSON.createObjectNode());
            return describe("Created role", client.post(ROLES, body), NAMED_FIELDS);
        });
    }

    @Override
    public String updateRole(String uuid, String configuration) {
        return updateNamed("role", ROLES_2, ROLE, uuid, configuration);
    }

    @Override
    public String deleteRole(String uuid) {
        return remove("role", ROLE, uuid);
    }

    @Override
    public String getRoleUsers(String uuid) {
        return run("reading role users",
                () -> list("Role users", client.get(ROLE + USERS_ENDPOINT, id(uuid)), USER_FIELDS));
    }

    @Override
    public String setRoleUsers(String uuid, String userUuids, boolean replace) {
        return run("replacing role users", () -> {
            id(uuid);
            replaceIntent(replace);
            ArrayNode users = uuids(userUuids);
            client.patch(ROLE + USERS_ENDPOINT, users, uuid);
            return "Replaced all user assignments for role " + uuid + ".";
        });
    }

    @Override
    public String getRolePermissions(String uuid) {
        return run("reading role permissions", () -> permissionText(client.get(PERMISSIONS, id(uuid))));
    }

    @Override
    public String getRoleResourcePermissions(String roleUuid, String resourceUuid) {
        return run("reading role resource permissions", () -> {
            id(roleUuid);
            id(resourceUuid);
            return describe("Resource permissions", client.get(RESOURCE, roleUuid, resourceUuid), "name",
                    ALLOW_ALL_ACTIONS, ACTIONS)
                    + list("Object permissions", client.get(RESOURCE + OBJECTS_ENDPOINT, roleUuid, resourceUuid),
                            "uuid", "name", ALLOW, "deny");
        });
    }

    @Override
    public String getRoleObjectPermissions(String roleUuid, String resourceUuid) {
        return run("reading role object permissions", () -> {
            id(roleUuid);
            id(resourceUuid);
            return list("Object permissions", client.get(RESOURCE + OBJECTS_ENDPOINT, roleUuid, resourceUuid), "uuid",
                    "name", ALLOW, "deny");
        });
    }

    @Override
    public String listPermissionResources() {
        return run("listing permission resources", () -> {
            StringBuilder output = new StringBuilder("Permission resources\n");
            for (JsonNode resource : client.get(CATALOG)) {
                output.append(describe("Resource", resource, "uuid", "name", "displayName", "objectAccess"));
                output.append(list("Actions", resource.path(ACTIONS), "uuid", "name", "displayName"));
            }
            return output.toString();
        });
    }

    @Override
    public String listPermissionResourceObjects(String resourceUuid) {
        return run("listing permission resource objects", () -> {
            JsonNode resource = catalogResource(resourceUuid);
            requireObjectAccess(resource);
            return list("Permission resource objects",
                    client.get(CATALOG + "/{resource}/objects", resource.path("name").asString()), "uuid", "name");
        });
    }

    @Override
    public String setRoleResourceGrant(String roleUuid, String resourceUuid, String configuration) {
        return run("changing role resource grant", () -> {
            id(roleUuid);
            id(resourceUuid);
            ObjectNode changes = configuration(configuration, ALLOW_ALL_ACTIONS, ACTIONS);
            bool(changes, ALLOW_ALL_ACTIONS);
            if (!changes.has(ALLOW_ALL_ACTIONS) || !changes.has(ACTIONS)) {
                throw new IllegalArgumentException(
                        "Supply allowAllActions and the complete actions list for this resource.");
            }
            checkMutableRole(roleUuid);
            JsonNode resource = catalogResource(resourceUuid);
            changes.set(ACTIONS, actions(changes.path(ACTIONS), resource));
            JsonNode current = client.get(PERMISSIONS, roleUuid);
            if (current.path(ALLOW_ALL_RESOURCES).asBoolean(false)) {
                throw new IllegalArgumentException(
                        "Role allows all resources; use replace_role_permissions with explicit replacement intent to change that grant.");
            }
            ObjectNode body = permissionBody(current);
            ArrayNode resources = (ArrayNode) body.path(RESOURCES);
            String name = resource.path("name").asString();
            ObjectNode target = null;
            for (JsonNode entry : resources) {
                if (name.equals(entry.path("name").asString())) {
                    target = (ObjectNode) entry;
                }
            }
            if (target == null) {
                target = JSON.createObjectNode();
                target.put("name", name);
                target.set(OBJECTS, JSON.createArrayNode());
                resources.add(target);
            }
            target.set(ALLOW_ALL_ACTIONS, changes.get(ALLOW_ALL_ACTIONS));
            target.set(ACTIONS, changes.get(ACTIONS));
            client.post(PERMISSIONS, body, roleUuid);
            return "Updated resource grant " + resourceUuid + " for role " + roleUuid
                    + "; other resource and object grants retained.";
        });
    }

    @Override
    public String replaceRolePermissions(String roleUuid, String configuration, boolean replace) {
        return run("replacing role permissions", () -> {
            id(roleUuid);
            replaceIntent(replace);
            ObjectNode body = configuration(configuration, ALLOW_ALL_RESOURCES, RESOURCES);
            bool(body, ALLOW_ALL_RESOURCES);
            if (!body.has(ALLOW_ALL_RESOURCES) || !body.path(RESOURCES).isArray()) {
                throw new IllegalArgumentException("Supply allowAllResources and a complete resources array.");
            }
            checkMutableRole(roleUuid);
            JsonNode catalog = client.get(CATALOG);
            Set<String> names = new HashSet<>();
            for (JsonNode resource : body.path(RESOURCES)) {
                validatePermissionResource(resource, catalog, names);
            }
            client.post(PERMISSIONS, body, roleUuid);
            return "Replaced all permissions for role " + roleUuid + ".";
        });
    }

    private static void validatePermissionResource(JsonNode resource, JsonNode catalog, Set<String> names) {
        allow(resource, "name", ALLOW_ALL_ACTIONS, ACTIONS, OBJECTS);
        JsonNode definition = findResource(catalog, "name", resource.path("name").asString(null));
        if (!names.add(resource.path("name").asString())) {
            throw new IllegalArgumentException("Duplicate permission resource.");
        }
        bool(resource, ALLOW_ALL_ACTIONS);
        if (!resource.has(ALLOW_ALL_ACTIONS)) {
            throw new IllegalArgumentException("Each resource requires allowAllActions.");
        }
        actions(resource.path(ACTIONS), definition);
        if (!resource.path(OBJECTS).isArray()) {
            throw new IllegalArgumentException("Each resource requires an objects array.");
        }
        for (JsonNode object : resource.path(OBJECTS)) {
            objectGrant(object, definition, true);
        }
    }

    @Override
    public String setRoleObjectGrant(String roleUuid, String resourceUuid, String objectUuid, String configuration,
            boolean create) {
        return run("changing object permission grant", () -> {
            id(roleUuid);
            id(resourceUuid);
            id(objectUuid);
            ObjectNode body = configuration(configuration, "name", ALLOW, "deny");
            body.put("uuid", objectUuid);
            checkMutableRole(roleUuid);
            objectGrant(body, catalogResource(resourceUuid), true);
            if (create) {
                client.post(RESOURCE + OBJECTS_ENDPOINT, JSON.createArrayNode().add(body), roleUuid, resourceUuid);
            } else {
                client.put(OBJECT, body, roleUuid, resourceUuid, objectUuid);
            }
            return (create ? "Added" : "Updated") + " object " + objectUuid + " permissions for role " + roleUuid
                    + " resource " + resourceUuid + ".";
        });
    }

    @Override
    public String removeRoleObjectGrant(String roleUuid, String resourceUuid, String objectUuid) {
        return run("removing object permission grant", () -> {
            id(roleUuid);
            id(resourceUuid);
            id(objectUuid);
            checkMutableRole(roleUuid);
            client.delete(OBJECT, roleUuid, resourceUuid, objectUuid);
            return "Removed object " + objectUuid + " permissions for role " + roleUuid + " resource " + resourceUuid
                    + ".";
        });
    }

    private ObjectNode userInput(String configuration, boolean create) {
        ObjectNode body = create
                ? configuration(configuration, USERNAME, ENABLED, DESCRIPTION, FIRST_NAME, LAST_NAME, EMAIL,
                        GROUP_UUIDS, CERTIFICATE_UUID, CERTIFICATE_DATA, CUSTOM_ATTRIBUTES,
                        CERTIFICATE_CUSTOM_ATTRIBUTES)
                : configuration(configuration, DESCRIPTION, FIRST_NAME, LAST_NAME, EMAIL, GROUP_UUIDS, CERTIFICATE_UUID,
                        CERTIFICATE_DATA, CUSTOM_ATTRIBUTES, CERTIFICATE_CUSTOM_ATTRIBUTES);
        text(body, USERNAME, DESCRIPTION, FIRST_NAME, LAST_NAME, EMAIL, CERTIFICATE_UUID, CERTIFICATE_DATA);
        bool(body, ENABLED);
        if (body.has(GROUP_UUIDS)) {
            body.set(GROUP_UUIDS, strings(body.path(GROUP_UUIDS), GROUP_UUIDS, true));
        }
        if (body.hasNonNull(CERTIFICATE_UUID)) {
            id(body.path(CERTIFICATE_UUID).asString());
        }
        if (body.hasNonNull(CERTIFICATE_UUID) && body.hasNonNull(CERTIFICATE_DATA)) {
            throw new IllegalArgumentException("Supply either certificateUuid or certificateData.");
        }
        if (body.has(CERTIFICATE_CUSTOM_ATTRIBUTES) && !body.hasNonNull(CERTIFICATE_DATA)) {
            throw new IllegalArgumentException("certificateCustomAttributes requires certificateData.");
        }
        return body;
    }

    private ObjectNode userBody(ObjectNode changes, JsonNode existing) {
        ObjectNode body = select(existing, DESCRIPTION, FIRST_NAME, LAST_NAME, EMAIL);
        if (existing.has(GROUPS)) {
            ArrayNode groups = JSON.createArrayNode();
            for (JsonNode group : existing.path(GROUPS)) {
                groups.add(id(group.path("uuid").asString(null)));
            }
            body.set(GROUP_UUIDS, groups);
        }
        changes.properties().forEach(entry -> {
            if (!Set.of(CUSTOM_ATTRIBUTES, CERTIFICATE_CUSTOM_ATTRIBUTES).contains(entry.getKey())) {
                body.set(entry.getKey(), entry.getValue());
            }
        });
        body.set(CUSTOM_ATTRIBUTES, customAttributes("users", existing, changes));
        if (changes.hasNonNull(CERTIFICATE_DATA)) {
            JsonNode definitions = client.get("/v1/attributes/custom/resources/certificates");
            body
                    .set(CERTIFICATE_CUSTOM_ATTRIBUTES,
                            ConfigurationAttributes
                                    .encode(definitions,
                                            changes.has(CERTIFICATE_CUSTOM_ATTRIBUTES)
                                                    ? changes.path(CERTIFICATE_CUSTOM_ATTRIBUTES).toString()
                                                    : "{}"));
        }
        return body;
    }

    private ObjectNode namedInput(String configuration) {
        ObjectNode result = configuration(configuration, "name", DESCRIPTION, EMAIL, CUSTOM_ATTRIBUTES);
        text(result, "name", DESCRIPTION, EMAIL);
        return result;
    }

    private ObjectNode namedBody(String resource, ObjectNode changes, JsonNode existing) {
        ObjectNode body = select(existing, "name", DESCRIPTION, EMAIL);
        changes.properties().forEach(entry -> {
            if (!CUSTOM_ATTRIBUTES.equals(entry.getKey())) {
                body.set(entry.getKey(), entry.getValue());
            }
        });
        required(body.path("name").asString(null), "name");
        body.set(CUSTOM_ATTRIBUTES, customAttributes(resource, existing, changes));
        return body;
    }

    private ArrayNode customAttributes(String resource, JsonNode existing, ObjectNode changes) {
        JsonNode definitions = client.get("/v1/attributes/custom/resources/{resource}", resource);
        String values = changes.has(CUSTOM_ATTRIBUTES) ? changes.path(CUSTOM_ATTRIBUTES).toString() : "{}";
        return ConfigurationAttributes
                .replace(definitions,
                        existing.hasNonNull(CUSTOM_ATTRIBUTES)
                                ? existing.path(CUSTOM_ATTRIBUTES)
                                : JSON.createArrayNode(),
                        values);
    }

    private String namedRead(String title, String path, String uuid) {
        return run("reading " + title, () -> describe(title, client.get(path, id(uuid)), NAMED_FIELDS));
    }

    private String updateNamed(String title, String resource, String path, String uuid, String configuration) {
        return run("updating " + title, () -> {
            id(uuid);
            ObjectNode changes = namedInput(configuration);
            JsonNode existing = client.get(path, uuid);
            if (GROUPS.equals(resource) && changes.has("name") && !changes.path("name").equals(existing.path("name"))) {
                throw new IllegalArgumentException("Group name cannot be changed by the platform update endpoint.");
            }
            ObjectNode body = namedBody(resource, changes, existing);
            return "Updated " + title + " " + uuid + ".\n"
                    + describe(title, client.put(path, body, uuid), NAMED_FIELDS);
        });
    }

    private String remove(String title, String path, String uuid) {
        return run("deleting " + title, () -> {
            client.delete(path, id(uuid));
            return "Deleted " + title + " " + uuid + ".";
        });
    }

    private void checkMutableRole(String roleUuid) {
        if (client.get(ROLE, roleUuid).path("systemRole").asBoolean(false)) {
            throw new IllegalArgumentException("System role permissions cannot be changed.");
        }
    }

    private JsonNode catalogResource(String resourceUuid) {
        return findResource(client.get(CATALOG), "uuid", id(resourceUuid));
    }

    private static JsonNode findResource(JsonNode catalog, String key, String value) {
        for (JsonNode resource : catalog) {
            if (value != null && value.equals(resource.path(key).asString())) {
                return resource;
            }
        }
        throw new IllegalArgumentException(
                "Permission resource is unavailable on this platform; use list_permission_resources.");
    }

    private static ArrayNode actions(JsonNode values, JsonNode resource) {
        ArrayNode result = strings(values, ACTIONS, false);
        Set<String> available = new HashSet<>();
        for (JsonNode action : resource.path(ACTIONS)) {
            available.add(action.path("name").asString());
        }
        for (JsonNode action : result) {
            if (!available.contains(action.asString())) {
                throw new IllegalArgumentException(
                        "Permission action is unavailable for this resource; use list_permission_resources.");
            }
        }
        return result;
    }

    private static void requireObjectAccess(JsonNode resource) {
        if (!resource.path("objectAccess").asBoolean(false)) {
            throw new IllegalArgumentException("This permission resource does not support object grants.");
        }
    }

    private static void objectGrant(JsonNode object, JsonNode resource, boolean requireUuid) {
        allow(object, "uuid", "name", ALLOW, "deny");
        requireObjectAccess(resource);
        if (requireUuid) {
            id(object.path("uuid").asString(null));
        }
        text(object, "name");
        ArrayNode allow = actions(object.path(ALLOW), resource);
        ArrayNode deny = actions(object.path("deny"), resource);
        Set<String> granted = new HashSet<>();
        allow.forEach(action -> granted.add(action.asString()));
        for (JsonNode action : deny) {
            if (granted.contains(action.asString())) {
                throw new IllegalArgumentException("An action cannot appear in both allow and deny.");
            }
        }
    }

    private static ObjectNode permissionBody(JsonNode current) {
        ObjectNode body = select(current, ALLOW_ALL_RESOURCES);
        ArrayNode resources = JSON.createArrayNode();
        for (JsonNode resource : current.path(RESOURCES)) {
            ObjectNode clean = select(resource, "name", ALLOW_ALL_ACTIONS, ACTIONS);
            ArrayNode objects = JSON.createArrayNode();
            for (JsonNode object : resource.path(OBJECTS)) {
                objects.add(select(object, "uuid", "name", ALLOW, "deny"));
            }
            clean.set(OBJECTS, objects);
            resources.add(clean);
        }
        body.set(RESOURCES, resources);
        return body;
    }

    private static String permissionText(JsonNode permissions) {
        StringBuilder text = new StringBuilder(describe("Permissions", permissions, ALLOW_ALL_RESOURCES));
        for (JsonNode resource : permissions.path(RESOURCES)) {
            text.append(describe("Resource", resource, "name", ALLOW_ALL_ACTIONS, ACTIONS));
            text.append(list("Objects", resource.path(OBJECTS), "uuid", "name", ALLOW, "deny"));
        }
        return text.toString();
    }
}
