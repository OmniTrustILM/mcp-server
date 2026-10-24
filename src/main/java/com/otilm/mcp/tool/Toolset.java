package com.otilm.mcp.tool;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An area of tools the operator can switch on or off. Compatibility aggregates can overlap focused areas.
 */
public enum Toolset {

    CERTIFICATES("certificates", CertificateTool.class, CertificateOperationsTool.class),
    KEYS("keys", KeyTool.class, KeyManagementTool.class),
    INFRASTRUCTURE("infrastructure", InfrastructureTool.class, ConnectorInventoryTool.class,
            AuthorityInventoryTool.class, RaProfileInventoryTool.class, TokenInventoryTool.class,
            EntityInventoryTool.class, DiscoveryInventoryTool.class, GroupTool.class, ConnectorTool.class,
            ProxyTool.class, CredentialConfigurationTool.class, AuthorityTool.class, RaProfileTool.class,
            TokenTool.class, EntityTool.class, LocationTool.class, DiscoveryManagementTool.class, ComplianceTool.class),
    SECRETS("secrets", SecretTool.class, SecretOperationsTool.class),
    VAULTS("vaults", VaultTool.class, VaultConfigurationTool.class),
    CRYPTO_ASSETS("crypto-assets", CryptoAssetTool.class),
    CBOMS("cboms", CbomTool.class),
    SIGNING("signing", SigningTool.class, SigningConfigurationTool.class, TimestampConfigurationTool.class),
    WORKFLOWS("workflows", WorkflowTool.class, AutomationMaintenanceTool.class),
    ATTRIBUTES("attributes", AttributeTool.class, AttributeConfigurationTool.class, MetadataTool.class),
    USERS("users", UserTool.class, AdministrationTool.class, GroupTool.class),
    NOTIFICATIONS("notifications", NotificationTool.class, NotificationMaintenanceTool.class),
    CONNECTORS("connectors", ConnectorInventoryTool.class, ConnectorTool.class, ProxyTool.class),
    CREDENTIALS("credentials", CredentialConfigurationTool.class),
    AUTHORITIES("authorities", AuthorityInventoryTool.class, AuthorityTool.class),
    RA_PROFILES("ra-profiles", RaProfileInventoryTool.class, RaProfileTool.class),
    TOKENS("tokens", TokenInventoryTool.class, TokenTool.class),
    ENROLLMENT("enrollment", EnrollmentTool.class),
    ENTITIES("entities", EntityInventoryTool.class, EntityTool.class, LocationTool.class),
    APPROVALS("approvals", ApprovalTool.class),
    SCHEDULER("scheduler", SchedulerTool.class),
    SETTINGS("settings", SettingsTool.class),
    METADATA("metadata", MetadataTool.class),
    OIDS("oids", OidTool.class),
    TRUST("trust", TrustTool.class),
    DISCOVERY("discovery", DiscoveryInventoryTool.class, DiscoveryManagementTool.class,
            AutomationMaintenanceTool.class),
    COMPLIANCE("compliance", ComplianceTool.class);

    private static final String ALL = "all";

    private final String label;
    private final List<Class<?>> toolClasses;

    Toolset(String label, Class<?>... toolClasses) {
        this.label = label;
        this.toolClasses = List.of(toolClasses);
    }

    public String label() {
        return label;
    }

    public List<Class<?>> toolClasses() {
        return toolClasses;
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
