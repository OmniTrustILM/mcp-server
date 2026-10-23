package com.otilm.mcp.service;

import com.otilm.api.model.client.notification.NotificationProfileRequestDto;
import com.otilm.api.model.core.notification.RecipientType;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reads what a client sends for a notification profile, with readable messages for anything ILM would reject.
 */
final class ProfileRequests {

    private static final Set<RecipientType> LISTED = EnumSet
            .of(RecipientType.USER, RecipientType.GROUP, RecipientType.ROLE);
    private static final Set<RecipientType> NOT_IN_APP = EnumSet
            .of(RecipientType.NONE, RecipientType.DEFAULT, RecipientType.OBJECT);
    private static final String FREQUENCY_INVALID = "frequency must be a positive ISO-8601 duration of days and hours, "
            + "for example P1D or PT12H.";

    private ProfileRequests() {
    }

    static NotificationProfileRequestDto request(NotificationProfileInput input) {
        if (isBlank(input.name())) {
            throw new IllegalArgumentException("A notification profile needs a name.");
        }
        RecipientType type = recipientType(input.recipientType());
        List<String> recipients = isBlank(input.recipients())
                ? List.of()
                : Arrays
                        .stream(input.recipients().split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .toList();
        if (LISTED.contains(type) && recipients.isEmpty()) {
            throw new IllegalArgumentException(
                    "Recipient type %s needs recipients: comma-separated user, group or ".formatted(type.getCode())
                            + "role UUIDs.");
        }
        if (!LISTED.contains(type) && !recipients.isEmpty()) {
            throw new IllegalArgumentException("Recipient type %s takes no recipients.".formatted(type.getCode()));
        }
        List<UUID> recipientUuids = recipients.stream().map(ProfileRequests::uuid).toList();
        UUID instance = isBlank(input.notificationInstanceUuid())
                ? null
                : uuid(input.notificationInstanceUuid().trim());
        boolean inApp = Boolean.TRUE.equals(input.internalNotification());
        if (inApp && NOT_IN_APP.contains(type)) {
            throw new IllegalArgumentException(
                    "In-app notifications cannot go to recipient type %s.".formatted(type.getCode()));
        }
        if (instance == null && !inApp) {
            throw new IllegalArgumentException(
                    "A notification profile needs a notificationInstanceUuid, internalNotification=true, or both.");
        }
        Duration frequency = frequency(input.frequency());
        if (input.repetitions() != null && input.repetitions() < 1) {
            throw new IllegalArgumentException("repetitions must be at least 1.");
        }
        NotificationProfileRequestDto request = new NotificationProfileRequestDto();
        request.setName(input.name().trim());
        request.setRecipientType(type);
        request.setRecipientUuids(recipientUuids);
        request.setNotificationInstanceUuid(instance);
        request.setInternalNotification(inApp);
        request.setFrequency(frequency);
        request.setRepetitions(input.repetitions());
        return request;
    }

    static boolean frequencyDefaulted(NotificationProfileInput input) {
        return isBlank(input.frequency());
    }

    private static RecipientType recipientType(String value) {
        String code = value == null ? "" : value.trim();
        return Arrays
                .stream(RecipientType.values())
                .filter(type -> type.getCode().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown recipient type '%s'. Valid types: %s."
                        .formatted(value,
                                Arrays
                                        .stream(RecipientType.values())
                                        .map(RecipientType::getCode)
                                        .collect(Collectors.joining(", ")))));
    }

    /**
     * The frequency given, or one day: certificate_expiring is raised every hour for each expiring certificate.
     */
    private static Duration frequency(String value) {
        if (isBlank(value)) {
            return Duration.ofDays(1);
        }
        Duration frequency;
        try {
            frequency = Duration.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(FREQUENCY_INVALID, e);
        }
        if (frequency.isNegative() || frequency.isZero() || frequency.toMinutesPart() != 0
                || frequency.toSecondsPart() != 0 || frequency.toNanosPart() != 0) {
            throw new IllegalArgumentException(FREQUENCY_INVALID);
        }
        return frequency;
    }

    private static UUID uuid(String value) {
        if (!Uuids.valid(value)) {
            throw new IllegalArgumentException("'%s' is not a UUID.".formatted(value));
        }
        return UUID.fromString(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
