package com.otilm.mcp.security;

import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.config.IlmApiClientConfiguration;
import com.otilm.mcp.config.IlmApiProperties;
import com.otilm.mcp.service.NotificationProfileInput;
import com.otilm.mcp.service.NotificationServiceImpl;
import com.otilm.mcp.service.TriggerInput;
import com.otilm.mcp.service.WorkflowServiceImpl;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import static com.otilm.mcp.IlmTestSupport.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class SharedClientRecoveryTest {
    private static final String SENTINEL = "secretTokenEchoMustNeverLeak";
    private static final String PROFILE = "00000000-0000-4000-8000-000000000531";
    private static final String TRIGGER = "6daef4cf-2ad8-45f8-8998-de8d3fc44fad";

    @ParameterizedTest
    @ValueSource(strings = {"response", "transport"})
    void createdProfileStillAnswersWhenTheFollowUpReadFails(String variant) {
        AtomicInteger creates = new AtomicInteger();
        AtomicInteger reads = new AtomicInteger();
        ClientHttpRequestFactory failure = failingFactory(variant, HttpStatus.SERVICE_UNAVAILABLE);
        NotificationServiceImpl service = new NotificationServiceImpl(client((uri, method) -> {
            if (method == HttpMethod.POST) {
                assertThat(uri.getPath()).isEqualTo("/api/v1/notificationProfiles");
                creates.incrementAndGet();
                return request(uri, method, fixture("notification-profile"), HttpStatus.CREATED);
            }
            assertThat(uri.getPath()).isEqualTo("/api/v1/notificationProfiles/" + PROFILE);
            reads.incrementAndGet();
            return failure.createRequest(uri, method);
        }));

        String result = RequestCredentials
                .call(RequestCredentialsTest.context(SENTINEL), () -> service
                        .createNotificationProfile(
                                new NotificationProfileInput("name-5", "owner", null, null, true, "P1D", null)));

        assertThat(result)
                .startsWith("Created notification profile name-5 [" + PROFILE + "].")
                .contains("Recipients: groups name-7 [00000000-0000-4000-8000-000000000521]")
                .doesNotContain(SENTINEL);
        assertThat(creates.get()).isEqualTo(1);
        assertThat(reads.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"response", "transport"})
    void triggerStillAnswersWhenTheFollowUpAttachmentReadFails(String variant) {
        ClientHttpRequestFactory failure = failingFactory(variant, HttpStatus.SERVICE_UNAVAILABLE);
        WorkflowServiceImpl service = new WorkflowServiceImpl(client((uri, method) -> {
            if (uri.getPath().equals("/api/v1/workflows/triggers/" + TRIGGER)) {
                return request(uri, method, fixture("workflow-trigger-set-field"), HttpStatus.OK);
            }
            assertThat(uri.getPath()).isEqualTo("/api/v1/settings/events");
            return failure.createRequest(uri, method);
        }));

        String result = RequestCredentials
                .call(RequestCredentialsTest.context(SENTINEL), () -> service.getTrigger(TRIGGER));

        assertThat(result)
                .contains("Trigger: name-1 [" + TRIGGER + "]",
                        "Platform attachment: unknown (the platform event settings could not be read)")
                .doesNotContain(SENTINEL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"response", "transport-status"})
    void missingNotificationProfileRetainsTheActionableNotFoundAnswer(String variant) {
        WorkflowServiceImpl service = new WorkflowServiceImpl(client(failingFactory(variant, HttpStatus.NOT_FOUND)));

        String result = RequestCredentials
                .call(RequestCredentialsTest.context(SENTINEL),
                        () -> service
                                .createTrigger(new TriggerInput("new-trigger", "certificate_expiring", null, null,
                                        PROFILE, null, false)));

        assertThat(result)
                .isEqualTo(
                        "Notification profile " + PROFILE + " was not found; list_notification_profiles lists them.");
    }

    @ParameterizedTest
    @CsvSource({
            "response, 302",
            "response, 404",
            "response, 503",
            "transport-status, 302",
            "transport-status, 404",
            "transport-status, 503"})
    void sharedClientPreservesHttpStatusTypesWithoutResponseData(String variant, int status) {
        IlmApiClient client = client(failingFactory(variant, HttpStatusCode.valueOf(status)));
        var context = RequestCredentialsTest.context(SENTINEL);

        assertThatExceptionOfType(RestClientResponseException.class)
                .isThrownBy(() -> RequestCredentials.call(context, () -> client.getNotificationProfile(PROFILE)))
                .satisfies(failure -> {
                    assertThat(failure.getStatusCode().value()).isEqualTo(status);
                    if (status == 404) {
                        assertThat(failure).isExactlyInstanceOf(HttpClientErrorException.NotFound.class);
                    } else if (status == 503) {
                        assertThat(failure).isExactlyInstanceOf(HttpServerErrorException.ServiceUnavailable.class);
                    }
                    assertThat(failure.getMessage()).contains(Integer.toString(status)).doesNotContain(SENTINEL);
                    assertThat(failure.getStatusText()).doesNotContain(SENTINEL);
                    assertThat(failure.getResponseBodyAsByteArray()).isEmpty();
                    assertThat(failure.getResponseHeaders()).isEqualTo(HttpHeaders.EMPTY);
                    assertThat(failure.getCause()).isNull();
                    assertThat(failure.getSuppressed()).isEmpty();
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"transport", "runtime"})
    void transportFailuresRemainRecoverableWithoutTheirMessagesOrCauses(String variant) {
        IlmApiClient client = client(failingFactory(variant, HttpStatus.SERVICE_UNAVAILABLE));
        var context = RequestCredentialsTest.context(SENTINEL);

        assertThatExceptionOfType(RestClientException.class)
                .isThrownBy(() -> RequestCredentials.call(context, () -> client.getNotificationProfile(PROFILE)))
                .satisfies(failure -> {
                    assertThat(failure.getMessage()).doesNotContain(SENTINEL);
                    assertThat(failure.getCause()).isNull();
                    assertThat(failure.getSuppressed()).isEmpty();
                });
    }

    private static ClientHttpRequestFactory failingFactory(String variant, HttpStatusCode status) {
        return (uri, method) -> {
            if (variant.equals("transport")) {
                throw new IOException(SENTINEL);
            }
            if (variant.equals("runtime")) {
                throw new IllegalStateException(SENTINEL);
            }
            if (variant.equals("transport-status")) {
                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth(SENTINEL);
                throw new RestClientResponseException(SENTINEL, status, SENTINEL, headers,
                        SENTINEL.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            }
            return request(uri, method, SENTINEL, status);
        };
    }

    private static MockClientHttpRequest request(URI uri, HttpMethod method, String body, HttpStatusCode status) {
        MockClientHttpRequest request = new MockClientHttpRequest(method, uri);
        MockClientHttpResponse response = new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setBearerAuth(SENTINEL);
        request.setResponse(response);
        return request;
    }

    private static IlmApiClient client(ClientHttpRequestFactory factory) {
        IlmApiProperties properties = new IlmApiProperties();
        properties.setUrl("https://ilm.test/api");
        properties.getAuth().setMethod(IlmApiProperties.AuthMethod.OAUTH_DELEGATION);
        return new IlmApiClient(
                new IlmApiClientConfiguration().ilmRestClient(properties, Optional.empty(), Optional.of(factory)));
    }
}
