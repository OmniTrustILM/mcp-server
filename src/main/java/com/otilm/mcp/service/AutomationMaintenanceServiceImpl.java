package com.otilm.mcp.service;

import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.connector.FunctionGroupCode;
import com.otilm.api.model.core.other.ResourceEvent;
import com.otilm.mcp.client.ConfigurationApiClient;
import com.otilm.mcp.client.IlmApiClient;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static com.otilm.mcp.service.AdministrationInput.JSON;
import static com.otilm.mcp.service.AdministrationInput.bool;
import static com.otilm.mcp.service.AdministrationInput.configuration;
import static com.otilm.mcp.service.AdministrationInput.id;
import static com.otilm.mcp.service.AdministrationInput.select;
import static com.otilm.mcp.service.AdministrationInput.strings;
import static com.otilm.mcp.service.AdministrationInput.text;
import static com.otilm.mcp.service.AdministrationInput.uuids;
import static com.otilm.mcp.service.ConfigurationSupport.describe;
import static com.otilm.mcp.service.ConfigurationSupport.list;
import static com.otilm.mcp.service.ConfigurationSupport.required;
import static com.otilm.mcp.service.ConfigurationSupport.run;

@Service
public class AutomationMaintenanceServiceImpl implements AutomationMaintenanceService {
    private static final String TRIGGER_2 = "Trigger ";
    private static final String ATTRIBUTE_MAPPINGS = "attributeMappings";
    private static final String ATTRIBUTES = "attributes";
    private static final String CONDITIONS = "conditions";
    private static final String DESCRIPTION = "description";
    private static final String EVENT = "event";
    private static final String EVENT_DATA_CATEGORIES = "eventDataCategories";
    private static final String FREQUENCY = "frequency";
    private static final String IGNORE = "ignore";
    private static final String IGNORE_TRIGGER = "ignoreTrigger";
    private static final String INTERNAL_NOTIFICATION = "internalNotification";
    private static final String NOTIFICATION_INSTANCE_UUID = "notificationInstanceUuid";
    private static final String NOTIFICATION_PROFILE_UUID = "notificationProfileUuid";
    private static final String OBJECT_UUID = "objectUuid";
    private static final String RECIPIENT_TYPE = "recipientType";
    private static final String RECIPIENT_UUIDS = "recipientUuids";
    private static final String REPETITIONS = "repetitions";
    private static final String REPLACE_COMPOSITION = "replaceComposition";
    private static final String RESOURCE = "resource";
    private static final String RULES = "rules";
    private static final String SET_FIELDS = "setFields";

    private static final String WORKFLOW = "/v1/workflows";
    private static final String TRIGGER = WORKFLOW + "/triggers/{uuid}";
    private static final String INSTANCE = "/v1/notificationInstances/{uuid}";
    private static final String PROFILE = "/v1/notificationProfiles/{uuid}";
    private static final String IDENTITY = "Replacing this ordered list rebinds the execution identity of every included association to the authenticated caller, including the retained associations.";
    private final ConfigurationApiClient client;
    private final IlmApiClient legacy;
    private final PlatformVersionCheck versions;
    private final Object associationLock = new Object();

    public AutomationMaintenanceServiceImpl(ConfigurationApiClient client, IlmApiClient legacy,
            PlatformVersionCheck versions) {
        this.client = client;
        this.legacy = legacy;
        this.versions = versions;
    }

    @Override
    public String getScopedTriggers(String resource, String objectUuid) {
        return run("reading scoped triggers", () -> {
            scope(resource, objectUuid);
            return describe("Scoped trigger lists",
                    client.get(WORKFLOW + "/events/{resource}/{object}", resource, objectUuid), eventCodes());
        });
    }

    @Override
    public String attachScopedTrigger(String resource, String objectUuid, String event, String triggerUuid,
            Integer position) {
        return run("attaching scoped trigger", () -> {
            ResourceEvent checked = event(resource, objectUuid, event);
            id(triggerUuid);
            synchronized (associationLock) {
                List<String> current = attached(resource, objectUuid, event);
                if (current.contains(triggerUuid)) {
                    return TRIGGER_2 + triggerUuid + " is already attached; no association changed.";
                }
                int index = position == null ? current.size() : position - 1;
                if (index < 0 || index > current.size()) {
                    throw new IllegalArgumentException("Position must be between 1 and list size plus one.");
                }
                current.add(index, triggerUuid);
                return replace(resource, objectUuid, checked, current);
            }
        });
    }

