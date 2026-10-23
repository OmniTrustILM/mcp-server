package com.otilm.mcp.client;

import java.util.List;
import java.util.Map;

/**
 * A connector's attribute definition as ILM sends it. The interfaces' own attribute classes need Jackson 2 to be read,
 * so definitions and callback answers are read into this record instead.
 */
public record ConnectorAttribute(String uuid, String name, String type, String contentType, Properties properties,
        List<Map<String, Object>> content, Callback attributeCallback) {

    public record Properties(String label, Boolean required, Boolean list, Boolean multiSelect, Boolean readOnly,
            Boolean extensibleList) {
    }

    public record Callback(String callbackContext, String callbackMethod, List<Mapping> mappings) {
    }

    public record Mapping(String from, String to, List<String> targets, Object value) {
    }

    public String label() {
        return properties == null || properties.label() == null ? name : properties.label();
    }

    public boolean required() {
        return properties != null && Boolean.TRUE.equals(properties.required());
    }

    public boolean list() {
        return properties != null && Boolean.TRUE.equals(properties.list());
    }

    public boolean multiSelect() {
        return properties != null && Boolean.TRUE.equals(properties.multiSelect());
    }

    /**
     * Whether a list field also takes values outside its choices.
     */
    public boolean extensible() {
        return properties != null && Boolean.TRUE.equals(properties.extensibleList());
    }

    public boolean readOnly() {
        return properties != null && Boolean.TRUE.equals(properties.readOnly());
    }

    /**
     * The choices of a list field, which ILM sends as its content.
     */
    public List<Object> options() {
        return list() && content != null ? content.stream().map(item -> item.get("data")).toList() : List.of();
    }

    /**
     * The default value of a field that is not a list, or null when it has none.
     */
    public Object defaultData() {
        return list() || content == null || content.isEmpty() ? null : content.get(0).get("data");
    }
}
