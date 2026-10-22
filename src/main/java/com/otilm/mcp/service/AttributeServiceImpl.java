package com.otilm.mcp.service;

import com.otilm.api.model.client.attribute.custom.CustomAttributeCreateRequestDto;
import com.otilm.api.model.client.attribute.custom.CustomAttributeDefinitionDetailDto;
import com.otilm.api.model.client.attribute.custom.CustomAttributeDefinitionDto;
import com.otilm.api.model.common.attribute.common.content.AttributeContentType;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.mcp.client.IlmApiClient;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

import static com.otilm.mcp.service.TextFormat.code;
import static com.otilm.mcp.service.TextFormat.field;
import static com.otilm.mcp.service.TextFormat.item;
import static com.otilm.mcp.service.TextFormat.titledList;

@Service
public class AttributeServiceImpl implements AttributeService {

    private static final String INDENT = "  ";

    private final IlmApiClient ilmApiClient;

    public AttributeServiceImpl(IlmApiClient ilmApiClient) {
        this.ilmApiClient = ilmApiClient;
    }

    @Override
    public String listCustomAttributes() {
        return ToolResponses.run("listing custom attributes", () -> {
            List<CustomAttributeDefinitionDto> attributes = ilmApiClient.listCustomAttributes();
            return titledList("Custom Attributes (" + attributes.size() + ")", attributes,
                    "No custom attributes found.", (sb, attribute) -> {
                        item(sb, attribute.getName(), attribute.getUuid());
                        field(sb, INDENT, "Label", attribute.getLabel());
                        field(sb, INDENT, "Content Type", code(attribute.getContentType()));
                        field(sb, INDENT, "Resources", resourceCodes(attribute.getResources()));
                        field(sb, INDENT, "Enabled", attribute.getEnabled());
                        if (attribute.getContentType() != null) {
                            field(sb, INDENT, "Field Identifier",
                                    attribute.getName() + "|" + attribute.getContentType().name());
                        }
                        field(sb, INDENT, "Description", attribute.getDescription());
                    });
        });
    }

    @Override
    public String createCustomAttribute(String name, String label, String contentType, String resources,
            String description) {
        return ToolResponses.run("creating custom attribute", () -> {
            if (isBlank(name) || isBlank(label)) {
                throw new IllegalArgumentException("A custom attribute needs a name and a label.");
            }
            AttributeContentType type = contentType(contentType);
            List<Resource> targets = resources(resources);
            CustomAttributeCreateRequestDto request = new CustomAttributeCreateRequestDto();
            request.setName(name.trim());
            request.setLabel(label.trim());
            request.setContentType(type);
            request.setResources(targets);
            request.setDescription(description);
            request.setVisible(true);
            CustomAttributeDefinitionDetailDto created = ilmApiClient.createCustomAttribute(request);
            return "Created custom attribute %s [%s] (%s) for %s. Refer to it as %s|%s in conditions and setFields."
                    .formatted(request.getName(), created.getUuid(), type.getCode(), resourceCodes(targets),
                            request.getName(), type.name());
        });
    }

    private static AttributeContentType contentType(String value) {
        String code = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return Arrays
                .stream(AttributeContentType.values())
                .filter(type -> type.getCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown content type '%s'. Valid content types: %s."
                        .formatted(value,
                                Arrays
                                        .stream(AttributeContentType.values())
                                        .map(AttributeContentType::getCode)
                                        .collect(Collectors.joining(", ")))));
    }

    private static List<Resource> resources(String value) {
        return Arrays
                .stream((value == null ? "" : value).split(","))
                .map(String::trim)
                .map(code -> Arrays
                        .stream(Resource.values())
                        .filter(resource -> resource.getCode().equalsIgnoreCase(code))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Unknown resource '" + code + "'.")))
                .toList();
    }

    private static String resourceCodes(List<Resource> resources) {
        return resources == null ? null : resources.stream().map(Resource::getCode).collect(Collectors.joining(", "));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