    @Override
    public String detachScopedTrigger(String resource, String objectUuid, String event, String triggerUuid) {
        return run("detaching scoped trigger", () -> {
            ResourceEvent checked = event(resource, objectUuid, event);
            id(triggerUuid);
            synchronized (associationLock) {
                List<String> current = attached(resource, objectUuid, event);
                if (!current.remove(triggerUuid)) {
                    return TRIGGER_2 + triggerUuid + " is not attached; no association changed.";
                }
                return replace(resource, objectUuid, checked, current);
            }
        });
    }

    @Override
    public String reorderScopedTriggers(String resource, String objectUuid, String event, String triggerUuids) {
        return run("reordering scoped triggers", () -> {
            ResourceEvent checked = event(resource, objectUuid, event);
            ArrayNode desired = uuids(triggerUuids);
            synchronized (associationLock) {
                List<String> current = attached(resource, objectUuid, event);
                List<String> ordered = new ArrayList<>();
                desired.forEach(value -> ordered.add(value.asString()));
                if (!new HashSet<>(current).equals(new HashSet<>(ordered))) {
                    throw new IllegalArgumentException(
                            "Reordering must contain the same trigger UUIDs as the current list.");
                }
                if (current.equals(ordered)) {
                    return "Trigger order is unchanged; no association changed.";
                }
                return replace(resource, objectUuid, checked, ordered);
            }
        });
    }

    @Override
    public String getScopedTriggerHistory(String objectUuid, String triggerUuid) {
        return run("reading scoped trigger history", () -> {
            id(objectUuid);
            JsonNode history = triggerUuid == null
                    ? client.get(WORKFLOW + "/triggers/history/{object}", objectUuid)
                    : client.get(WORKFLOW + "/triggers/{trigger}/history/{object}", id(triggerUuid), objectUuid);
            if (triggerUuid == null) {
                return describe("Trigger association history summary", history, "associationResource",
                        "associationObjectUuid", "objectsResource", "objectsEvaluated", "objectsMatched",
                        "objectsIgnored")
                        + list("Evaluated objects", history.path("objects"), OBJECT_UUID, "referenceObjectUuid",
                                "hostObject.uuid", "hostObject.name", "matched", "ignored");
            }
            return list("Trigger association history", history, "uuid", "triggerUuid", OBJECT_UUID,
                    "referenceObjectUuid", "conditionsMatched", "actionsPerformed", "triggeredAt");
        });
    }

    @Override
    public String getNotificationInstance(String uuid) {
        return run("reading notification instance", () -> describe("Notification instance",
                client.get(INSTANCE, id(uuid)), "uuid", "name", DESCRIPTION, "connectorUuid", "connectorName", "kind"));
    }

    @Override
    public String updateNotificationInstance(String uuid, String configuration) {
        return run("updating notification instance", () -> {
            id(uuid);
            ObjectNode changes = configuration(configuration, DESCRIPTION, ATTRIBUTES, ATTRIBUTE_MAPPINGS);
            text(changes, DESCRIPTION);
            JsonNode current = client.get(INSTANCE, uuid);
            String connector = id(current.path("connectorUuid").asString(null));
            String kind = required(current.path("kind").asString(null), "Notification kind");
            String values = changes.has(ATTRIBUTES) ? changes.path(ATTRIBUTES).toString() : "{}";
            JsonNode definitions = client
                    .get("/v1/connectors/{connector}/attributes/notificationProvider/{kind}", connector, kind);
            JsonNode resolved = ConfigurationAttributes
                    .resolveReplacement(client,
                            new ConfigurationAttributes.ConnectorContext(connector,
                                    FunctionGroupCode.NOTIFICATION_PROVIDER, kind),
                            definitions, CertificateConfiguration.array(current.path(ATTRIBUTES)), values);
            ObjectNode body = select(current, DESCRIPTION);
            if (changes.has(DESCRIPTION)) {
                body.set(DESCRIPTION, changes.get(DESCRIPTION));
            }
            body
                    .set(ATTRIBUTES, ConfigurationAttributes
                            .replace(resolved, CertificateConfiguration.array(current.path(ATTRIBUTES)), values));
            body
                    .set(ATTRIBUTE_MAPPINGS,
                            changes.has(ATTRIBUTE_MAPPINGS)
                                    ? mappings(connector, kind, changes.path(ATTRIBUTE_MAPPINGS))
                                    : CertificateConfiguration.array(current.path(ATTRIBUTE_MAPPINGS)));
            client.put(INSTANCE, body, uuid);
            return "Updated notification instance " + uuid + "; provider values remain omitted from this response.";
        });
    }

