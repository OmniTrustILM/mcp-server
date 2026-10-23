package com.otilm.mcp.service;

import java.util.regex.Pattern;

/**
 * The canonical form of a UUID; Java's own parser also accepts shortened forms such as 1-1-1-1-1.
 */
final class Uuids {

    private static final Pattern FORMAT = Pattern
            .compile("\\p{XDigit}{8}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{12}");

    private Uuids() {
    }

    static boolean valid(String value) {
        return value != null && FORMAT.matcher(value).matches();
    }
}
