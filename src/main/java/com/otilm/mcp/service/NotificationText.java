package com.otilm.mcp.service;

import com.otilm.api.model.client.notification.NotificationProfileDetailDto;
import com.otilm.api.model.common.NameAndUuidDto;
import com.otilm.api.model.core.notification.RecipientType;
import com.otilm.mcp.client.ConnectorAttribute;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.otilm.mcp.service.TextFormat.field;

/**
 * Plain-text explanations of notification profiles: who receives them, how they are sent, and how often.
 */
final class NotificationText {

    private static final String EXPIRING_ONLY = " per object (certificate_expiring only)";

    private NotificationText() {
    }

    static void profile(StringBuilder sb, NotificationProfileDetailDto profile) {
        sb
                .append("Notification profile: ")
                .append(profile.getName())
                .append(" [")
                .append(profile.getUuid())
                .append("] (version ")
                .append(profile.getVersion())
                .append(")\n");
        field(sb, "", "Description", profile.getDescription());
        field(sb, "", "Recipients", recipients(profile.getRecipientType(), profile.getRecipients()));
        field(sb, "", "Sends through",
                profile.getNotificationInstance() == null
                        ? null
                        : "notification instance " + label(profile.getNotificationInstance()));
        sb.append("In-app notification: ").append(profile.isInternalNotification() ? "yes" : "no").append("\n");
        if (profile.getFrequency() != null) {
            sb.append("Frequency: at most once every ").append(every(profile.getFrequency())).append(EXPIRING_ONLY);
            sb.append("\n");
        }
        if (profile.getRepetitions() != null) {
            sb.append("Repetitions: at most ").append(profile.getRepetitions()).append(EXPIRING_ONLY).append("\n");
        }
    }

    /**
     * What a new notification instance needs: its fields, the information the connector gives, the fields that wait for
     * other values, and the recipient attributes that can be mapped.
     */
    static void settings(StringBuilder sb, InstanceFields fields, List<ConnectorAttribute> mappings) {
        sb.append("Fields:\n");
        fields.settable().forEach(field -> sb.append(fieldLine(field)).append("\n"));
        if (!fields.information().isEmpty()) {
            sb.append("Information:\n");
            fields
                    .information()
                    .forEach(info -> sb
                            .append("- ")
                            .append(info.label())
                            .append(": ")
                            .append(info.defaultData())
                            .append("\n"));
        }
        if (!fields.unresolved().isEmpty()) {
            sb.append("Fields that depend on other values:\n");
            fields
                    .unresolved()
                    .forEach(
                            line -> sb.append("- ").append(line).append("; give it in attributes to see its fields\n"));
        }
        if (mappings != null && !mappings.isEmpty()) {
            sb.append("\nRecipient attributes that can be mapped to custom attributes:\n");
            mappings
                    .forEach(mapping -> sb
                            .append("- ")
                            .append(mapping.name())
                            .append(" | ")
                            .append(mapping.label())
                            .append(" (")
                            .append(mapping.contentType())
                            .append(mapping.list() ? ", list" : "")
                            .append(")\n"));
        }
    }

    private static String fieldLine(ConnectorAttribute field) {
        String line = "- " + field.name() + " | " + field.label() + " (" + field.contentType()
                + (field.required() ? ", required" : "") + ")";
        String hint = hint(field);
        return hint == null ? line : line + " — " + hint;
    }

    private static String hint(ConnectorAttribute field) {
        if (field.list()) {
            String options = field.options().stream().map(String::valueOf).collect(Collectors.joining(", "));
            if (field.extensible()) {
                return options.isEmpty() ? "Any value" : "Suggested: " + options + "; other values are allowed";
            }
            return "One of: " + options;
        }
        if (InstanceFields.CODEBLOCK.equals(field.contentType())) {
            boolean template = field.defaultData() instanceof Map<?, ?> code && code.get("code") != null;
            return template
                    ? "Default: a template (" + InstanceFields.language(field) + ")"
                    : "Give it as plain text (" + InstanceFields.language(field) + "); no default";
        }
        Object data = field.defaultData();
        return data == null || "secret".equals(field.contentType()) ? null : "Default: " + data;
    }

    /**
     * The recipients of a listed profile, which carries their count but not their names.
     */
    static String recipients(RecipientType type, int count) {
        return listed(type) ? who(type) + " (" + count + ")" : who(type);
    }

    private static String recipients(RecipientType type, List<NameAndUuidDto> recipients) {
        if (!listed(type)) {
            return who(type);
        }
        return who(type) + " " + recipients.stream().map(NotificationText::label).collect(Collectors.joining(", "));
    }

    private static boolean listed(RecipientType type) {
        return type == RecipientType.USER || type == RecipientType.GROUP || type == RecipientType.ROLE;
    }

    private static String who(RecipientType type) {
        return switch (type) {
            case NONE -> "none (the instance decides, for example a webhook)";
            case DEFAULT -> "the event's default recipients (for certificate events the owner and groups)";
            case USER -> "users";
            case GROUP -> "groups";
            case ROLE -> "roles";
            case OWNER -> "the object's owner";
            case OBJECT -> "addresses mapped from the object's custom attributes";
        };
    }

    private static String every(Duration frequency) {
        long hours = frequency.toHours();
        return hours % 24 == 0 ? count(hours / 24, "day") : count(hours, "hour");
    }

    private static String count(long amount, String unit) {
        return amount + " " + unit + (amount == 1 ? "" : "s");
    }

    private static String label(NameAndUuidDto reference) {
        return reference.getName() + " [" + reference.getUuid() + "]";
    }
}