    @Override
    public String updateNotificationProfile(String uuid, String configuration) {
        return run("updating notification profile", () -> {
            id(uuid);
            ObjectNode changes = configuration(configuration, DESCRIPTION, RECIPIENT_TYPE, RECIPIENT_UUIDS,
                    NOTIFICATION_INSTANCE_UUID, INTERNAL_NOTIFICATION, FREQUENCY, REPETITIONS, EVENT_DATA_CATEGORIES);
            text(changes, DESCRIPTION, RECIPIENT_TYPE, NOTIFICATION_INSTANCE_UUID, FREQUENCY);
            bool(changes, INTERNAL_NOTIFICATION);
            if (changes.has(EVENT_DATA_CATEGORIES)) {
                require(IlmFeature.NOTIFICATION_EVENT_DATA);
            }
            JsonNode current = client.get(PROFILE, uuid);
            ObjectNode body = notificationProfileRequest(current, changes);
            validateNotificationProfile(body);
            client.put(PROFILE, body, uuid);
            String output = "Updated notification profile " + uuid + ".";
            try {
                output += "\n" + describe("Current profile version", client.get(PROFILE, uuid), "uuid", "name",
                        "version", RECIPIENT_TYPE, INTERNAL_NOTIFICATION, FREQUENCY, REPETITIONS);
            } catch (RuntimeException unknown) {
                output += " Current version could not be read.";
            }
            return output;
        });
    }

    private static ObjectNode notificationProfileRequest(JsonNode current, ObjectNode changes) {
        ObjectNode body = select(current, DESCRIPTION, RECIPIENT_TYPE, INTERNAL_NOTIFICATION, FREQUENCY, REPETITIONS,
                EVENT_DATA_CATEGORIES);
        ArrayNode recipients = JSON.createArrayNode();
        for (JsonNode recipient : current.path("recipients")) {
            recipients.add(id(recipient.path("uuid").asString(null)));
        }
        body.set(RECIPIENT_UUIDS, recipients);
        if (current.path("notificationInstance").hasNonNull("uuid")) {
            body.set(NOTIFICATION_INSTANCE_UUID, current.path("notificationInstance").get("uuid"));
        }
        if (changes.has(RECIPIENT_TYPE) && !changes.path(RECIPIENT_TYPE).equals(current.path(RECIPIENT_TYPE))
                && !changes.has(RECIPIENT_UUIDS)) {
            throw new IllegalArgumentException(
                    "Changing recipientType requires an explicit recipientUuids list, including [] for types without recipients.");
        }
        changes.properties().forEach(entry -> body.set(entry.getKey(), entry.getValue()));
        return body;
    }

    private static void validateNotificationProfile(ObjectNode body) {
        body.set(RECIPIENT_UUIDS, strings(body.path(RECIPIENT_UUIDS), RECIPIENT_UUIDS, true));
        if (body.hasNonNull(NOTIFICATION_INSTANCE_UUID)) {
            id(body.path(NOTIFICATION_INSTANCE_UUID).asString(null));
        }
        if (body.hasNonNull(REPETITIONS) && !body.path(REPETITIONS).isInt()) {
            throw new IllegalArgumentException("repetitions must be an integer or null.");
        }
        List<String> recipientIds = new ArrayList<>();
        body.path(RECIPIENT_UUIDS).forEach(value -> recipientIds.add(value.asString()));
        ProfileRequests
                .request(new NotificationProfileInput("Profile", body.path(RECIPIENT_TYPE).asString(null),
                        String.join(",", recipientIds), body.path(NOTIFICATION_INSTANCE_UUID).asString(null),
                        body.path(INTERNAL_NOTIFICATION).asBoolean(false), body.path(FREQUENCY).asString(null),
                        body.hasNonNull(REPETITIONS) ? body.path(REPETITIONS).asInt() : null));
        validateEventDataCategories(body);
    }

