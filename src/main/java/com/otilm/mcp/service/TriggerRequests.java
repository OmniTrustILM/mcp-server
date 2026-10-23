package com.otilm.mcp.service;

import com.otilm.api.model.client.certificate.SearchFilterRequestDto;
import com.otilm.api.model.core.other.ResourceEvent;
import com.otilm.api.model.core.search.FilterConditionOperator;
import com.otilm.api.model.core.search.FilterFieldSource;
import com.otilm.api.model.core.search.SearchFieldDataByGroupDto;
import com.otilm.api.model.core.search.SearchFieldDataDto;
import com.otilm.api.model.core.workflows.ConditionItemRequestDto;
import com.otilm.api.model.core.workflows.ExecutionItemRequestDto;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import static com.otilm.mcp.service.TextFormat.code;

/**
 * Reads what a client sends for a trigger, with readable messages for anything ILM would reject.
 */
public final class TriggerRequests {

    private static final JsonMapper JSON_MAPPER = JsonMapper
            .builder()
            .findAndAddModules()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private TriggerRequests() {
    }

    /**
     * A trigger as checked and ready to send: condition items, set-field items, and what else it does.
     */
    public record TriggerPlan(String name, ResourceEvent event, List<ConditionItemRequestDto> conditions,
            List<ExecutionItemRequestDto> setFields, String notificationProfileUuid, boolean ignore, boolean attach) {
    }

    record SetField(FilterFieldSource fieldSource, String fieldIdentifier, Object value) {
    }

    public static TriggerPlan plan(TriggerInput input) {
        if (isBlank(input.name())) {
            throw new IllegalArgumentException("A trigger needs a name.");
        }
        ResourceEvent event = event(input.event());
        List<ConditionItemRequestDto> conditions = SearchFilterParser
                .parseFilters(input.conditions())
                .stream()
                .map(TriggerRequests::conditionItem)
                .toList();
        List<ExecutionItemRequestDto> setFields = setFields(input.setFields());
        String profile = isBlank(input.notificationProfileUuid()) ? null : input.notificationProfileUuid().trim();
        boolean ignore = Boolean.TRUE.equals(input.ignore());
        if (ignore && (!setFields.isEmpty() || profile != null)) {
            throw new IllegalArgumentException("An ignore trigger cannot also set fields or send notifications.");
        }
        if (ignore && conditions.isEmpty()) {
            throw new IllegalArgumentException(
                    "An ignore trigger needs at least one condition; without one it would ignore every object of the event.");
        }
        if (!ignore && setFields.isEmpty() && profile == null) {
            throw new IllegalArgumentException(
                    "A trigger needs something to do: setFields, notificationProfileUuid, or ignore=true.");
        }
        return new TriggerPlan(input.name().trim(), event, conditions, setFields, profileUuid(profile), ignore,
                !Boolean.FALSE.equals(input.attach()));
    }

    private static String profileUuid(String profile) {
        if (profile != null && !Uuids.valid(profile)) {
            throw new IllegalArgumentException("notificationProfileUuid '%s' is not a UUID.".formatted(profile));
        }
        return profile;
    }

