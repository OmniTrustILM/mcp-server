package com.otilm.mcp.service;

public enum IlmFeature {

    SIGNING_PROFILES("Signing profiles", 2, 18),
    TSP_PROFILES("TSP profiles", 2, 18),
    SIGNING_RECORDS("Signing records", 2, 19),
    SIGNING_STATISTICS("Signing statistics", 2, 19),
    CRYPTO_ASSETS("Cryptographic asset inventory", 2, 20),
    CBOM_ASSETS("CBOM asset listing", 2, 20);

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
