package com.otilm.mcp.service;

public interface TrustService {
    String listTrustedCertificates();

    String getTrustedCertificate(String uuid);

    String addTrustedCertificate(String certificateContent);

    String deleteTrustedCertificate(String uuid);
}
