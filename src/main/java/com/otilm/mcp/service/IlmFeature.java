package com.otilm.mcp.service;

public enum IlmFeature {

    SIGNING_PROFILES("Signing profiles", 2, 18),
    TSP_PROFILES("TSP profiles", 2, 18),
    SIGNING_RECORDS("Signing records", 2, 19),
    SIGNING_STATISTICS("Signing statistics", 2, 19),
    CRYPTO_ASSETS("Cryptographic asset inventory", 2, 20),
    CBOM_ASSETS("CBOM asset listing", 2, 20),
    SETTINGS_REQUEST_ATTRIBUTES("Default request attribute settings", 2, 19),
    SETTINGS_CERTIFICATE_REGISTRATION("Certificate registration settings", 2, 19),
    SETTINGS_CBOM_SYNC("CBOM synchronization settings", 2, 20),
    SETTINGS_OAUTH_USERNAME_CLAIM("OAuth username claim", 2, 18),
    SETTINGS_OAUTH_DELEGATION("OAuth delegation settings", 2, 21),
    CONNECTOR_PROXY("Connector proxies", 2, 18),
    KEY_TRANSFER("Key import and export", 2, 20),
    TOKEN_PROVIDER_ATTRIBUTES("Provider-neutral token attributes", 2, 20),
    TOKEN_PROFILE_CALLBACKS("Token profile callbacks", 2, 19),
    CERTIFICATE_MANUAL_OPERATIONS("Manual certificate operations", 2, 18),
    CERTIFICATE_REGISTRATION("Certificate registration and capabilities", 2, 19),
    CERTIFICATE_PROVIDER_EXTENSIONS("Extended certificate provider attributes", 2, 20),
    CERTIFICATE_TRANSFER("Certificate import and keystore export", 2, 20),
    RA_REQUEST_ATTRIBUTES("RA request attribute configuration", 2, 19),
    ENROLLMENT_SCEP_CHALLENGE("SCEP challenge password configuration", 2, 19),
    ENROLLMENT_EXTENSIONS("Extended enrollment configuration", 2, 20),
    AUTHORITY_INTERFACES("Authority connector interfaces", 2, 19),
    TSP_AUTHENTICATION("TSP authentication and basic credentials", 2, 19),
    SIGNING_FORMATTING_EXTENSIONS("Extended signing formatting", 2, 20),
    TIME_QUALITY("Time quality configurations", 2, 18),
    TRUSTED_CERTIFICATES("Trusted certificates", 2, 18),
    SYSTEM_OIDS("System OID catalog", 2, 19),
    CERTIFICATE_EXTENSION_OIDS("Certificate extension OIDs", 2, 19),
    OID_VALUE_SCHEMA("OID value schemas", 2, 20),
    DISCOVERY_EXTENSIONS("Extended discovery management", 2, 20),
    WORKFLOW_RENAME("Trigger renaming", 2, 18),
    NOTIFICATION_EVENT_DATA("Notification event data categories", 2, 20);

    private final String label;
    private final PlatformVersion minimum;

    IlmFeature(String label, int major, int minor) {
        this.label = label;
        this.minimum = new PlatformVersion(major, minor);
    }

    public String label() {
        return label;
    }

    public PlatformVersion minimum() {
        return minimum;
    }
}
