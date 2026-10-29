package com.otilm.mcp.security;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The outbound proxy of the shared OAuth client, set through the variables ILM Core reads. {@code HTTP_PROXY} serves
 * http requests and {@code HTTPS_PROXY} serves https requests, with no fallback from one to the other. Loopback hosts
 * and hosts that match {@code NO_PROXY} bypass both. Credentials in a proxy URL go to that proxy only.
 */
final class OutboundProxy {
    private static final Logger logger = LoggerFactory.getLogger(OutboundProxy.class);
    private static final String UNSUPPORTED = "HTTP_PROXY and HTTPS_PROXY must be http://host:port URLs";
    private static final List<Proxy> DIRECT = List.of(Proxy.NO_PROXY);
    // The JDK adds these to the nonProxyHosts of ILM Core, so they bypass the proxy whatever NO_PROXY holds.
    private static final String LOOPBACK_HOSTS = "localhost|127.*|[::1]|0.0.0.0|[::0]";

    /** The proxy for each request scheme that has one. */
    private final Map<String, Endpoint> endpoints;

    /** One pattern per {@code NO_PROXY} entry and loopback host, matched against the whole lower-case request host. */
    private final List<Pattern> exempt;

    private OutboundProxy(Map<String, Endpoint> endpoints, List<Pattern> exempt) {
        this.endpoints = endpoints;
        this.exempt = exempt;
    }

    /**
     * Reads the proxy settings.
     *
     * @return the proxy, or empty when neither {@code HTTP_PROXY} nor {@code HTTPS_PROXY} is set
     * @throws IllegalStateException if a proxy URL is not an {@code http://host:port} URL
     */
    static Optional<OutboundProxy> from(OutboundProperties outbound) {
        Map<String, Endpoint> endpoints = new LinkedHashMap<>();
        add(endpoints, "http", outbound.httpProxy());
        add(endpoints, "https", outbound.httpsProxy());
        if (endpoints.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new OutboundProxy(endpoints, exemptions(outbound.noProxy())));
    }

    /** Selects the proxy for a request URI, or none when its scheme has no proxy or its host is exempt. */
    ProxySelector selector() {
        return new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                String scheme = uri.getScheme();
                String host = uri.getHost();
                Endpoint endpoint = scheme == null ? null : endpoints.get(scheme.toLowerCase(Locale.ROOT));
                if (endpoint == null || host == null || isExempt(host)) {
                    return DIRECT;
                }
                return List.of(endpoint.proxy());
            }

