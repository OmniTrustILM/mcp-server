package com.otilm.mcp.service;

import com.otilm.api.model.client.attribute.custom.CustomAttributeDefinitionDto;
import com.otilm.api.model.client.notification.NotificationProfileDetailDto;
import com.otilm.api.model.client.notification.NotificationProfileDto;
import com.otilm.api.model.client.notification.NotificationProfileResponseDto;
import com.otilm.api.model.common.UuidDto;
import com.otilm.api.model.core.connector.ConnectorDto;
import com.otilm.api.model.core.notification.AttributeMappingDto;
import com.otilm.api.model.core.notification.NotificationInstanceDto;
import com.otilm.mcp.client.ConnectorAttribute;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.client.NotificationInstanceRequest;
import com.otilm.mcp.client.NotificationInstanceRequest.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import static com.otilm.mcp.service.TextFormat.field;
import static com.otilm.mcp.service.TextFormat.item;
import static com.otilm.mcp.service.TextFormat.title;
import static com.otilm.mcp.service.TextFormat.titledList;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private static final String INDENT = "  ";
    private static final String DELETING_INSTANCE = "deleting notification instance";
    private static final String DELETING_PROFILE = "deleting notification profile";
    private static final int IN_USE = 422;
    private static final String MAPPINGS_INVALID = "Invalid attributeMappings JSON. Expected a JSON object of "
            + "mappable attribute names and custom attribute names or UUIDs, for example "
            + "{\"data_recipientEmailAddress\":\"Contact email\"}. Error: ";

    private final IlmApiClient ilmApiClient;

    public NotificationServiceImpl(IlmApiClient ilmApiClient) {
        this.ilmApiClient = ilmApiClient;
    }

    @Override
    public String listNotificationInstances() {
        return ToolResponses.run("listing notification instances", () -> {
            List<NotificationInstanceDto> instances = ilmApiClient.listNotificationInstances();
            return titledList("Notification instances (" + instances.size() + ")", instances,
                    "No notification instances found.", (sb, instance) -> {
                        item(sb, instance.getName(), instance.getUuid());
                        field(sb, INDENT, "Connector",
                                instance.getConnectorName() + " [" + instance.getConnectorUuid() + "]");
                        field(sb, INDENT, "Kind", instance.getKind());
                        field(sb, INDENT, "Description", instance.getDescription());
                    });
        });
    }

    @Override
    public String listNotificationProfiles() {
        return ToolResponses.run("listing notification profiles", () -> {
            NotificationProfileResponseDto page = ilmApiClient.listNotificationProfiles();
            List<NotificationProfileDto> profiles = page.getNotificationProfiles() == null
                    ? List.of()
                    : page.getNotificationProfiles();
            String text = titledList("Notification profiles (" + profiles.size() + ")", profiles,
                    "No notification profiles found.", (sb, profile) -> {
                        item(sb, profile.getName(), profile.getUuid());
                        field(sb, INDENT, "Recipients", NotificationText
                                .recipients(profile.getRecipientType(),
                                        profile.getRecipientUuids() == null ? 0 : profile.getRecipientUuids().size()));
                        field(sb, INDENT, "Sends through", profile.getNotificationInstanceUuid());
                        field(sb, INDENT, "In-app", profile.isInternalNotification() ? "yes" : null);
                        field(sb, INDENT, "Description", profile.getDescription());
                    });
            long total = page.getTotalItems() == null ? profiles.size() : page.getTotalItems();
            return total > profiles.size()
                    ? text + "Showing the first " + profiles.size() + " of " + total + ".\n"
                    : text;
        });
    }

    @Override
    public String getNotificationConnectorSettings(String connectorUuid, String kind, String attributes) {
        return ToolResponses.run("retrieving notification connector settings", () -> {
            Map<String, Object> values = InstanceFields.values(attributes);
            ConnectorDto connector = ilmApiClient.getConnector(connectorUuid);
            String offered = InstanceFields.kind(connector, kind);
            InstanceFields fields = InstanceFields.resolve(ilmApiClient, connectorUuid, offered, values);
            StringBuilder sb = new StringBuilder();
            title(sb, "Notification settings for " + connector.getName() + " [" + connector.getUuid() + "], kind "
                    + offered);
            NotificationText.settings(sb, fields, ilmApiClient.listNotificationMappings(connectorUuid, offered));
            return sb.toString();
        });
    }

    @Override
    public String createNotificationInstance(NotificationInstanceInput input) {
        return ToolResponses.run("creating notification instance", () -> {
            if (input.name() == null || input.name().isBlank()) {
                throw new IllegalArgumentException("A notification instance needs a name.");
            }
            Map<String, Object> values = InstanceFields.values(input.attributes());
            Map<String, Object> requestedMappings = InstanceFields.object(input.attributeMappings(), MAPPINGS_INVALID);
            ConnectorDto connector = ilmApiClient.getConnector(input.connectorUuid());
            String kind = InstanceFields.kind(connector, input.kind());
            List<Field> fields = InstanceFields
                    .resolve(ilmApiClient, input.connectorUuid(), kind, values)
                    .content(values);
            NotificationInstanceRequest request = new NotificationInstanceRequest(input.name().trim(),
                    input.description(), input.connectorUuid(), kind, fields,
                    mappings(input.connectorUuid(), kind, requestedMappings));
            UuidDto created = ilmApiClient.createNotificationInstance(request);
            return created(request, created.getUuid(), connector.getName(), values);
        });
    }

    /**
     * The mappings of recipient attributes to custom attributes; refuses names the connector does not map and custom
     * attributes that do not exist, which ILM itself would only notice after creating the instance.
     */
    private List<AttributeMappingDto> mappings(String connectorUuid, String kind, Map<String, Object> requested) {
        if (requested.isEmpty()) {
            return List.of();
        }
        List<ConnectorAttribute> mappable = ilmApiClient.listNotificationMappings(connectorUuid, kind);
        List<String> unknown = requested
                .keySet()
                .stream()
                .filter(name -> mappable.stream().noneMatch(attribute -> attribute.name().equals(name)))
                .toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown mappable attributes: %s. Mappable: %s."
                    .formatted(String.join(", ", unknown), mappable.isEmpty()
                            ? "none"
                            : mappable.stream().map(ConnectorAttribute::name).collect(Collectors.joining(", "))));
        }
        List<CustomAttributeDefinitionDto> customAttributes = ilmApiClient.listCustomAttributes();
        List<AttributeMappingDto> mappings = new ArrayList<>();
        requested.forEach((name, target) -> {
            String wanted = String.valueOf(target);
            CustomAttributeDefinitionDto custom = customAttributes
                    .stream()
                    .filter(attribute -> wanted.equals(attribute.getName())
                            || wanted.equalsIgnoreCase(attribute.getUuid()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No custom attribute named or with UUID '%s'. list_custom_attributes lists them."
                                    .formatted(wanted)));
            ConnectorAttribute attribute = mappable
                    .stream()
                    .filter(candidate -> candidate.name().equals(name))
                    .findFirst()
                    .orElseThrow();
            AttributeMappingDto mapping = new AttributeMappingDto();
            mapping.setMappingAttributeUuid(attribute.uuid());
            mapping.setMappingAttributeName(attribute.name());
            mapping.setCustomAttributeUuid(custom.getUuid());
            mapping.setCustomAttributeLabel(custom.getLabel());
            mappings.add(mapping);
        });
        return mappings;
    }

    private static String created(NotificationInstanceRequest request, String uuid, String connector,
            Map<String, Object> values) {
        StringBuilder sb = new StringBuilder("Created notification instance %s [%s] for %s, kind %s."
                .formatted(request.name(), uuid, connector, request.kind())).append("\n");
        List<String> names = request.attributes().stream().map(Field::name).toList();
        field(sb, "", "Set", String.join(", ", names.stream().filter(values::containsKey).toList()));
        field(sb, "", "ILM defaults used",
                String.join(", ", names.stream().filter(name -> !values.containsKey(name)).toList()));
        request
                .attributeMappings()
                .forEach(mapping -> sb
                        .append("Mapped: ")
                        .append(mapping.getMappingAttributeName())
                        .append(" → custom attribute ")
                        .append(mapping.getCustomAttributeLabel())
                        .append(" [")
                        .append(mapping.getCustomAttributeUuid())
                        .append("]\n"));
        return sb.toString();
    }

    @Override
    public String createNotificationProfile(NotificationProfileInput input) {
        return ToolResponses.run("creating notification profile", () -> {
            NotificationProfileDetailDto created = ilmApiClient
                    .createNotificationProfile(ProfileRequests.request(input));
            StringBuilder sb = new StringBuilder(
                    "Created notification profile %s [%s].".formatted(created.getName(), created.getUuid()))
                    .append("\n\n");
            NotificationText.profile(sb, reread(created));
            if (ProfileRequests.frequencyDefaulted(input)) {
                sb
                        .append("\nfrequency was not given, so it is one day: certificate_expiring sends at most one ")
                        .append("notification a day for the same certificate; other events ignore frequency and ")
                        .append("repetitions.\n");
            }
            return sb.toString();
        });
    }

    /**
     * The profile as ILM stored it. The create answer may leave out the instance, so the profile is read again; when
     * that read fails the profile still exists, and the create answer describes it.
     */
    private NotificationProfileDetailDto reread(NotificationProfileDetailDto created) {
        try {
            return ilmApiClient.getNotificationProfile(created.getUuid());
        } catch (RestClientException e) {
            logger.debug("Could not read the created notification profile again: {}", e.getClass().getSimpleName());
            return created;
        }
    }

    @Override
    public String deleteNotificationInstance(String uuid) {
        return ToolResponses.run(DELETING_INSTANCE, () -> {
            NotificationInstanceDto instance = ilmApiClient
                    .listNotificationInstances()
                    .stream()
                    .filter(candidate -> candidate.getUuid().equalsIgnoreCase(uuid == null ? null : uuid.trim()))
                    .findFirst()
                    .orElse(null);
            if (instance == null) {
                return "No notification instance [%s]; list_notification_instances lists them.".formatted(uuid);
            }
            try {
                ilmApiClient.deleteNotificationInstance(instance.getUuid());
            } catch (RestClientResponseException e) {
                return refusal(DELETING_INSTANCE, e,
                        "Delete or change those profiles first; delete_notification_profile deletes one.");
            }
            return "Deleted notification instance %s [%s].".formatted(instance.getName(), instance.getUuid());
        });
    }

    @Override
    public String deleteNotificationProfile(String uuid) {
        return ToolResponses.run(DELETING_PROFILE, () -> {
            NotificationProfileDetailDto profile = ilmApiClient.getNotificationProfile(uuid);
            try {
                ilmApiClient.deleteNotificationProfile(profile.getUuid());
            } catch (RestClientResponseException e) {
                return refusal(DELETING_PROFILE, e, "Delete the triggers that send through it first with "
                        + "delete_trigger; their notify parts are named \"<trigger> - notify\".");
            }
            return "Deleted notification profile %s [%s].".formatted(profile.getName(), profile.getUuid());
        });
    }

    /**
     * ILM's refusal of a delete with what to do first, when ILM refused because something still uses the object.
     */
    private static String refusal(String action, RestClientResponseException e, String hint) {
        if (e.getStatusCode().value() != IN_USE) {
            throw e;
        }
        return ToolResponses.errorText(action, e) + "\n" + hint;
    }

    @Override
    public String getNotificationProfile(String uuid) {
        return ToolResponses.run("retrieving notification profile", () -> {
            StringBuilder sb = new StringBuilder();
            NotificationText.profile(sb, ilmApiClient.getNotificationProfile(uuid));
            return sb.toString();
        });
    }
}
