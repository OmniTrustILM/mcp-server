package com.otilm.mcp.service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record PlatformVersion(int major, int minor) {

    private static final Pattern MAJOR_MINOR = Pattern.compile("(\\d{1,9})\\.(\\d{1,9})(?!\\d)");

    public static Optional<PlatformVersion> parse(String version) {
        if (version == null) {
            return Optional.empty();
        }
        Matcher matcher = MAJOR_MINOR.matcher(version.strip());
        if (!matcher.lookingAt()) {
            return Optional.empty();
        }
        return Optional.of(new PlatformVersion(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))));
    }

    public boolean isAtLeast(PlatformVersion minimum) {
        return major > minimum.major || (major == minimum.major && minor >= minimum.minor);
    }

    @Override
    public String toString() {
        return major + "." + minor;
    }
}
