package com.otilm.mcp.security;

public final class AuthTokenHolder {
    private static final ThreadLocal<String> TOKEN = new ThreadLocal<>();

    private AuthTokenHolder() {
    }

    public static void setToken(String token) {
        TOKEN.set(token);
    }

    public static String getToken() {
        return TOKEN.get();
    }

    public static void clear() {
        TOKEN.remove();
    }
}