    private static void validateEventDataCategories(JsonNode body) {
        if (body.has(EVENT_DATA_CATEGORIES)) {
            for (JsonNode category : strings(body.path(EVENT_DATA_CATEGORIES), EVENT_DATA_CATEGORIES, false)) {
                if (!Set
                        .of("customAttributes", "metadata", "associations", "objectContent")
                        .contains(category.asString())) {
                    throw new IllegalArgumentException("Unsupported notification event data category.");
                }
            }
        }
    }

    @Override
    public String updateTrigger(String uuid, String configuration) {
        return run("updating trigger", () -> {
            id(uuid);
            ObjectNode changes = configuration(configuration, "name", DESCRIPTION, REPLACE_COMPOSITION, CONDITIONS,
                    SET_FIELDS, NOTIFICATION_PROFILE_UUID, IGNORE);
            text(changes, "name", DESCRIPTION, NOTIFICATION_PROFILE_UUID);
            bool(changes, REPLACE_COMPOSITION);
            bool(changes, IGNORE);
            if (changes.has("name")) {
                require(IlmFeature.WORKFLOW_RENAME);
            }
            JsonNode current = client.get(TRIGGER, uuid);
            ObjectNode snapshot = triggerRequest(current);
            if (versions.unsupported(IlmFeature.WORKFLOW_RENAME).isPresent()) {
                snapshot.remove("name");
            }
            ObjectNode desired = triggerMetadata(snapshot, changes);
            boolean composition = changes.has(CONDITIONS) || changes.has(SET_FIELDS)
                    || changes.has(NOTIFICATION_PROFILE_UUID) || changes.has(IGNORE);
            if (!composition) {
                client.put(TRIGGER, desired, uuid);
                return "Updated trigger " + uuid + "; existing composition and all associations retained.";
            }
            validateComposition(changes);
            TriggerRequests.TriggerPlan plan = replacementPlan(current, desired, changes);
            TriggerChain chain = new TriggerChain(legacy);
            try {
                JsonNode parts = JSON.valueToTree(chain.createParts(plan));
                desired.set("rulesUuids", parts.path("rulesUuids"));
                desired.set("actionsUuids", parts.path("actionsUuids"));
                desired.set(IGNORE_TRIGGER, parts.path(IGNORE_TRIGGER));
            } catch (RuntimeException failedCreation) {
                return TRIGGER_2 + uuid + " was not changed. " + chain.undoSafe();
            }
            try {
                client.put(TRIGGER, desired, uuid);
            } catch (RuntimeException failedUpdate) {
                return "Trigger update was not confirmed for " + uuid + ". " + restoreTrigger(uuid, snapshot, chain);
            }
            return "Updated trigger " + uuid + "; UUID and all platform/scoped associations retained. "
                    + cleanup(current);
        });
    }

    private static ObjectNode triggerMetadata(ObjectNode snapshot, ObjectNode changes) {
        ObjectNode desired = snapshot.deepCopy();
        for (String field : new String[]{"name", DESCRIPTION}) {
            if (changes.has(field)) {
                desired.set(field, changes.get(field));
            }
        }
        return desired;
    }

    private static void validateComposition(JsonNode changes) {
        if (!changes.path(REPLACE_COMPOSITION).asBoolean(false)) {
            throw new IllegalArgumentException(
                    "Set replaceComposition=true to explicitly replace the complete workflow composition.");
        }
        for (String field : new String[]{CONDITIONS, SET_FIELDS, NOTIFICATION_PROFILE_UUID, IGNORE}) {
            if (!changes.has(field)) {
                throw new IllegalArgumentException(
                        "Complete replacement requires conditions, setFields, notificationProfileUuid (or null), and ignore.");
            }
        }
        if (!changes.path(CONDITIONS).isArray() || !changes.path(SET_FIELDS).isArray()) {
            throw new IllegalArgumentException("conditions and setFields must be JSON arrays.");
        }
    }

