package com.otilm.mcp.tool;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An area of tools the operator can switch on or off. Each tool class is one toolset.
 */
public enum Toolset {

    CERTIFICATES("certificates", CertificateTool.class),
    KEYS("keys", KeyTool.class),
    INFRASTRUCTURE("infrastructure", InfrastructureTool.class),
    SECRETS("secrets", SecretTool.class),
    VAULTS("vaults", VaultTool.class),
    CRYPTO_ASSETS("crypto-assets", CryptoAssetTool.class),
    CBOMS("cboms", CbomTool.class),
    SIGNING("signing", SigningTool.class);

    private static final String ALL = "all";

    private final String label;
    private final Class<?> toolClass;

    Toolset(String label, Class<?> toolClass) {
        this.label = label;
        this.toolClass = toolClass;
    }

    public String label() {
        return label;
    }

    public Class<?> toolClass() {
        return toolClass;
    }

    public static Set<Toolset> parse(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw unknown("");
        }
        Set<Toolset> toolsets = EnumSet.noneOf(Toolset.class);
        boolean all = false;
        for (String value : values) {
            String normalized = value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
            if (ALL.equals(normalized)) {
                all = true;
            } else {
                toolsets
                        .add(Arrays
                                .stream(values())
                                .filter(toolset -> toolset.label.equals(normalized))
                                .findFirst()
                                .orElseThrow(() -> unknown(value)));
            }
        }
        return all ? EnumSet.allOf(Toolset.class) : toolsets;
    }

    private static IllegalArgumentException unknown(String value) {
        return new IllegalArgumentException(
                "Unknown toolset '%s' in ilm.mcp.toolsets (ILM_MCP_TOOLSETS). Valid values: %s, %s."
                        .formatted(value, ALL, labels()));
    }

    private static String labels() {
        return Arrays.stream(values()).map(Toolset::label).collect(Collectors.joining(", "));
    }
}
