package com.otilm.mcp.security;

public final class RequestCredential {
    private final String token;

    RequestCredential(String token) {
        this.token = token;
    }

    String token() {
        return token;
    }

    @Override
    public String toString() {
        return "RequestCredential[redacted]";
    }
}
