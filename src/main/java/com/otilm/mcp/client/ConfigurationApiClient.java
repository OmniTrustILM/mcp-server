package com.otilm.mcp.client;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.NullNode;

/** Internal transport for fixed configuration endpoints; credential-bearing failures never expose response bodies. */
@Component
public class ConfigurationApiClient {

    private final RestClient client;

    public ConfigurationApiClient(RestClient ilmRestClient) {
        this.client = ilmRestClient;
    }

    public JsonNode get(String path, Object... variables) {
        return json(HttpMethod.GET, path, null, variables);
    }

    public JsonNode post(String path, Object body, Object... variables) {
        return json(HttpMethod.POST, path, body, variables);
    }

    public JsonNode put(String path, Object body, Object... variables) {
        return json(HttpMethod.PUT, path, body, variables);
    }

    public JsonNode patch(String path, Object body, Object... variables) {
        return json(HttpMethod.PATCH, path, body, variables);
    }

    public void delete(String path, Object... variables) {
        deleteBody(path, null, variables);
    }

    public void deleteBody(String path, Object body, Object... variables) {
        safely(() -> {
            request(HttpMethod.DELETE, path, body, variables).retrieve().toBodilessEntity();
            return null;
        }, false);
    }

    public void deleteWithDependencies(String path, Object... variables) {
        safely(() -> {
            request(HttpMethod.DELETE, path, null, variables).retrieve().toBodilessEntity();
            return null;
        }, true);
    }

    public BinaryContent download(String path, Object body, Object... variables) {
        return safely(() -> {
            ResponseEntity<byte[]> response = request(HttpMethod.POST, path, body, variables)
                    .accept(MediaType.ALL)
                    .retrieve()
                    .toEntity(byte[].class);
            String filename = response.getHeaders().getContentDisposition().getFilename();
            String mediaType = response.getHeaders().getContentType() == null
                    ? "application/octet-stream"
                    : response.getHeaders().getContentType().toString();
            return new BinaryContent(safeFilename(filename), mediaType,
                    response.getBody() == null ? new byte[0] : response.getBody());
        }, false);
    }

    private JsonNode json(HttpMethod method, String path, Object body, Object... variables) {
        return safely(() -> Objects
                .requireNonNullElse(request(method, path, body, variables).retrieve().body(JsonNode.class),
                        NullNode.getInstance()),
                false);
    }

    private RestClient.RequestBodySpec request(HttpMethod method, String path, Object body, Object... variables) {
        if (path == null || !path.startsWith("/") || path.startsWith("//") || path.contains("://")) {
            throw new IllegalArgumentException("A fixed relative ILM endpoint is required.");
        }
        RestClient.RequestBodySpec request = client.method(method).uri(path, variables);
        return body == null ? request : request.body(body);
    }

    private static <T> T safely(Supplier<T> operation, boolean dependencyMessage) {
        try {
            return operation.get();
        } catch (RestClientResponseException failure) {
            String message = "ILM answered HTTP " + failure.getStatusCode().value() + ".";
            if (dependencyMessage && failure.getStatusCode().value() == 422) {
                message += " Deletion was refused; check dependencies in ILM.";
            }
            throw new ApiException(message);
        } catch (RestClientException failure) {
            throw new ApiException(
                    "ILM could not complete the request. Check the connection and server configuration.");
        }
    }

    private static String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "export.bin";
        }
        String normalized = filename.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
        return name.isBlank() ? "export.bin" : name;
    }

    public record BinaryContent(String filename, String contentType, byte[] data) {

        @Override
        public boolean equals(Object other) {
            return other instanceof BinaryContent(String otherFilename, String otherContentType, byte[] otherData)
                    && Objects.equals(filename, otherFilename) && Objects.equals(contentType, otherContentType)
                    && Arrays.equals(data, otherData);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(filename, contentType) + Arrays.hashCode(data);
        }

        @Override
        public String toString() {
            return "Binary content (" + contentType + ", " + data.length + " bytes)";
        }
    }

    public static final class ApiException extends IllegalStateException {

        private static final long serialVersionUID = 1L;

        public ApiException(String message) {
            super(message);
        }
    }
}