    private TriggerRequests.TriggerPlan replacementPlan(JsonNode current, ObjectNode desired, JsonNode changes) {
        String event = current.path(EVENT).asString(null);
        if (event == null) {
            throw new IllegalArgumentException("Whole-workflow composition requires an event trigger.");
        }
        String partName = desired.path("name").asString(current.path("name").asString("Trigger")) + " - revision "
                + UUID.randomUUID();
        TriggerRequests.TriggerPlan plan;
        try {
            plan = TriggerRequests
                    .plan(new TriggerInput(partName, event, changes.path(CONDITIONS).toString(),
                            changes.path(SET_FIELDS).toString(), changes.path(NOTIFICATION_PROFILE_UUID).asString(null),
                            changes.path(IGNORE).asBoolean(), false));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(
                    "Invalid complete workflow composition; use supported condition/set-field schemas and compatible notification/ignore choices.");
        }
        if (!plan.conditions().isEmpty()) {
            TriggerRequests
                    .checkConditionFields(plan.conditions(),
                            legacy.listRuleFilterFields(plan.event().getResource().getCode(), false));
        }
        if (!plan.setFields().isEmpty()) {
            TriggerRequests
                    .checkSetFields(plan.setFields(),
                            legacy.listRuleFilterFields(plan.event().getResource().getCode(), true));
        }
        if (plan.notificationProfileUuid() != null) {
            client.get(PROFILE, plan.notificationProfileUuid());
        }
        return plan;
    }

    private String restoreTrigger(String uuid, ObjectNode snapshot, TriggerChain chain) {
        try {
            client.put(TRIGGER, snapshot, uuid);
            return "Restored original trigger references. " + chain.undoSafe();
        } catch (RuntimeException failedRestore) {
            return "Could not confirm restoration; retained newly created parts: " + chain.retainedParts()
                    + ". Inspect trigger " + uuid + " before cleanup.";
        }
    }

    private String cleanup(JsonNode old) {
        String name = old.path("name").asString("");
        List<String> kept = new ArrayList<>();
        Set<String> tried = new HashSet<>();
        for (String collection : new String[]{RULES, "actions"}) {
            for (JsonNode parent : old.path(collection)) {
                cleanupParent(collection, parent, name, tried, kept);
            }
        }
        return kept.isEmpty()
                ? "Removed unused owned replacement parts; unrelated/shared parts retained."
                : "Kept parts still in use or not removable: " + String.join(", ", kept) + ".";
    }

    private void cleanupParent(String collection, JsonNode parent, String name, Set<String> tried, List<String> kept) {
        if (!owned(parent, name) || !removePart(collection, parent, tried, kept)) {
            return;
        }
        String childCollection = RULES.equals(collection) ? CONDITIONS : "executions";
        for (JsonNode child : parent.path(childCollection)) {
            if (owned(child, name)) {
                removePart(childCollection, child, tried, kept);
            }
        }
    }

    private boolean removePart(String collection, JsonNode part, Set<String> tried, List<String> kept) {
        String uuid = id(part.path("uuid").asString(null));
        if (!tried.add(uuid)) {
            return false;
        }
        try {
            client.delete(WORKFLOW + "/" + collection + "/{uuid}", uuid);
            return true;
        } catch (RuntimeException inUse) {
            kept.add(collection + " " + uuid);
            return false;
        }
    }

    private static boolean owned(JsonNode part, String name) {
        String partName = part.path("name").asString("");
        return TriggerChain.DESCRIPTION.equals(part.path(DESCRIPTION).asString())
                && (partName.equals(name) || partName.startsWith(name + " - "));
    }

    private static ObjectNode triggerRequest(JsonNode current) {
        ObjectNode body = select(current, "name", DESCRIPTION, "type", RESOURCE, EVENT, IGNORE_TRIGGER);
        for (String collection : new String[]{RULES, "actions"}) {
            ArrayNode ids = JSON.createArrayNode();
            for (JsonNode item : current.path(collection)) {
                ids.add(id(item.path("uuid").asString(null)));
            }
            body.set(collection + "Uuids", ids);
        }
        return body;
    }

