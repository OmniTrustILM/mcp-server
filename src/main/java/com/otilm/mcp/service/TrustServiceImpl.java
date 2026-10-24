package com.otilm.mcp.service;

import com.otilm.mcp.client.ConfigurationApiClient;
import java.util.Base64;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

import static com.otilm.mcp.service.ConfigurationSupport.describe;
import static com.otilm.mcp.service.ConfigurationSupport.list;
import static com.otilm.mcp.service.ConfigurationSupport.run;
import static com.otilm.mcp.service.ConfigurationSupport.uuid;

@Service
public class TrustServiceImpl implements TrustService {
    private static final String TRUSTED_CERTIFICATE_ENDPOINT = "/v1/trustedCertificates/{uuid}";
    private static final String[] FIELDS = {
            "uuid",
            "subject",
            "issuer",
            "san",
            "serialNumber",
            "thumbprint",
            "notBefore",
            "notAfter"};
    private final ConfigurationApiClient api;
    private final PlatformVersionCheck versions;

    public TrustServiceImpl(ConfigurationApiClient api, PlatformVersionCheck versions) {
        this.api = api;
        this.versions = versions;
    }

    @Override
    public String listTrustedCertificates() {
        return execute(() -> list("Trusted certificates", api.get("/v1/trustedCertificates"), FIELDS));
    }

    @Override
    public String getTrustedCertificate(String uuid) {
        return execute(() -> describe("Trusted certificate",
                api.get(TRUSTED_CERTIFICATE_ENDPOINT, uuid(uuid, "Trusted certificate UUID")), FIELDS));
    }

    @Override
    public String addTrustedCertificate(String certificateContent) {
        return execute(() -> {
            try {
                if (certificateContent == null || certificateContent.isBlank()
                        || certificateContent.length() > 7_000_000) {
                    throw new IllegalArgumentException();
                }
                if (Base64.getDecoder().decode(certificateContent).length > 5 * 1024 * 1024) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("Certificate content must be Base64 containing at most 5 MiB.");
            }
            return describe("Trusted certificate added", api
                    .post("/v1/trustedCertificates",
                            AdministrationInput.JSON.createObjectNode().put("certificateContent", certificateContent)),
                    FIELDS);
        });
    }

    @Override
    public String deleteTrustedCertificate(String uuid) {
        return execute(() -> {
            api.delete(TRUSTED_CERTIFICATE_ENDPOINT, uuid(uuid, "Trusted certificate UUID"));
            return "Trusted certificate deleted.";
        });
    }

    private String execute(Supplier<String> operation) {
        return run("managing trusted certificates",
                () -> versions.unsupported(IlmFeature.TRUSTED_CERTIFICATES).orElseGet(operation));
    }
}
