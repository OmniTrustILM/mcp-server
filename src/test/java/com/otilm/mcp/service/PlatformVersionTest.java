package com.otilm.mcp.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformVersionTest {

    @ParameterizedTest
    @ValueSource(strings = {"2.20.0", "2.20", "2.20.0-SNAPSHOT", " 2.20.1 "})
    void parsesMajorAndMinor(String version) {
        assertThat(PlatformVersion.parse(version)).contains(new PlatformVersion(2, 20));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "x", "2", "unknown", "99999999999.1"})
    void rejectsUnparsableVersions(String version) {
        assertThat(PlatformVersion.parse(version)).isEmpty();
    }

    @Test
    void comparesMajorBeforeMinor() {
        PlatformVersion minimum = new PlatformVersion(2, 20);

        assertThat(new PlatformVersion(2, 19).isAtLeast(minimum)).isFalse();
        assertThat(new PlatformVersion(2, 20).isAtLeast(minimum)).isTrue();
        assertThat(new PlatformVersion(3, 0).isAtLeast(minimum)).isTrue();
        assertThat(new PlatformVersion(1, 99).isAtLeast(minimum)).isFalse();
    }

    @Test
    void printsMajorDotMinor() {
        assertThat(new PlatformVersion(2, 18)).hasToString("2.18");
    }
}