    private ArrayNode mappings(String connector, String kind, JsonNode input) {
        if (!input.isObject()) {
            throw new IllegalArgumentException(
                    "attributeMappings must be a plain mapping-name to custom attribute name/UUID JSON object.");
        }
        JsonNode available = client
                .get("/v1/notificationInstances/attributes/mapping/{connector}/{kind}", connector, kind);
        JsonNode custom = client.get("/v1/attributes/custom");
        ArrayNode result = JSON.createArrayNode();
        for (var entry : input.properties()) {
            if (!entry.getValue().isString()) {
                throw new IllegalArgumentException("Mapping targets must be custom attribute names or UUIDs.");
            }
            JsonNode mapping = findMapping(available, entry.getKey());
            JsonNode target = findCustomAttribute(custom, entry.getValue().asString());
            if (mapping == null || target == null) {
                throw new IllegalArgumentException("Unknown notification mapping or custom attribute target.");
            }
            ObjectNode encoded = JSON.createObjectNode();
            encoded.put("mappingAttributeUuid", id(mapping.path("uuid").asString(null)));
            encoded.put("mappingAttributeName", entry.getKey());
            encoded.put("customAttributeUuid", id(target.path("uuid").asString(null)));
            if (target.has("label")) {
                encoded.set("customAttributeLabel", target.path("label"));
            }
            result.add(encoded);
        }
        return result;
    }

    private static JsonNode findMapping(JsonNode available, String name) {
        JsonNode mapping = null;
        for (JsonNode item : available) {
            if (name.equals(item.path("name").asString())) {
                mapping = item;
            }
        }
        return mapping;
    }

    private static JsonNode findCustomAttribute(JsonNode custom, String reference) {
        JsonNode target = null;
        for (JsonNode item : custom) {
            if (reference.equals(item.path("name").asString()) || reference.equals(item.path("uuid").asString())) {
                target = item;
            }
        }
        return target;
    }

    private List<String> attached(String resource, String object, String event) {
        List<String> result = new ArrayList<>();
        for (JsonNode value : client.get(WORKFLOW + "/events/{resource}/{object}", resource, object).path(event)) {
            result.add(id(value.asString(null)));
        }
        return result;
    }

    private String replace(String resource, String object, ResourceEvent event, List<String> ordered) {
        boolean normalSeen = false;
        for (String uuid : ordered) {
            JsonNode trigger = client.get(TRIGGER, uuid);
            if (!event.getResource().getCode().equals(trigger.path(RESOURCE).asString())
                    || trigger.hasNonNull(EVENT) && !event.getCode().equals(trigger.path(EVENT).asString())) {
                throw new IllegalArgumentException("Trigger resource/event does not match the requested scoped event.");
            }
            if (trigger.path(IGNORE_TRIGGER).asBoolean(false)) {
                if (normalSeen) {
                    throw new IllegalArgumentException(
                            "Ignore triggers must be placed before ordinary triggers; choose an earlier insertion position or valid complete order.");
                }
            } else {
                normalSeen = true;
            }
        }
        ObjectNode body = JSON.createObjectNode();
        body.put(RESOURCE, resource);
        body.put(OBJECT_UUID, object);
        body.put(EVENT, event.getCode());
        ArrayNode ids = body.putArray("triggerUuids");
        ordered.forEach(ids::add);
        client.post(WORKFLOW + "/events", body);
        return "Updated scoped triggers for " + resource + " " + object + ": " + String.join(", ", ordered) + ".\n"
                + IDENTITY;
    }

    private ResourceEvent event(String resource, String object, String event) {
        Resource scoped = scope(resource, object);
        ResourceEvent checked = TriggerRequests.event(event);
        if (checked.getResource() != scoped && !checked.getOverridingResources().contains(scoped)) {
            throw new IllegalArgumentException("This event is incompatible with the selected scope resource.");
        }
        if ("certificate_registered".equals(event)) {
            require(IlmFeature.CERTIFICATE_REGISTRATION);
        }
        return checked;
    }

    private static Resource scope(String resource, String object) {
        id(object);
        return switch (resource) {
            case "discoveries" -> Resource.DISCOVERY;
            case "raProfiles" -> Resource.RA_PROFILE;
            case "groups" -> Resource.GROUP;
            default -> throw new IllegalArgumentException("Scope resource must be discoveries, raProfiles or groups.");
        };
    }

    private static String[] eventCodes() {
        return java.util.Arrays.stream(ResourceEvent.values()).map(ResourceEvent::getCode).toArray(String[]::new);
    }

    private void require(IlmFeature feature) {
        versions.unsupported(feature).ifPresent(message -> {
            throw new IllegalArgumentException(message);
        });
    }
}
