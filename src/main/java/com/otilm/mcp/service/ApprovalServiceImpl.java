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
import static com.otilm.mcp.service.AdministrationInput.select;
import static com.otilm.mcp.service.AdministrationInput.text;
import static com.otilm.mcp.service.ConfigurationSupport.describe;
import static com.otilm.mcp.service.ConfigurationSupport.list;
import static com.otilm.mcp.service.ConfigurationSupport.required;
import static com.otilm.mcp.service.ConfigurationSupport.run;

@Service
public class ApprovalServiceImpl implements ApprovalService {
    private static final String APPROVAL_PROFILE = " approval profile ";
    private static final String APPROVAL_STEPS = "approvalSteps";
    private static final String DESCRIPTION = "description";
    private static final String EXPIRY = "expiry";
    private static final String GROUP_UUID = "groupUuid";
    private static final String ORDER = "order";
    private static final String REQUIRED_APPROVALS = "requiredApprovals";
    private static final String ROLE_UUID = "roleUuid";
    private static final String USER_UUID = "userUuid";

    private static final String PROFILES = "/v1/approvalProfiles";
    private static final String PROFILE = PROFILES + "/{uuid}";
    private static final String APPROVALS = "/v1/approvals";
    private static final String APPROVAL = APPROVALS + "/{uuid}";
    private static final String[] PROFILE_FIELDS = {
            "uuid",
            "name",
            DESCRIPTION,
            "version",
            EXPIRY,
            "numberOfSteps",
            "associations"};
    private static final String[] APPROVAL_FIELDS = {
            "approvalUuid",
            "status",
            "version",
            "resource",
            "resourceAction",
            "objectUuid",
            "approvalProfileName",
            "approvalProfileUuid",
            "createdAt",
            "expiryAt",
            "closedAt"};
    private final ConfigurationApiClient client;

    public ApprovalServiceImpl(ConfigurationApiClient client) {
        this.client = client;
    }

    @Override
    public String listApprovalProfiles(int page, int size) {
        return run("listing approval profiles", () -> {
            pagination(page, size);
            JsonNode result = client.get(PROFILES + "?pageNumber={page}&itemsPerPage={size}", page, size);
            return list("Approval profiles", result.path("approvalProfiles"), PROFILE_FIELDS);
        });
    }

    @Override
    public String getApprovalProfile(String uuid, Integer version) {
        return run("reading approval profile", () -> {
            id(uuid);
            if (version != null && version < 1) {
                throw new IllegalArgumentException("version must be positive.");
            }
            return profileText(version == null
                    ? client.get(PROFILE, uuid)
                    : client.get(PROFILE + "?version={version}", uuid, version));
        });
    }

    @Override
    public String createApprovalProfile(String configuration) {
        return run("creating approval profile", () -> {
            ObjectNode body = configuration(configuration, "name", DESCRIPTION, "enabled", EXPIRY, APPROVAL_STEPS);
            required(body.path("name").asString(null), "name");
            text(body, "name", DESCRIPTION);
            bool(body, "enabled");
            validateInputSteps(body);
            validateProfile(body);
            JsonNode result = client.post(PROFILES, body);
            String uuid = id(result.path("uuid").asString(null));
            return recordedProfile(uuid, "Created");
        });
    }

    @Override
    public String updateApprovalProfile(String uuid, String configuration) {
        return run("updating approval profile", () -> {
            id(uuid);
            ObjectNode changes = configuration(configuration, DESCRIPTION, EXPIRY, APPROVAL_STEPS);
            text(changes, DESCRIPTION);
            validateInputSteps(changes);
            ObjectNode body = select(client.get(PROFILE, uuid), DESCRIPTION, EXPIRY, APPROVAL_STEPS);
            changes.properties().forEach(entry -> body.set(entry.getKey(), entry.getValue()));
            validateProfile(body);
            client.put(PROFILE, body, uuid);
            return recordedProfile(uuid, "Updated");
        });
    }

    @Override
    public String deleteApprovalProfile(String uuid) {
        return run("deleting approval profile", () -> {
            client.delete(PROFILE, id(uuid));
            return "Deleted approval profile " + uuid + ".";
        });
    }

    @Override
    public String getApprovalProfileAssociations(String uuid) {
        return run("reading approval profile associations", () -> list("Associations",
                client.get(PROFILE + "/associations", id(uuid)), "resource", "objectUuid", "uuid", "name"));
    }

    @Override
    public String getAssociatedApprovalProfiles(String resource, String objectUuid) {
        return run("reading associated approval profiles", () -> {
            associationResource(resource);
            return list("Approval profiles",
                    client.get(PROFILES + "/associations/{resource}/{object}", resource, id(objectUuid)),
                    PROFILE_FIELDS);
        });
    }

    @Override
    public String setApprovalProfileAssociation(String uuid, String resource, String objectUuid, boolean associate) {
        return run("changing approval profile association", () -> {
            id(uuid);
            id(objectUuid);
            associationResource(resource);
            String path = PROFILE + "/associations/{resource}/{object}";
            if (associate) {
                client.patch(path, null, uuid, resource, objectUuid);
            } else {
                client.delete(path, uuid, resource, objectUuid);
            }
            return (associate ? "Associated" : "Disassociated") + APPROVAL_PROFILE + uuid + " with " + resource + " "
                    + objectUuid + ".";
        });
    }

