package com.otilm.mcp.security;

import java.nio.file.Path;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SharedHttpsTrustTest {
    @Test
    void sharedClientTrustsAServerCertificateOnlyWhenItIsListed(@TempDir Path directory) throws Exception {
        try (HttpsFixture server = new HttpsFixture(directory, "private-ca")) {
            RestClient untrusting = client(outbound(""));
            RestClient.ResponseSpec untrusted = untrusting.get().uri(server.url()).retrieve();
            assertThatThrownBy(untrusted::toBodilessEntity)
                    .isInstanceOf(ResourceAccessException.class)
                    .hasCauseInstanceOf(SSLHandshakeException.class);

            RestClient trusting = client(outbound(server.pem()));
            assertThat(trusting.get().uri(server.url()).retrieve().toBodilessEntity().getStatusCode().value())
                    .isEqualTo(200);
        }
    }

    private static RestClient client(OutboundProperties outbound) {
        var factory = new SharedSecurityConfiguration.Enabled()
                .sharedHttpRequestFactory(new SharedSecurityProperties(), outbound);
        return RestClient.builder().requestFactory(factory).build();
    }

    private static OutboundProperties outbound(String pem) {
        return new OutboundProperties(pem, "", "", "");
    }
}
