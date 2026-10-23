package com.otilm.mcp.service;

import com.otilm.mcp.client.ConfigurationApiClient;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import tools.jackson.databind.node.ObjectNode;

import static com.otilm.mcp.service.ConfigurationSupport.describe;
import static com.otilm.mcp.service.ConfigurationSupport.list;
import static com.otilm.mcp.service.ConfigurationSupport.required;
import static com.otilm.mcp.service.ConfigurationSupport.run;
import static com.otilm.mcp.service.ConfigurationSupport.uuid;

@Service
public class ProxyServiceImpl implements ProxyService {
    private static final String PROXY_UUID = "Proxy UUID";
    private static final String DESCRIPTION = "description";

    private static final String PROXY_ENDPOINT = "/v1/proxies/{uuid}";
    private static final String[] FIELDS = {
            "uuid",
            "name",
            DESCRIPTION,
            "code",
            "status",
            "lastActivity",
            "connectors"};
    private final ConfigurationApiClient api;
    private final PlatformVersionCheck versions;

    public ProxyServiceImpl(ConfigurationApiClient api, PlatformVersionCheck versions) {
        this.api = api;
        this.versions = versions;
    }

    @Override
    public String listProxies() {
        return execute(() -> list("Proxies", api.get("/v1/proxies"), FIELDS));
    }

    @Override
    public String getProxy(String uuid) {
        return execute(() -> describe("Proxy", api.get(PROXY_ENDPOINT, uuid(uuid, PROXY_UUID)), FIELDS));
    }

    @Override
    public String createProxy(String configuration) {
        return execute(() -> {
            ObjectNode body = AdministrationInput.configuration(configuration, "name", DESCRIPTION);
            AdministrationInput.text(body, "name", DESCRIPTION);
            body.put("name", required(body.path("name").asString(null), "Proxy name"));
            return describe("Proxy created", api.post("/v1/proxies", body), FIELDS);
        });
    }

    @Override
    public String updateProxy(String uuid, String configuration) {
        return run("updating proxy", () -> {
            ObjectNode body = AdministrationInput.configuration(configuration, DESCRIPTION);
            AdministrationInput.text(body, DESCRIPTION);
            return execute(
                    () -> describe("Proxy updated", api.put(PROXY_ENDPOINT, body, uuid(uuid, PROXY_UUID)), FIELDS));
        });
    }

    @Override
    public String deleteProxy(String uuid) {
        return execute(() -> {
            api.delete(PROXY_ENDPOINT, uuid(uuid, PROXY_UUID));
            return "Proxy deleted.";
        });
    }

    @Override
    public String getProxyInstallationInstructions(String uuid) {
        return execute(() -> describe("Proxy installation instructions",
                api.get(PROXY_ENDPOINT + "/instructions", uuid(uuid, PROXY_UUID)), "uuid", "installationInstructions"));
    }

    private String execute(Supplier<String> action) {
        return run("managing proxy", () -> versions.unsupported(IlmFeature.CONNECTOR_PROXY).orElseGet(action));
    }
}
