package com.otilm.mcp.security;

public final class AuthenticationFailure extends RuntimeException {
    private final int status;

    public AuthenticationFailure(int status) {
        super(switch (status) {
            case 403 -> "Insufficient scope";
            case 503 -> "Identity provider unavailable";
            default -> "Invalid bearer token";
        });
        this.status = status;
    }

    public int status() {
        return status;
    }
}
