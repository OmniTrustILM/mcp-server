package com.otilm.mcp.service;

import com.otilm.api.model.core.connector.ConnectorDto;
import com.otilm.api.model.core.connector.FunctionGroupCode;
import com.otilm.api.model.core.connector.FunctionGroupDto;
import com.otilm.mcp.client.ConnectorAttribute;
import com.otilm.mcp.client.ConnectorAttribute.Mapping;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.client.NotificationInstanceRequest.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The fields a connector asks for when a notification instance is created, including the ones a connector callback
 * provides once the values they depend on are known, and the attribute content built from the values given for them.
 */
final class InstanceFields {

    static final String CODEBLOCK = "codeblock";

    private static final String LANGUAGE = "language";
    private static final String NO_VALUE = "Field %s has no value; leave it out to use the connector's default.";

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();
    private static final Set<String> TEXT = Set.of("string", "text", CODEBLOCK);
    private static final Set<String> FILLABLE = Set
            .of("string", "text", "integer", "float", "boolean", "date", "time", "datetime", CODEBLOCK);

    private final String kind;
    private final List<ConnectorAttribute> settable;
    private final List<ConnectorAttribute> information;
    private final List<String> unresolved;

    private InstanceFields(String kind, List<ConnectorAttribute> settable, List<ConnectorAttribute> information,
            List<String> unresolved) {
        this.kind = kind;
        this.settable = settable;
        this.information = information;
        this.unresolved = unresolved;
    }

    static Map<String, Object> values(String json) {
        return object(json, "Invalid attributes JSON. Expected a JSON object of field names and values, for example "
                + "{\"data_webhookUrl\":\"https://example.com/hook\",\"data_contentType\":\"JSON\"}. Error: ");
    }

