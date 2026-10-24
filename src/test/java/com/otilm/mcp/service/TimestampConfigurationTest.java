package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.ConfigurationApiClient;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.tool.TimestampConfigurationTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class TimestampConfigurationTest {
    private static final String TSP = SigningConfigurationTest.TSP;
    private static final String SIGNING = SigningConfigurationTest.PROFILE;
    private static final String TIME = "00000000-0000-4000-8000-000000000915";
    private static final String CREDENTIAL = "00000000-0000-4000-8000-000000000916";
    private static final String USER = "00000000-0000-4000-8000-000000000917";
    private static final String VAULT = "00000000-0000-4000-8000-000000000918";
    private static final String BASE = "/v1/tspProfiles/" + TSP;
    private static final String CLOCK = "/v1/timeQualityConfigurations";
    private TimestampConfigurationTool tool;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        tool = new TimestampConfigurationTool(new TimestampConfigurationServiceImpl(
                new ConfigurationApiClient(IlmApiClient.restClientBuilder(wm.getHttpBaseUrl()).build()),
                new PlatformVersionCheck(IlmTestSupport.client(wm))));
        IlmTestSupport.stubIlmVersion("2.20.0");
        for (String resource : new String[]{"tspProfiles", "timeQualityConfigurations"}) {
            stubFor(get(urlEqualTo("/v1/attributes/custom/resources/" + resource)).willReturn(okJson("[]")));
        }
        stubFor(get(urlEqualTo(BASE))
                .willReturn(okJson("{\"uuid\":\"" + TSP
                        + "\",\"name\":\"tsp\",\"description\":\"old\",\"defaultSigningProfile\":{\"uuid\":\"" + SIGNING
                        + "\"},\"vaultProfile\":{\"uuid\":\"" + VAULT
                        + "\"},\"allowedAuthenticationMethods\":[\"basicPassword\"],\"customAttributes\":[],\"password\":\"SENTINEL_SECRET\"}")));
        stubFor(get(urlEqualTo(CLOCK + "/" + TIME))
                .willReturn(okJson("{\"uuid\":\"" + TIME
                        + "\",\"name\":\"clock\",\"accuracy\":\"PT1S\",\"ntpServers\":[\"ntp.example.test\"],\"ntpCheckInterval\":\"PT0.5S\",\"ntpCheckTimeout\":\"PT0.1S\",\"maxClockDrift\":\"PT0.5S\",\"ntpSamplesPerServer\":3,\"ntpServersMinReachable\":1,\"leapSecondGuard\":true,\"customAttributes\":[]}")));
    }

    @Test
    void tspCreateUpdateStateDeleteAndDetailUseCompletePreservedReferences() {
        stubFor(post(urlEqualTo("/v1/tspProfiles")).willReturn(okJson("{\"uuid\":\"" + TSP + "\",\"name\":\"new\"}")));
        stubFor(put(urlEqualTo(BASE)).willReturn(okJson("{\"uuid\":\"" + TSP + "\"}")));
        assertThat(tool.getTspProfile(TSP)).contains("tsp").doesNotContain("SENTINEL_SECRET");
        assertThat(tool.createTspProfile("{\"name\":\"new\",\"defaultSigningProfileUuid\":\"" + SIGNING + "\"}"))
                .contains(TSP);
        assertThat(tool.updateTspProfile(TSP, "{\"description\":\"updated\"}")).contains(TSP);
        verify(putRequestedFor(urlEqualTo(BASE))
                .withRequestBody(
                        equalToJson("{\"name\":\"tsp\",\"description\":\"updated\",\"defaultSigningProfileUuid\":\""
                                + SIGNING + "\",\"vaultProfileUuid\":\"" + VAULT
                                + "\",\"allowedAuthenticationMethods\":[\"basicPassword\"],\"customAttributes\":[]}")));
        stubFor(patch(urlEqualTo(BASE + "/enable")).willReturn(noContent()));
        stubFor(patch(urlEqualTo(BASE + "/disable")).willReturn(noContent()));
        stubFor(delete(urlEqualTo(BASE)).willReturn(noContent()));
        assertThat(tool.enableTspProfile(TSP)).contains("accepted");
        assertThat(tool.disableTspProfile(TSP)).contains("accepted");
        assertThat(tool.deleteTspProfile(TSP)).contains("Deleted");
    }

    @Test
    void basicCredentialsSendPasswordAndReturnMetadataOnlyWithSafeRotation() {
        String path = BASE + "/basicCredentials";
        String detail = "{\"uuid\":\"" + CREDENTIAL + "\",\"username\":\"service\",\"mappedUser\":{\"uuid\":\"" + USER
                + "\",\"name\":\"user\"},\"password\":\"SENTINEL_SECRET\"}";
        stubFor(get(urlEqualTo(path)).willReturn(okJson("[" + detail + "]")));
        stubFor(get(urlEqualTo(path + "/" + CREDENTIAL)).willReturn(okJson(detail)));
        stubFor(post(urlEqualTo(path)).willReturn(okJson(detail)));
        stubFor(put(urlEqualTo(path + "/" + CREDENTIAL)).willReturn(okJson(detail)));
        stubFor(delete(urlEqualTo(path + "/" + CREDENTIAL)).willReturn(noContent()));
        assertThat(tool.listTspBasicCredentials(TSP)).contains("service").doesNotContain("SENTINEL_SECRET");
        assertThat(tool.getTspBasicCredential(TSP, CREDENTIAL)).contains("service").doesNotContain("SENTINEL_SECRET");
        assertThat(
                tool
                        .createTspBasicCredential(TSP,
                                "{\"username\":\"service\",\"password\":\"fresh-synthetic\",\"mappedUserUuid\":\""
                                        + USER + "\"}"))
                .contains(CREDENTIAL)
                .doesNotContain("fresh-synthetic", "SENTINEL_SECRET");
        verify(postRequestedFor(urlEqualTo(path))
                .withRequestBody(matchingJsonPath("$.password", equalTo("fresh-synthetic"))));
        assertThat(tool.updateTspBasicCredential(TSP, CREDENTIAL, "{\"username\":\"changed\"}")).contains("fresh");
        verify(0, putRequestedFor(urlEqualTo(path + "/" + CREDENTIAL)));
        assertThat(tool
                .updateTspBasicCredential(TSP, CREDENTIAL,
                        "{\"username\":\"changed\",\"password\":\"rotated-synthetic\"}"))
                .contains(CREDENTIAL);
        verify(putRequestedFor(urlEqualTo(path + "/" + CREDENTIAL))
                .withRequestBody(matchingJsonPath("$.password", equalTo("rotated-synthetic")))
                .withRequestBody(matchingJsonPath("$.mappedUserUuid", equalTo(USER))));
        assertThat(tool.deleteTspBasicCredential(TSP, CREDENTIAL)).contains("Deleted");
    }

    @Test
    void authenticationAndCredentialsRequire219AndVaultForBasicMethod() {
        IlmTestSupport.stubIlmVersion("2.18.0");
        assertThat(tool.createTspProfile("{\"name\":\"old\",\"allowedAuthenticationMethods\":[\"clientCertificate\"]}"))
                .contains("2.19");
        assertThat(tool.listTspBasicCredentials(TSP)).contains("2.19");
        IlmTestSupport.stubIlmVersion("2.20.0");
        assertThat(tool.createTspProfile("{\"name\":\"invalid\",\"allowedAuthenticationMethods\":[\"basicPassword\"]}"))
                .containsIgnoringCase("vault");
        verify(0, postRequestedFor(urlEqualTo("/v1/tspProfiles")));
    }

    @Test
    void timeQualitySearchCreatePatchReadAndReferencesUseValidDurations() {
        stubFor(post(urlEqualTo(CLOCK + "/list"))
                .willReturn(okJson("{\"items\":[{\"uuid\":\"" + TIME + "\",\"name\":\"clock\"}]}")));
        stubFor(post(urlEqualTo(CLOCK)).willReturn(okJson("{\"uuid\":\"" + TIME + "\"}")));
        stubFor(put(urlEqualTo(CLOCK + "/" + TIME)).willReturn(okJson("{\"uuid\":\"" + TIME + "\"}")));
        stubFor(get(urlEqualTo(CLOCK + "/" + TIME + "/signingProfiles"))
                .willReturn(okJson("[{\"uuid\":\"" + SIGNING + "\",\"name\":\"signing\"}]")));
        assertThat(tool.searchTimeQualityConfigurations(null, 10, 1)).contains("clock");
        assertThat(tool.getTimeQualityConfiguration(TIME)).contains("ntp.example.test");
        assertThat(tool
                .createTimeQualityConfiguration(
                        "{\"name\":\"clock\",\"accuracy\":\"PT1S\",\"ntpServers\":[\"ntp.example.test\"],\"ntpCheckInterval\":\"PT0.5S\",\"ntpCheckTimeout\":\"PT0.1S\",\"maxClockDrift\":\"PT0.5S\"}"))
                .contains(TIME);
        assertThat(tool.updateTimeQualityConfiguration(TIME, "{\"ntpSamplesPerServer\":5}")).contains(TIME);
        verify(putRequestedFor(urlEqualTo(CLOCK + "/" + TIME))
                .withRequestBody(matchingJsonPath("$.ntpServers[0]", equalTo("ntp.example.test")))
                .withRequestBody(matchingJsonPath("$.ntpSamplesPerServer", equalTo("5"))));
        assertThat(tool.getTimeQualitySigningProfiles(TIME)).contains(SIGNING);
        stubFor(delete(urlEqualTo(CLOCK + "/" + TIME)).willReturn(noContent()));
        assertThat(tool.deleteTimeQualityConfiguration(TIME)).contains("Deleted");
        assertThat(tool.updateTimeQualityConfiguration(TIME, "{\"accuracy\":\"PT-1S\"}"))
                .containsIgnoringCase("positive");
        verify(1, putRequestedFor(urlEqualTo(CLOCK + "/" + TIME)));
    }

    @Test
    void intervalEqualToAccuracyIsRejectedBeforeTimeQualityReplacement() {
        assertThat(tool.updateTimeQualityConfiguration(TIME, "{\"ntpCheckInterval\":\"PT1S\"}"))
                .containsIgnoringCase("accuracy");
        verify(0, putRequestedFor(urlEqualTo(CLOCK + "/" + TIME)));
    }

    @Test
    void ntpHostnameValidationSupportsIpv6AndRejectsEmptyLabels() {
        stubFor(put(urlEqualTo(CLOCK + "/" + TIME)).willReturn(okJson("{\"uuid\":\"" + TIME + "\"}")));
        assertThat(tool.updateTimeQualityConfiguration(TIME, "{\"ntpServers\":[\"::1\"]}")).contains(TIME);
        assertThat(tool.updateTimeQualityConfiguration(TIME, "{\"ntpServers\":[\"invalid..example\"]}"))
                .containsIgnoringCase("hostname");
        verify(1, putRequestedFor(urlEqualTo(CLOCK + "/" + TIME)));
    }

}
