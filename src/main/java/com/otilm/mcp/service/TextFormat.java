package com.otilm.mcp.service;

import com.otilm.api.model.client.attribute.ResponseAttribute;
import com.otilm.api.model.common.NameAndUuidDto;
import com.otilm.api.model.common.enums.IPlatformEnum;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public final class TextFormat {

    public static final String NOT_AVAILABLE = "N/A";

    private TextFormat() {
    }

    public static String orNa(Object value) {
        return value == null ? NOT_AVAILABLE : value.toString();
    }

    public static String code(IPlatformEnum value) {
        return value == null ? NOT_AVAILABLE : value.getCode();
    }

    public static void title(StringBuilder sb, String text) {
        sb.append(text).append("\n").append("=".repeat(text.length())).append("\n\n");
    }

    public static void item(StringBuilder sb, String name, Object uuid) {
        sb.append("- ").append(orNa(name)).append(" [").append(uuid).append("]\n");
    }

    public static <T> String titledList(String title, Collection<T> items, String emptyText,
            BiConsumer<StringBuilder, T> formatter) {
        StringBuilder sb = new StringBuilder();
        title(sb, title);
        appendEntries(sb, items, emptyText, formatter);
        return sb.toString();
    }

    public static <T> String pagedList(Number totalItems, String noun, Number pageNumber, Number totalPages,
            Collection<T> items, BiConsumer<StringBuilder, T> formatter) {
        StringBuilder sb = new StringBuilder();
        pageHeader(sb, totalItems, noun, pageNumber, totalPages);
        appendEntries(sb, items, "No " + noun + " found.", formatter);
        return sb.toString();
    }

    private static <T> void appendEntries(StringBuilder sb, Collection<T> items, String emptyText,
            BiConsumer<StringBuilder, T> formatter) {
        if (items == null || items.isEmpty()) {
            sb.append(emptyText).append("\n");
            return;
        }
        for (T entry : items) {
            formatter.accept(sb, entry);
            sb.append("\n");
        }
    }

    public static void field(StringBuilder sb, String indent, String label, Object value) {
        if (value == null || value.toString().isBlank()) {
            return;
        }
        sb.append(indent).append(label).append(": ").append(metadataValue(label, value.toString())).append("\n");
    }

    private static String metadataValue(String label, String value) {
        String key = label.toLowerCase(Locale.ROOT);
        if (!key.endsWith("url") && !key.endsWith("uri")) {
            return value;
        }
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null || uri.getScheme() == null
                    || !Set.of("https", "http").contains(uri.getScheme())) {
                return "URL omitted";
            }
            URI authority = new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), null, null, null);
            return URI.create(authority + uri.getRawPath()).toASCIIString();
        } catch (IllegalArgumentException | URISyntaxException invalid) {
            return "URL omitted";
        }
    }

    public static void pageHeader(StringBuilder sb, Number totalItems, String noun, Number pageNumber,
            Number totalPages) {
        sb
                .append("Found ")
                .append(totalItems)
                .append(" ")
                .append(noun)
                .append(" (page ")
                .append(pageNumber)
                .append(" of ")
                .append(totalPages)
                .append(")\n\n");
    }

    public static void counts(StringBuilder sb, String title, Map<String, Long> counts) {
        if (counts == null || counts.isEmpty()) {
            return;
        }
        sb.append("\n").append(title).append(":\n");
        counts.forEach((key, value) -> sb.append("  ").append(key).append(": ").append(value).append("\n"));
    }

    public static void customAttributes(StringBuilder sb, String indent, List<ResponseAttribute> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        sb.append("\n").append(indent).append("Custom Attributes:\n");
        // Detail responses omit protection metadata even when Core has decrypted scalar content.
        for (ResponseAttribute attribute : attributes) {
            sb.append(indent).append("  ").append(attribute.getName()).append(": value omitted\n");
        }
    }

    public static String pairs(Map<String, ?> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", "));
    }

    public static String nameOf(NameAndUuidDto reference) {
        return reference == null ? null : reference.getName();
    }

    public static String names(Collection<? extends NameAndUuidDto> items) {
        if (items == null) {
            return "";
        }
        return items.stream().map(NameAndUuidDto::getName).collect(Collectors.joining(", "));
    }
}
