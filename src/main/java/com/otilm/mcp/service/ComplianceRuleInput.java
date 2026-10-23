package com.otilm.mcp.service;

import java.util.Set;
import tools.jackson.databind.JsonNode;

/** Validates internal conditions against the resource's offered field/operator schema. */
final class ComplianceRuleInput {
    private static final String FIELD_IDENTIFIER = "fieldIdentifier";
    private static final String VALUE = "value";

    private ComplianceRuleInput() {
    }

    static void conditions(JsonNode conditions, JsonNode fields) {
        if (!conditions.isArray() || conditions.isEmpty()) {
            throw new IllegalArgumentException("An internal compliance rule needs at least one condition item.");
        }
        for (JsonNode condition : conditions) {
            if (!condition.isObject() || !Set
                    .of("fieldSource", FIELD_IDENTIFIER, "operator", VALUE)
                    .containsAll(condition.propertyNames())) {
                throw new IllegalArgumentException("Condition items contain unsupported fields.");
            }
            String source = condition.path("fieldSource").asString("");
            String identifier = condition.path(FIELD_IDENTIFIER).asString("");
            String operator = condition.path("operator").asString("");
            JsonNode offered = field(fields, source, identifier);
            boolean accepted = false;
            for (JsonNode candidate : offered.path("conditions")) {
                accepted |= operator.equals(candidate.asString());
            }
            if (!accepted) {
                throw new IllegalArgumentException("Condition operator is not offered for the chosen resource field.");
            }
            if (!Set.of("EMPTY", "NOT_EMPTY").contains(operator)) {
                if (!condition.hasNonNull(VALUE)) {
                    throw new IllegalArgumentException("Condition requires a value.");
                }
                validateValue(offered, condition.path(VALUE));
            }
        }
    }

    private static JsonNode field(JsonNode groups, String source, String identifier) {
        for (JsonNode group : groups) {
            if (source.equals(group.path("filterFieldSource").asString())) {
                for (JsonNode field : group.path("searchFieldData")) {
                    if (identifier.equals(field.path(FIELD_IDENTIFIER).asString())) {
                        return field;
                    }
                }
            }
        }
        throw new IllegalArgumentException("Condition field is not offered by the chosen resource.");
    }

    private static void validateValue(JsonNode field, JsonNode value) {
        if (value.isArray()) {
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Condition values cannot be empty.");
            }
            for (JsonNode item : value) {
                validateValue(field, item);
            }
            return;
        }
        boolean valid = switch (field.path("type").asString("")) {
            case "string", "date", "datetime" -> value.isString();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "list" -> value.isString() || value.isNumber() || value.isBoolean();
            default -> false;
        };
        if (!valid) {
            throw new IllegalArgumentException("Condition value has an incompatible field type.");
        }
    }
}