    @Override
    public String listApprovals(int page, int size, boolean currentUser, boolean history) {
        return run("listing approvals", () -> {
            pagination(page, size);
            if (history && !currentUser) {
                throw new IllegalArgumentException("history requires currentUser=true.");
            }
            JsonNode result = currentUser
                    ? client
                            .get(APPROVALS + "/user?pageNumber={page}&itemsPerPage={size}&history={history}", page,
                                    size, history)
                    : client.get(APPROVALS + "?pageNumber={page}&itemsPerPage={size}", page, size);
            return list("Approvals", result.path("approvals"), APPROVAL_FIELDS);
        });
    }

    @Override
    public String getApproval(String uuid) {
        return run("reading approval", () -> approvalText(client.get(APPROVAL, id(uuid))));
    }

    @Override
    public String decideApproval(String uuid, boolean approve, String comment) {
        return run("recording approval recipient decision", () -> {
            id(uuid);
            ObjectNode body = JSON.createObjectNode();
            if (comment != null) {
                body.put("comment", comment);
            }
            client.patch(APPROVAL + (approve ? "/approveRecipient" : "/rejectRecipient"), body, uuid);
            String recorded = "Recipient decision recorded for approval " + uuid + ". ";
            try {
                return recorded + approvalText(client.get(APPROVAL, uuid))
                        + "\nThe underlying operation may still be pending; inspect its resource status before treating it as complete.";
            } catch (RuntimeException error) {
                return recorded
                        + "Current approval state could not be read. Inspect the approval and underlying resource before treating the operation as complete.";
            }
        });
    }

    private String recordedProfile(String uuid, String action) {
        try {
            return action + APPROVAL_PROFILE + uuid + ".\n" + profileText(client.get(PROFILE, uuid));
        } catch (RuntimeException error) {
            return action + APPROVAL_PROFILE + uuid + ". Current version could not be read; inspect the profile.";
        }
    }

    private static String profileText(JsonNode result) {
        return describe("Approval profile", result, PROFILE_FIELDS) + "\n"
                + list("Ordered approval steps", result.path(APPROVAL_STEPS), ORDER, USER_UUID, ROLE_UUID, GROUP_UUID,
                        "username", "roleName", "groupName", DESCRIPTION, REQUIRED_APPROVALS);
    }

    private static String approvalText(JsonNode result) {
        return describe("Approval", result, APPROVAL_FIELDS) + "\n"
                + list("Approval steps", result.path(APPROVAL_STEPS), ORDER, USER_UUID, ROLE_UUID, GROUP_UUID,
                        REQUIRED_APPROVALS, "username", "roleName", "groupName");
    }

    private static void validateInputSteps(ObjectNode body) {
        for (JsonNode step : body.path(APPROVAL_STEPS)) {
            allow(step, USER_UUID, ROLE_UUID, GROUP_UUID, DESCRIPTION, ORDER, REQUIRED_APPROVALS);
        }
    }

    private static void validateProfile(ObjectNode body) {
        if (body.hasNonNull(EXPIRY) && (!body.path(EXPIRY).isInt() || body.path(EXPIRY).asInt() <= 0)) {
            throw new IllegalArgumentException("expiry must be a positive number of hours or null.");
        }
        JsonNode input = body.path(APPROVAL_STEPS);
        if (!input.isArray() || input.isEmpty()) {
            throw new IllegalArgumentException("approvalSteps must contain at least one ordered recipient step.");
        }
        ArrayNode steps = JSON.createArrayNode();
        Set<Integer> orders = new HashSet<>();
        for (JsonNode step : input) {
            steps.add(validatedStep(step, orders));
        }
        // Keep caller ordering explicit: the platform orders steps by their order field.
        body.set(APPROVAL_STEPS, steps);
    }

    private static ObjectNode validatedStep(JsonNode step, Set<Integer> orders) {
        if (!step.isObject()) {
            throw new IllegalArgumentException("Each approval step must be an object.");
        }
        // Response-only fields are removed when retaining the existing ordered steps.
        ObjectNode clean = select(step, USER_UUID, ROLE_UUID, GROUP_UUID, DESCRIPTION, ORDER, REQUIRED_APPROVALS);
        validateRecipients(clean);
        if (!clean.path(ORDER).isInt() || clean.path(ORDER).asInt() < 1 || !orders.add(clean.path(ORDER).asInt())) {
            throw new IllegalArgumentException("Step order must be positive and unique.");
        }
        if (!clean.path(REQUIRED_APPROVALS).isInt() || clean.path(REQUIRED_APPROVALS).asInt() < 1
                || (clean.hasNonNull(USER_UUID) && clean.path(REQUIRED_APPROVALS).asInt() != 1)) {
            throw new IllegalArgumentException(
                    "requiredApprovals must be positive; a user recipient requires exactly one approval.");
        }
        text(clean, DESCRIPTION);
        return clean;
    }

    private static void validateRecipients(JsonNode step) {
        int recipients = 0;
        for (String recipient : new String[]{USER_UUID, ROLE_UUID, GROUP_UUID}) {
            if (step.hasNonNull(recipient)) {
                id(step.path(recipient).asString(null));
                recipients++;
            }
        }
        if (recipients != 1) {
            throw new IllegalArgumentException("Each step requires exactly one userUuid, roleUuid or groupUuid.");
        }
    }

    private static void associationResource(String resource) {
        if (!Set.of("raProfiles", "vaultProfiles").contains(resource)) {
            throw new IllegalArgumentException("Approval association resource must be raProfiles or vaultProfiles.");
        }
    }

    private static void pagination(int page, int size) {
        if (page < 1 || size < 1 || size > 1000) {
            throw new IllegalArgumentException("Page must be positive and page size must be between 1 and 1000.");
        }
    }
}