    public static ResourceEvent event(String value) {
        if (isBlank(value)) {
            throw new IllegalArgumentException("Missing event. Valid events: " + eventCodes() + ".");
        }
        String code = value.trim().toLowerCase(Locale.ROOT);
        return Arrays
                .stream(ResourceEvent.values())
                .filter(event -> event.getCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown event '%s'. Valid events: %s.".formatted(value, eventCodes())));
    }

    /**
     * Refuses conditions on fields, or with operators, that ILM does not list for rule conditions on the trigger's
     * resource.
     */
    public static void checkConditionFields(List<ConditionItemRequestDto> conditions,
            List<SearchFieldDataByGroupDto> fields) {
        Map<String, SearchFieldDataDto> listed = listed(fields);
        refuseUnlisted(conditions.stream().map(TriggerRequests::conditionName), listed,
                "Conditions cannot check %s; list_trigger_fields lists the fields they can check.");
        List<String> unsupported = conditions
                .stream()
                .filter(item -> !takes(listed.get(conditionName(item)), item.getOperator()))
                .map(item -> item.getOperator().name() + " on " + conditionName(item))
                .distinct()
                .toList();
        if (!unsupported.isEmpty()) {
            throw new IllegalArgumentException(
                    "Conditions cannot use %s; list_trigger_fields lists the operators each field takes."
                            .formatted(String.join(", ", unsupported)));
        }
    }

    /**
     * Refuses setFields entries for fields that ILM does not list as settable on the trigger's resource.
     */
    public static void checkSetFields(List<ExecutionItemRequestDto> setFields, List<SearchFieldDataByGroupDto> fields) {
        refuseUnlisted(setFields.stream().map(item -> fieldName(item.getFieldSource(), item.getFieldIdentifier())),
                listed(fields), "setFields cannot set %s; list_trigger_fields lists the fields it can set.");
    }

    private static Map<String, SearchFieldDataDto> listed(List<SearchFieldDataByGroupDto> fields) {
        Map<String, SearchFieldDataDto> listed = new HashMap<>();
        for (SearchFieldDataByGroupDto group : Objects
                .requireNonNullElse(fields, List.<SearchFieldDataByGroupDto>of())) {
            for (SearchFieldDataDto field : Objects
                    .requireNonNullElse(group.getSearchFieldData(), List.<SearchFieldDataDto>of())) {
                listed.put(fieldName(group.getFilterFieldSource(), field.getFieldIdentifier()), field);
            }
        }
        return listed;
    }

    private static void refuseUnlisted(Stream<String> requested, Map<String, SearchFieldDataDto> listed,
            String refusal) {
        List<String> unlisted = requested.filter(name -> !listed.containsKey(name)).distinct().toList();
        if (!unlisted.isEmpty()) {
            throw new IllegalArgumentException(refusal.formatted(String.join(", ", unlisted)));
        }
    }

    private static boolean takes(SearchFieldDataDto field, FilterConditionOperator operator) {
        List<FilterConditionOperator> operators = field.getConditions();
        return operators != null && operators.contains(operator);
    }

    private static String conditionName(ConditionItemRequestDto item) {
        return fieldName(item.getFieldSource(), item.getFieldIdentifier());
    }

    private static String fieldName(FilterFieldSource source, String identifier) {
        return code(source) + " " + identifier;
    }

    private static String eventCodes() {
        return Arrays.stream(ResourceEvent.values()).map(ResourceEvent::getCode).collect(Collectors.joining(", "));
    }

    private static ConditionItemRequestDto conditionItem(SearchFilterRequestDto filter) {
        if (filter == null || filter.getFieldSource() == null || isBlank(filter.getFieldIdentifier())
                || filter.getCondition() == null) {
            throw new IllegalArgumentException("Each condition needs a fieldSource, a fieldIdentifier and a condition; "
                    + "list_trigger_fields lists the fields.");
        }
        ConditionItemRequestDto item = new ConditionItemRequestDto();
        item.setFieldSource(filter.getFieldSource());
        item.setFieldIdentifier(filter.getFieldIdentifier());
        item.setOperator(filter.getCondition());
        item.setValue(filter.getValue());
        return item;
    }

    private static List<ExecutionItemRequestDto> setFields(String json) {
        if (isBlank(json)) {
            return List.of();
        }
        List<SetField> fields;
        try {
            fields = JSON_MAPPER.readValue(json, new TypeReference<>() {
            });
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Invalid setFields JSON. Expected a JSON array of objects with "
                    + "fieldSource (property or custom), fieldIdentifier and value. Example: "
                    + "[{\"fieldSource\":\"property\",\"fieldIdentifier\":\"GROUP_NAME\",\"value\":[\"<group uuid>\"]}]. "
                    + "Error: " + e.getOriginalMessage(), e);
        }
        return fields == null ? List.of() : fields.stream().map(TriggerRequests::setFieldItem).toList();
    }

    private static ExecutionItemRequestDto setFieldItem(SetField field) {
        if (field == null) {
            throw new IllegalArgumentException(
                    "Each setFields entry needs a fieldSource, a fieldIdentifier and a value.");
        }
        if (field.fieldSource() != FilterFieldSource.PROPERTY && field.fieldSource() != FilterFieldSource.CUSTOM) {
            throw new IllegalArgumentException(
                    "setFields can only set property or custom fields, not " + code(field.fieldSource()) + ".");
        }
        if (isBlank(field.fieldIdentifier())) {
            throw new IllegalArgumentException("Each setFields entry needs a fieldIdentifier.");
        }
        if (field.value() == null) {
            throw new IllegalArgumentException(
                    "Each setFields entry needs a value; to clear a field, give an empty list [].");
        }
        ExecutionItemRequestDto item = new ExecutionItemRequestDto();
        item.setFieldSource(field.fieldSource());
        item.setFieldIdentifier(field.fieldIdentifier());
        item.setData((Serializable) field.value());
        return item;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
