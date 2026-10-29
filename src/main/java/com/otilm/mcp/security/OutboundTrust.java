package com.otilm.mcp.security;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds the TLS context of the shared OAuth client. It trusts the JDK's own anchors plus the certificates in
 * {@code TRUSTED_CERTIFICATES}, so a server behind a private certificate authority is reachable without a custom trust
 * store in the image.
 */
final class OutboundTrust {
    private static final Logger logger = LoggerFactory.getLogger(OutboundTrust.class);
    private static final String BEGIN = "-----BEGIN CERTIFICATE-----";
    private static final String END = "-----END CERTIFICATE-----";
    // The value is the one ILM Core reads, so it can carry keys, revocation lists and comments next to the
    // certificates. Only certificate blocks count.
    private static final Pattern BLOCK = Pattern.compile(BEGIN + "(.*?)" + END, Pattern.DOTALL);
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private OutboundTrust() {
    }

    /**
     * Returns a context that trusts the JDK's anchors and every certificate block in {@code pem}, as ILM Core reads the
     * value, even a block written on a single line. A block that cannot be read is skipped with a warning, so the
     * content of the value never stops startup. When no certificate is added the JDK default context is returned, so a
     * client with nothing to add behaves exactly as it would without this class.
     *
     * @throws IllegalStateException if the context cannot be built
     */
    static SSLContext sslContext(String pem) {
        try {
            List<Certificate> additional = certificates(pem);
            if (additional.isEmpty()) {
                // The default context presents the client keystore set through javax.net.ssl.keyStore; a custom one
                // would not.
                return SSLContext.getDefault();
            }
            KeyStore anchors = KeyStore.getInstance(KeyStore.getDefaultType());
            anchors.load(null, null);
            add(anchors, "jdk-trusted-", defaultAnchors());
            add(anchors, "platform-trusted-", additional);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustManagers(anchors), null);
            int count = additional.size();
            logger.info("Trusting {} additional {}", count, count == 1 ? "certificate" : "certificates");
            return context;
        } catch (GeneralSecurityException | IOException failure) {
            throw new IllegalStateException("The TLS context for outgoing connections could not be built", failure);
        }
    }

    /**
     * Parses the certificate blocks of the value. The warnings carry counts only, because the value and the JDK's parse
     * errors can quote the input.
     */
    private static List<Certificate> certificates(String pem) throws CertificateException {
        if (pem == null || pem.isBlank()) {
            return List.of();
        }
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        List<Certificate> certificates = new ArrayList<>();
        int skipped = 0;
        Matcher blocks = BLOCK.matcher(pem);
        while (blocks.find()) {
            // ILM Core accepts a block without line breaks, but the JDK's reader needs one after the header.
            String body = WHITESPACE.matcher(blocks.group(1)).replaceAll("");
            byte[] block = String.join("\n", BEGIN, body, END).getBytes(StandardCharsets.UTF_8);
            try {
                certificates.add(factory.generateCertificate(new ByteArrayInputStream(block)));
            } catch (CertificateException ignored) {
                skipped++;
            }
        }
        if (skipped > 0) {
            logger
                    .warn("TRUSTED_CERTIFICATES: skipped {} certificate {} that could not be read", skipped,
                            skipped == 1 ? "block" : "blocks");
        }
        if (certificates.isEmpty()) {
            logger.warn("TRUSTED_CERTIFICATES holds no readable certificate, so nothing is added");
        }
        return certificates;
    }

    /**
     * The JDK's anchors, read through a trust manager because the image's jlinked runtime has no dependable cacerts
     * path.
     */
    private static List<X509Certificate> defaultAnchors() throws GeneralSecurityException {
        return Arrays
                .stream(trustManagers(null))
                .filter(X509TrustManager.class::isInstance)
                .map(X509TrustManager.class::cast)
                .flatMap(manager -> Arrays.stream(manager.getAcceptedIssuers()))
                .toList();
    }

    /** A null store selects the JDK's default anchors, including any {@code javax.net.ssl.trustStore} override. */
    private static TrustManager[] trustManagers(KeyStore store) throws GeneralSecurityException {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        return factory.getTrustManagers();
    }

    private static void add(KeyStore store, String aliasPrefix, List<? extends Certificate> certificates)
            throws KeyStoreException {
        for (int index = 0; index < certificates.size(); index++) {
            store.setCertificateEntry(aliasPrefix + index, certificates.get(index));
        }
    }
}