            @Override
            public void connectFailed(URI uri, SocketAddress address, IOException failure) {
                // Each scheme has one fixed proxy, so there is none to fall back to.
            }
        };
    }

    /**
     * Answers a credential request from the proxy of the request's scheme, so two proxies at one address keep their own
     * credentials. The JDK client also asks about origin servers that answer 401, and those must never receive the
     * proxy's credentials.
     */
    Authenticator authenticator() {
        return new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (getRequestorType() != RequestorType.PROXY) {
                    return null;
                }
                // The JDK gives no URL for the CONNECT that opens a tunnel, and it tunnels only https.
                URL url = getRequestingURL();
                Endpoint endpoint = endpoints.get(url == null ? "https" : url.getProtocol());
                if (endpoint == null || !endpoint.hasCredentials()
                        || !endpoint.serves(getRequestingHost(), getRequestingPort())) {
                    return null;
                }
                return endpoint.credentials();
            }
        };
    }

    /** Routes the client through this proxy and, when a proxy URL carries credentials, lets it answer the proxy. */
    void applyTo(HttpClient.Builder builder) {
        builder.proxy(selector());
        if (endpoints.values().stream().anyMatch(Endpoint::hasCredentials)) {
            // The JDK refuses Basic authentication towards a proxy for HTTPS tunnels by default. It reads that setting
            // once, when the first HttpClient of the process is built, so it is cleared before this client is built.
            System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
            // With an authenticator the JDK client throws on a 401 or 407 that offers Basic or no challenge, unless the
            // authenticator answers it. A proxy without credentials therefore installs none.
            builder.authenticator(authenticator());
        }
        String schemes = String.join(" and ", endpoints.keySet());
        logger.info("Using the outbound proxy for {} requests", schemes);
    }

    @Override
    public String toString() {
        return "OutboundProxy[redacted]";
    }

    private boolean isExempt(String host) {
        String name = host.toLowerCase(Locale.ROOT);
        return exempt.stream().anyMatch(pattern -> pattern.matcher(name).matches());
    }

    private static void add(Map<String, Endpoint> endpoints, String scheme, String url) {
        if (url != null && !url.isBlank()) {
            endpoints.put(scheme, Endpoint.parse(url.strip()));
        }
    }

    /**
     * Parses {@code NO_PROXY} and the loopback hosts the way the JDK reads the {@code nonProxyHosts} of ILM Core:
     * entries are separated by {@code ,} or {@code |}, ignore case, and are whole host names or patterns with a leading
     * or trailing {@code *} for any characters. Ports and CIDR ranges are not supported.
     */
    private static List<Pattern> exemptions(String noProxy) {
        String entries = (noProxy == null ? "" : noProxy) + "|" + LOOPBACK_HOSTS;
        return Arrays
                .stream(entries.split("[,|]"))
                .map(entry -> entry.strip().toLowerCase(Locale.ROOT))
                .filter(entry -> !entry.isEmpty())
                .map(OutboundProxy::pattern)
                .toList();
    }

    /** Only a leading or trailing {@code *} is a wildcard. Everything else is literal, as in the JDK. */
    private static Pattern pattern(String entry) {
        boolean leading = entry.startsWith("*");
        boolean trailing = entry.length() > 1 && entry.endsWith("*");
        String literal = entry.substring(leading ? 1 : 0, trailing ? entry.length() - 1 : entry.length());
        return Pattern.compile((leading ? ".*" : "") + Pattern.quote(literal) + (trailing ? ".*" : ""));
    }

    /** A proxy address and, when its URL carries them, the credentials it asks for. */
    private record Endpoint(InetSocketAddress address, PasswordAuthentication credentials) {
        /** A new instance each time, because the JDK tells whoever gets a password to zero it. */
        @Override
        public PasswordAuthentication credentials() {
            return credentials == null
                    ? null
                    : new PasswordAuthentication(credentials.getUserName(), credentials.getPassword());
        }

        /** Whether the URL carried credentials, without making a copy of the password. */
        boolean hasCredentials() {
            return credentials != null;
        }

        /** Parses an {@code http://[user[:password]@]host:port} URL. The failure never echoes the URL. */
        static Endpoint parse(String url) {
            URI uri;
            try {
                uri = new URI(url);
            } catch (URISyntaxException ignored) {
                throw new IllegalStateException(UNSUPPORTED);
            }
            int port = uri.getPort();
            if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || port < 1 || port > 65535) {
                throw new IllegalStateException(UNSUPPORTED);
            }
            return new Endpoint(InetSocketAddress.createUnresolved(uri.getHost(), port), credentials(uri));
        }

        private static PasswordAuthentication credentials(URI uri) {
            String userInfo = uri.getUserInfo();
            if (userInfo == null || userInfo.isEmpty()) {
                return null;
            }
            // The first colon ends the user name, as in Basic authentication. The URI has decoded any escapes.
            int colon = userInfo.indexOf(':');
            String user = colon < 0 ? userInfo : userInfo.substring(0, colon);
            String password = colon < 0 ? "" : userInfo.substring(colon + 1);
            return new PasswordAuthentication(user, password.toCharArray());
        }

        /** The address stays unresolved, so the JDK resolves it on each connection and follows DNS changes. */
        Proxy proxy() {
            return new Proxy(Proxy.Type.HTTP, address);
        }

        boolean serves(String host, int port) {
            return address.getPort() == port && address.getHostString().equalsIgnoreCase(host);
        }
    }
}
