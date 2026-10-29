package com.otilm.mcp.security;

import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class OutboundTrustTest {
    private static final String SENTINEL = "MustNotLeak";
    private static final String SKIPPED = "TRUSTED_CERTIFICATES: skipped 1 certificate block that could not be read";
    private static final String NOTHING_ADDED = "TRUSTED_CERTIFICATES holds no readable certificate, "
            + "so nothing is added";

    @FunctionalInterface
    private interface Body {
        void run() throws Exception;
    }

    // The default context presents a client keystore set through javax.net.ssl.keyStore. A context built here would
    // not, so a client with nothing to add has to keep the default one.
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " \n")
    void trustsDefaultAnchorsWhenEmpty(String pem, CapturedOutput output) throws Exception {
        try (Warnings warnings = new Warnings(OutboundTrust.class)) {
            assertThat(OutboundTrust.sslContext(pem)).isSameAs(SSLContext.getDefault());
            assertThat(warnings.messages()).isEmpty();
        }
        assertThat(output).doesNotContain("Trusting");
    }

    // An SSLContext does not expose its trust managers, so the JDK default trust store is pointed at a store holding
    // one server certificate, and handshakes show that the context keeps it next to the added certificate.
    @Test
    void keepsDefaultAnchorsWhenAddingCertificates(@TempDir Path directory, CapturedOutput output) throws Exception {
        try (HttpsFixture anchored = new HttpsFixture(directory, "anchored");
                HttpsFixture added = new HttpsFixture(directory, "added")) {
            withDefaultTrustStore(anchored, directory, () -> {
                SSLContext context = OutboundTrust.sslContext(added.pem());
                assertThat(anchored.status(context)).isEqualTo(200);
                assertThat(added.status(context)).isEqualTo(200);
            });
        }
        assertThat(output).contains("Trusting 1 additional certificate").doesNotContain("1 additional certificates");
    }

    @Test
    void addsEveryPemCertificate(@TempDir Path directory, CapturedOutput output) throws Exception {
        try (HttpsFixture first = new HttpsFixture(directory, "first");
                HttpsFixture second = new HttpsFixture(directory, "second");
                HttpsFixture unlisted = new HttpsFixture(directory, "unlisted")) {
            SSLContext context = OutboundTrust.sslContext(first.pem() + second.pem());
            assertThat(first.status(context)).isEqualTo(200);
            assertThat(second.status(context)).isEqualTo(200);
            assertThatThrownBy(() -> unlisted.status(context)).isInstanceOf(SSLHandshakeException.class);
        }
        assertThat(output).contains("Trusting 2 additional certificates").doesNotContain("BEGIN CERTIFICATE");
    }

    // ILM Core reads the certificate blocks of the value and nothing else, so a bundle that also carries a key or a
    // revocation list works, and neither is used.
    @Test
    void ignoresKeysAndRevocationListsNextToCertificates(@TempDir Path directory, CapturedOutput output)
            throws Exception {
        try (HttpsFixture server = new HttpsFixture(directory, "bundled");
                Warnings warnings = new Warnings(OutboundTrust.class)) {
            byte[] key = KeyPairGenerator.getInstance("EC").generateKeyPair().getPrivate().getEncoded();
            String bundle = block("PRIVATE KEY", Base64.getEncoder().encodeToString(key)) + server.pem()
                    + block("X509 CRL", SENTINEL);

            SSLContext context = OutboundTrust.sslContext(bundle);

            assertThat(server.status(context)).isEqualTo(200);
            assertThat(warnings.messages()).isEmpty();
        }
        assertThat(output).contains("Trusting 1 additional certificate").doesNotContain(SENTINEL, "-----BEGIN");
    }

    @ParameterizedTest
    @MethodSource("unreadableBlocks")
    void skipsAnUnreadableBlockAndTrustsTheOthers(String unreadable, @TempDir Path directory, CapturedOutput output)
            throws Exception {
        try (HttpsFixture server = new HttpsFixture(directory, "readable")) {
            for (String bundle : List.of(unreadable + server.pem(), server.pem() + unreadable)) {
                try (Warnings warnings = new Warnings(OutboundTrust.class)) {
                    SSLContext context = OutboundTrust.sslContext(bundle);

                    assertThat(server.status(context)).isEqualTo(200);
                    assertThat(warnings.messages()).containsExactly(SKIPPED);
                }
            }
        }
        assertThat(output).contains(SKIPPED).doesNotContain(SENTINEL, "-----BEGIN");
    }

    @Test
    void addsNothingWhenEveryBlockIsUnreadable(CapturedOutput output) throws Exception {
        try (Warnings warnings = new Warnings(OutboundTrust.class)) {
            SSLContext context = OutboundTrust.sslContext(String.join("", unreadableBlocks().toList()));

            assertThat(context).isSameAs(SSLContext.getDefault());
            assertThat(warnings.messages())
                    .containsExactly("TRUSTED_CERTIFICATES: skipped 2 certificate blocks that could not be read",
                            NOTHING_ADDED);
        }
        assertThat(output).doesNotContain("Trusting", SENTINEL, "-----BEGIN");
    }

    // The first value is plain text, the second holds only a key, and the third has no end marker.
    @ParameterizedTest
    @ValueSource(strings = {
            SENTINEL,
            "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----\n",
            "-----BEGIN CERTIFICATE-----\nAAAA\n"})
    void addsNothingWhenTheValueHasNoCertificateBlock(String value, CapturedOutput output) throws Exception {
        try (Warnings warnings = new Warnings(OutboundTrust.class)) {
            assertThat(OutboundTrust.sslContext(value)).isSameAs(SSLContext.getDefault());
            assertThat(warnings.messages()).containsExactly(NOTHING_ADDED);
        }
        assertThat(output).contains(NOTHING_ADDED).doesNotContain("Trusting", SENTINEL, "-----BEGIN");
    }

    // Separator lines of hyphens, common in annotated bundles, look like a PEM header to the JDK's reader, which then
    // fails on the whole value.
    @Test
    void trustsEveryCertificateAmongComments(@TempDir Path directory) throws Exception {
        try (HttpsFixture first = new HttpsFixture(directory, "first");
                HttpsFixture second = new HttpsFixture(directory, "second");
                Warnings warnings = new Warnings(OutboundTrust.class)) {
            String bundle = "# Roots of the corporate proxy\n----- First root -----\n" + first.pem()
                    + "\nSubject: CN=second\n----- Second root -----\n" + second.pem() + "end of bundle\n";

            SSLContext context = OutboundTrust.sslContext(bundle);

            assertThat(first.status(context)).isEqualTo(200);
            assertThat(second.status(context)).isEqualTo(200);
            assertThat(warnings.messages()).isEmpty();
        }
    }

    // ILM Core strips the markers and the line breaks and then decodes, so it accepts a block on one line. The JDK's
    // reader needs a line break after the header.
    @Test
    void trustsABlockWrittenOnOneLine(@TempDir Path directory) throws Exception {
        try (HttpsFixture server = new HttpsFixture(directory, "single-line");
                Warnings warnings = new Warnings(OutboundTrust.class)) {
            String oneLine = server.pem().replaceAll("\\R", "");

            SSLContext context = OutboundTrust.sslContext(oneLine);

            assertThat(server.status(context)).isEqualTo(200);
            assertThat(warnings.messages()).isEmpty();
        }
    }

    /** A block whose base64 is bad and one whose DER is bad. Both hold the sentinel in clear text. */
    private static Stream<String> unreadableBlocks() {
        return Stream.of(block("CERTIFICATE", "!" + SENTINEL), block("CERTIFICATE", SENTINEL));
    }

    private static String block(String type, String body) {
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
    }

    /** Runs the body with the JDK's default trust store replaced by one that holds only the server's certificate. */
    private static void withDefaultTrustStore(HttpsFixture server, Path directory, Body body) throws Exception {
        String store = server.trustStore(directory.resolve("default-anchors.p12")).toString();
        Map<String, String> settings = new HashMap<>();
        settings.put("javax.net.ssl.trustStore", store);
        settings.put("javax.net.ssl.trustStoreType", "PKCS12");
        settings.put("javax.net.ssl.trustStorePassword", server.storePassword());
        Map<String, String> previous = new HashMap<>();
        settings.forEach((key, value) -> previous.put(key, System.setProperty(key, value)));
        try {
            body.run();
        } finally {
            previous.forEach((key, value) -> {
                if (value == null) {
                    System.clearProperty(key);
                } else {
                    System.setProperty(key, value);
                }
            });
        }
    }
}
