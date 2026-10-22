package com.otilm.mcp.service;

import com.otilm.api.model.core.other.ResourceEvent;
import com.otilm.api.model.core.search.SearchFieldDataByGroupDto;
import com.otilm.api.model.core.search.SearchFieldDataDto;
import com.otilm.api.model.core.settings.EventsSettingsDto;
import com.otilm.api.model.core.workflows.ConditionItemDto;
import com.otilm.api.model.core.workflows.ExecutionItemDto;
import com.otilm.api.model.core.workflows.TriggerDetailDto;
import com.otilm.api.model.core.workflows.TriggerDto;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.otilm.mcp.service.TextFormat.code;
import static com.otilm.mcp.service.TextFormat.orNa;

/**
 * Plain-text explanations of workflow triggers: what fires them, what must hold, and what they do.
 */
public final class WorkflowText {

    public static final String PLATFORM_NOTE = "Discoveries, RA profiles and groups can carry their own triggers, which are not shown here.";

    private static final String ITEM = "  - ";
    private static final int SHOWN_VALUES = 10;

    private WorkflowText() {
    }

    public static String runAsNote(ResourceEvent event) {
        return "ILM re-saved every platform trigger of " + event.getCode()
                + " to run as the identity that made this change.";
    }

    /**
     * Writes fields by source, each with its type, optionally its operators, and at most ten of its possible values.
     */
    static void fields(StringBuilder sb, List<SearchFieldDataByGroupDto> groups, boolean operators) {
        if (groups == null || groups.isEmpty()) {
            sb.append("  none\n");
            return;
        }
        for (SearchFieldDataByGroupDto group : groups) {
            sb.append("Source: ").append(group.getFilterFieldSource().getCode()).append("\n");
            stream(group.getSearchFieldData()).forEach(field -> fieldLine(sb, field, operators));
        }
    }

    private static void fieldLine(StringBuilder sb, SearchFieldDataDto field, boolean operators) {
        sb.append(ITEM).append(field.getFieldIdentifier()).append(" | ").append(field.getFieldLabel());
        sb.append(" (").append(field.getType().name()).append(")");
        if (operators && field.getConditions() != null && !field.getConditions().isEmpty()) {
            sb.append(" — Operators: ");
            sb.append(field.getConditions().stream().map(Enum::name).collect(Collectors.joining(", ")));
        }
        if (field.getValue() instanceof List<?> options && !options.isEmpty()) {
            sb.append(" — Values: ").append(options(options));
        }
        sb.append("\n");
    }

    private static String options(List<?> options) {
        String shown = options.stream().limit(SHOWN_VALUES).map(WorkflowText::option).collect(Collectors.joining(", "));
        int more = options.size() - SHOWN_VALUES;
        return more > 0 ? shown + ", and " + more + " more" : shown;
    }

    private static String option(Object option) {
        return option instanceof Map<?, ?> object && object.containsKey("uuid")
                ? object.get("name") + " [" + object.get("uuid") + "]"
                : String.valueOf(option);
    }

    public static String eventLine(TriggerDto trigger) {
        String event = trigger.getEvent() == null ? "none" : trigger.getEvent().getCode();
        return event + " (" + code(trigger.getResource()) + ")";
    }

    public static void trigger(StringBuilder sb, TriggerDetailDto trigger) {
        sb.append("Trigger: ").append(trigger.getName()).append(" [").append(trigger.getUuid()).append("]\n");
        sb.append("Event: ").append(eventLine(trigger)).append("\n");
        TextFormat.field(sb, "", "Description", trigger.getDescription());
        conditions(sb, trigger);
        outcome(sb, trigger);
    }

    public static String attachment(TriggerDto trigger, EventsSettingsDto settings) {
        if (trigger.getEvent() == null) {
            return "Platform attachment: none (no event)";
        }
        List<UUID> attached = eventTriggers(settings, trigger.getEvent());
        int position = attached.indexOf(UUID.fromString(trigger.getUuid()));
        String event = trigger.getEvent().getCode();
        return position < 0
                ? "Platform attachment: not attached to " + event + " platform-wide"
                : "Platform attachment: attached to %s (position %d of %d)"
                        .formatted(event, position + 1, attached.size());
    }

    static List<UUID> eventTriggers(EventsSettingsDto settings, ResourceEvent event) {
        if (settings == null || settings.getEventsMapping() == null) {
            return List.of();
        }
        return settings.getEventsMapping().getOrDefault(event, List.of());
    }

    private static void conditions(StringBuilder sb, TriggerDetailDto trigger) {
        if (trigger.getRules() == null || trigger.getRules().isEmpty()) {
            sb.append("When: every ").append(code(trigger.getResource())).append(" object of this event\n");
            return;
        }
        sb.append("When (all must hold):\n");
        trigger
                .getRules()
                .stream()
                .flatMap(rule -> stream(rule.getConditions()))
                .flatMap(condition -> stream(condition.getItems()))
                .forEach(item -> condition(sb, item));
    }

    private static void condition(StringBuilder sb, ConditionItemDto item) {
        sb
                .append(ITEM)
                .append(code(item.getFieldSource()))
                .append(" ")
                .append(item.getFieldIdentifier())
                .append(" ")
                .append(code(item.getOperator()));
        if (item.getValue() != null) {
            sb.append(" ").append(value(item.getValue()));
        }
        sb.append("\n");
    }

    private static void outcome(StringBuilder sb, TriggerDetailDto trigger) {
        if (trigger.isIgnoreTrigger()) {
            sb.append("Then: ").append(ignored(trigger.getEvent())).append(" (ignore trigger)\n");
            return;
        }
        sb.append("Then:\n");
        stream(trigger.getActions())
                .flatMap(action -> stream(action.getExecutions()))
                .flatMap(execution -> stream(execution.getItems()))
                .forEach(item -> sb.append(ITEM).append(execution(item)).append("\n"));
    }

    private static String ignored(ResourceEvent event) {
        if (event == ResourceEvent.CERTIFICATE_DISCOVERED) {
            return "skip the certificate, so discovery does not import it";
        }
        if (event == ResourceEvent.CERTIFICATE_UPLOADED) {
            return "skip the certificate, so the upload is not stored";
        }
        return "ignore the object, so the triggers attached alongside it do not run for it";
    }

    private static String execution(ExecutionItemDto item) {
        if (item.getNotificationProfileUuid() != null) {
            return "notify through profile %s [%s]"
                    .formatted(orNa(item.getNotificationProfileName()), item.getNotificationProfileUuid());
        }
        String target = code(item.getFieldSource()) + " " + item.getFieldIdentifier();
        if (item.getSourceFieldSource() != null) {
            return "set " + target + " from " + code(item.getSourceFieldSource()) + " "
                    + item.getSourceFieldIdentifier();
        }
        return "set " + target + " to " + value(item.getData());
    }

    private static String value(Object value) {
        if (value instanceof Collection<?> values) {
            return values.stream().map(String::valueOf).collect(Collectors.joining(", "));
        }
        return String.valueOf(value);
    }

    private static <T> Stream<T> stream(List<T> items) {
        return items == null ? Stream.empty() : items.stream();
    }
}