    /**
     * Reads a JSON object; null, blank or a JSON null means an empty one.
     */
    static Map<String, Object> object(String json, String invalid) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> values = JSON_MAPPER.readValue(json, new TypeReference<>() {
            });
            return values == null ? Map.of() : values;
        } catch (JacksonException e) {
            throw new IllegalArgumentException(invalid + e.getOriginalMessage(), e);
        }
    }

    /**
     * The connector's spelling of a notification kind it offers; refuses a kind it does not offer.
     */
    static String kind(ConnectorDto connector, String kind) {
        List<String> kinds = connector.getFunctionGroups() == null
                ? List.of()
                : connector
                        .getFunctionGroups()
                        .stream()
                        .filter(group -> group.getFunctionGroupCode() == FunctionGroupCode.NOTIFICATION_PROVIDER)
                        .map(FunctionGroupDto::getKinds)
                        .filter(Objects::nonNull)
                        .flatMap(List::stream)
                        .toList();
        return kinds
                .stream()
                .filter(offered -> offered.equalsIgnoreCase(kind == null ? "" : kind.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Connector %s [%s] has no notification kind %s. Its kinds: %s."
                                .formatted(connector.getName(), connector.getUuid(), kind,
                                        kinds.isEmpty() ? "none" : String.join(", ", kinds))));
    }

    static InstanceFields resolve(IlmApiClient client, String connectorUuid, String kind, Map<String, Object> values) {
        List<ConnectorAttribute> definitions = client.listNotificationFields(connectorUuid, kind);
        List<ConnectorAttribute> settable = new ArrayList<>();
        List<ConnectorAttribute> information = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        sort(definitions, settable, information);
        for (ConnectorAttribute group : definitions) {
            if ("group".equals(group.type()) && group.attributeCallback() != null) {
                Map<String, Object> request = callbackRequest(group, values, settable, unresolved);
                if (!request.isEmpty()) {
                    sort(client.resolveNotificationCallback(connectorUuid, kind, request), settable, information);
                }
            }
        }
        return new InstanceFields(kind, settable, information, unresolved);
    }

    List<ConnectorAttribute> settable() {
        return settable;
    }

    List<ConnectorAttribute> information() {
        return information;
    }

    List<String> unresolved() {
        return unresolved;
    }

    /**
     * The attribute content for the new instance: the given values, and the connector's defaults for fields without
     * one. Refuses unknown fields, missing required values and values the connector does not offer.
     */
    List<Field> content(Map<String, Object> values) {
        if (!unresolved.isEmpty()) {
            throw new IllegalArgumentException(
                    unresolved.get(0) + ". get_notification_connector_settings lists the choices.");
        }
        Set<String> known = settable.stream().map(ConnectorAttribute::name).collect(Collectors.toSet());
        List<String> unknown = values.keySet().stream().filter(name -> !known.contains(name)).toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown fields for kind %s: %s. get_notification_connector_settings "
                    .formatted(kind, String.join(", ", unknown)) + "lists them.");
        }
        List<Field> fields = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (ConnectorAttribute field : settable) {
            if (values.containsKey(field.name())) {
                fields
                        .add(new Field(field.uuid(), field.name(), field.contentType(),
                                content(field, values.get(field.name()))));
            } else if (hasCompleteDefault(field)) {
                fields.add(new Field(field.uuid(), field.name(), field.contentType(), List.copyOf(field.content())));
            } else if (field.required()) {
                missing.add(field.name() + " (" + field.label() + ")");
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Kind %s needs values for: %s.".formatted(kind, String.join(", ", missing)));
        }
        return fields;
    }

    private static List<Object> content(ConnectorAttribute field, Object value) {
        if (value == null) {
            throw new IllegalArgumentException(NO_VALUE.formatted(field.name()));
        }
        if (!FILLABLE.contains(field.contentType())) {
            throw new IllegalArgumentException("Field %s has content type %s, which this tool cannot fill."
                    .formatted(field.name(), field.contentType()));
        }
        if (field.list()) {
            return selections(field, value).stream().map(item -> (Object) option(field, item)).toList();
        }
        requireText(field, value);
        if (CODEBLOCK.equals(field.contentType())) {
            String code = Base64.getEncoder().encodeToString(((String) value).getBytes(StandardCharsets.UTF_8));
            return List.of(Map.of("data", Map.of("code", code, LANGUAGE, language(field))));
        }
        return List.of(Map.of("data", value));
    }

    /**
     * The language of a code-block field, as its default names it.
     */
    static Object language(ConnectorAttribute field) {
        return field.defaultData() instanceof Map<?, ?> data && data.get(LANGUAGE) != null
                ? data.get(LANGUAGE)
                : "plain";
    }

    private static boolean hasCompleteDefault(ConnectorAttribute field) {
        Object data = field.defaultData();
        return CODEBLOCK.equals(field.contentType())
                ? data instanceof Map<?, ?> code && code.get("code") != null
                : data != null;
    }

    /**
     * The values given for a list field: present, one unless the field takes several, and among its choices unless the
     * list is extensible.
     */
    private static List<?> selections(ConnectorAttribute field, Object value) {
        List<?> chosen = value instanceof List<?> items ? items : Collections.singletonList(value);
        if (chosen.isEmpty() || chosen.contains(null)) {
            throw new IllegalArgumentException(NO_VALUE.formatted(field.name()));
        }
        if (!field.multiSelect() && chosen.size() > 1) {
            throw new IllegalArgumentException("Field %s takes one value.".formatted(field.name()));
        }
        chosen.forEach(item -> {
            requireText(field, item);
            checkOption(field, item);
        });
        return chosen;
    }

    private static void requireText(ConnectorAttribute field, Object value) {
        if (TEXT.contains(field.contentType()) && !(value instanceof String)) {
            throw new IllegalArgumentException("Field %s takes plain text.".formatted(field.name()));
        }
    }

    private static void checkOption(ConnectorAttribute field, Object value) {
        if (!field.extensible() && !field.options().contains(value)) {
            throw new IllegalArgumentException("%s must be one of %s."
                    .formatted(field.name(),
                            field.options().stream().map(String::valueOf).collect(Collectors.joining(", "))));
        }
    }

    /**
     * A chosen value as the connector offers it, because ILM takes a choice only when it equals an offered item,
     * reference included. A value the connector does not offer, which only an extensible list takes, is plain data.
     */
    private static Map<String, Object> option(ConnectorAttribute field, Object value) {
        List<Map<String, Object>> offered = field.content() == null ? List.of() : field.content();
        return offered
                .stream()
                .filter(item -> value.equals(item.get("data")))
                .findFirst()
                .orElse(Map.of("data", value));
    }

    private static void sort(List<ConnectorAttribute> definitions, List<ConnectorAttribute> settable,
            List<ConnectorAttribute> information) {
        for (ConnectorAttribute definition : definitions == null ? List.<ConnectorAttribute>of() : definitions) {
            if ("data".equals(definition.type()) && !definition.readOnly()) {
                settable.add(definition);
            } else if ("info".equals(definition.type())) {
                information.add(definition);
            }
        }
    }

    /**
     * The callback request for a group of fields, or an empty map when a value it depends on is still missing.
     */
    private static Map<String, Object> callbackRequest(ConnectorAttribute group, Map<String, Object> values,
            List<ConnectorAttribute> settable, List<String> unresolved) {
        Map<String, Object> pathVariable = new LinkedHashMap<>();
        Map<String, Object> requestParameter = new LinkedHashMap<>();
        Map<String, Object> body = new LinkedHashMap<>();
        List<Mapping> mappings = group.attributeCallback().mappings();
        for (Mapping mapping : mappings == null ? List.<Mapping>of() : mappings) {
            Object value = mapping.value() != null ? mapping.value() : sourceValue(mapping.from(), values, settable);
            if (value == null) {
                unresolved.add(group.name() + " needs " + sourceName(mapping.from()));
                return Map.of();
            }
            for (String target : mapping.targets() == null ? List.<String>of() : mapping.targets()) {
                switch (target) {
                    case "pathVariable" -> pathVariable.put(mapping.to(), value);
                    case "requestParameter" -> requestParameter.put(mapping.to(), value);
                    default -> body.put(mapping.to(), value);
                }
            }
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("uuid", group.uuid());
        request.put("name", group.name());
        request.put("pathVariable", pathVariable);
        request.put("requestParameter", requestParameter);
        request.put("body", body);
        return request;
    }

    private static Object sourceValue(String from, Map<String, Object> values, List<ConnectorAttribute> settable) {
        String name = sourceName(from);
        ConnectorAttribute source = settable
                .stream()
                .filter(field -> field.name().equals(name))
                .findFirst()
                .orElse(null);
        if (!values.containsKey(name)) {
            return source == null ? null : source.defaultData();
        }
        Object value = values.get(name);
        if (source == null || !source.list()) {
            return value;
        }
        List<?> chosen = selections(source, value);
        return source.multiSelect() ? chosen : chosen.get(0);
    }

    private static String sourceName(String from) {
        if (from == null) {
            return "";
        }
        int dot = from.indexOf('.');
        return dot < 0 ? from : from.substring(0, dot);
    }
}
