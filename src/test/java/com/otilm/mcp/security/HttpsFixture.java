package com.otilm.mcp.security;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.net.ssl.SSLContext;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * A WireMock HTTPS server whose self-signed certificate is generated for the test and never leaves its temp folder. It
 * binds to the IPv4 loopback address and is addressed by it, because a dynamic port reached through {@code localhost}
 * can end at another local process that listens on the same port number.
 */
final class HttpsFixture implements AutoCloseable {
    private static final String LOOPBACK = "127.0.0.1";
    private static final String PING = "/ping";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private final String secret = UUID.randomUUID().toString();
    private final X509Certificate certificate;
    private final WireMockServer server;

    HttpsFixture(Path directory, String name) throws Exception {
        KeyPair pair = keyPair();
        certificate = selfSigned(name, pair);
        KeyStore keys = emptyStore();
        keys.setKeyEntry(name, pair.getPrivate(), secret.toCharArray(), new X509Certificate[]{certificate});
        Path keystore = save(keys, directory.resolve(name + ".p12"));
        server = new WireMockServer(options()
                .bindAddress(LOOPBACK)
                .httpDisabled(true)
                .dynamicHttpsPort()
                .keystoreType("PKCS12")
                .keystorePath(keystore.toString())
                .keystorePassword(secret)
                .keyManagerPassword(secret));
        server.start();
        server.stubFor(get(PING).willReturn(ok()));
    }

    String url() {
        return "https://" + LOOPBACK + ":" + server.httpsPort() + PING;
    }

    /** The server certificate as one PEM block. */
    String pem() throws IOException {
        return pem(certificate);
    }

    /** Writes a PKCS12 trust store holding only this certificate, readable with {@link #storePassword()}. */
    Path trustStore(Path file) throws IOException, GeneralSecurityException {
        KeyStore trust = emptyStore();
        trust.setCertificateEntry("fixture", certificate);
        return save(trust, file);
    }

    String storePassword() {
        return secret;
    }

    /** Status code of a GET through a client that uses the given TLS context; a failed handshake throws. */
    int status(SSLContext context) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newBuilder().sslContext(context).connectTimeout(TIMEOUT).build()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url())).timeout(TIMEOUT).build();
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }

    @Override
    public void close() {
        server.stop();
    }

    private static String pem(X509Certificate certificate) throws IOException {
        StringWriter pem = new StringWriter();
        try (JcaPEMWriter writer = new JcaPEMWriter(pem)) {
            writer.writeObject(certificate);
        }
        return pem.toString();
    }

    private static KeyPair keyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static X509Certificate selfSigned(String name, KeyPair pair) throws Exception {
        X500Name subject = new X500Name("CN=" + name);
        Instant now = Instant.now();
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(subject,
                BigInteger.valueOf(now.toEpochMilli()), Date.from(now.minus(Duration.ofDays(1))),
                Date.from(now.plus(Duration.ofDays(1))), subject, pair.getPublic());
        builder
                .addExtension(Extension.subjectAlternativeName, false,
                        new GeneralNames(new GeneralName(GeneralName.iPAddress, LOOPBACK)));
        return new JcaX509CertificateConverter()
                .getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withECDSA").build(pair.getPrivate())));
    }

    private static KeyStore emptyStore() throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        return store;
    }

    private Path save(KeyStore store, Path file) throws IOException, GeneralSecurityException {
        try (OutputStream out = Files.newOutputStream(file)) {
            store.store(out, secret.toCharArray());
        }
        return file;
    }
}
