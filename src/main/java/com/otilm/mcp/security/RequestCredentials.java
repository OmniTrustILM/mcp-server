package com.otilm.mcp.security;

import io.modelcontextprotocol.common.McpTransportContext;
import java.util.function.Supplier;

/** Scoped only on the actual synchronous SDK callback worker; never inherited by detached tasks. */
public final class RequestCredentials {
    public static final String KEY = RequestCredentials.class.getName();
    private static final ThreadLocal<RequestCredential> CURRENT = new ThreadLocal<>();

    private RequestCredentials() {
    }

    /** Allows error presentation to apply the shared-request disclosure policy without exposing the credential. */
    public static boolean isBound() {
        return CURRENT.get() != null;
    }

    public static String token() {
        RequestCredential credential = CURRENT.get();
        if (credential == null) {
            throw new IllegalStateException("Authenticated request credential is required");
        }
        return credential.token();
    }

    public static <T> T call(McpTransportContext context, Supplier<T> callback) {
        if (context == null || !(context.get(KEY) instanceof RequestCredential credential)) {
            throw new IllegalStateException("Authenticated transport context is required");
        }
        RequestCredential previous = CURRENT.get();
        CURRENT.set(credential);
        try {
            return callback.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
