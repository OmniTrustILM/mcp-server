package com.otilm.mcp.service;

import com.otilm.api.model.client.certificate.DiscoveryResponseDto;
import com.otilm.api.model.client.certificate.EntityInstanceResponseDto;
import com.otilm.api.model.client.certificate.SearchRequestDto;
import com.otilm.api.model.client.discovery.DiscoveryListDto;
import com.otilm.api.model.common.UuidDto;
import com.otilm.api.model.core.authority.AuthorityInstanceDto;
import com.otilm.api.model.core.certificate.group.GroupDto;
import com.otilm.api.model.core.certificate.group.GroupRequestDto;
import com.otilm.api.model.core.connector.ConnectorDto;
import com.otilm.api.model.core.connector.FunctionGroupDto;
import com.otilm.api.model.core.credential.CredentialDto;
import com.otilm.api.model.core.cryptography.token.TokenInstanceDto;
import com.otilm.api.model.core.entity.EntityInstanceDto;
import com.otilm.api.model.core.raprofile.RaProfileDto;
import com.otilm.mcp.client.IlmApiClient;
import java.util.Collection;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

import static com.otilm.mcp.service.TextFormat.code;
import static com.otilm.mcp.service.TextFormat.field;
import static com.otilm.mcp.service.TextFormat.item;
import static com.otilm.mcp.service.TextFormat.titledList;

@Service
public class InfrastructureServiceImpl implements InfrastructureService {

    private static final String INDENT = "  ";
    private static final String STATUS = "Status";
    private static final String CONNECTOR = "Connector";
    private static final String KIND = "Kind";

    private final IlmApiClient ilmApiClient;

    public InfrastructureServiceImpl(IlmApiClient ilmApiClient) {
        this.ilmApiClient = ilmApiClient;
    }

    @Override
    public String listAuthorities() {
        return ToolResponses.run("listing authorities", () -> {
            Collection<AuthorityInstanceDto> authorities = ilmApiClient.listAuthorities();
            return titledList("Authority Instances (" + authorities.size() + ")", authorities,
                    "No authority instances found.", (sb, authority) -> {
                        item(sb, authority.getName(), authority.getUuid());
                        field(sb, INDENT, STATUS, authority.getStatus());
                        field(sb, INDENT, CONNECTOR, connectorName(authority));
                        field(sb, INDENT, KIND, authority.getKind());
                    });
        });
    }

    @Override
    public String listRaProfiles() {
        return ToolResponses.run("listing RA profiles", () -> {
            Collection<RaProfileDto> raProfiles = ilmApiClient.listRaProfiles();
            return titledList("RA Profiles (" + raProfiles.size() + ")", raProfiles, "No RA profiles found.",
                    (sb, profile) -> {
                        item(sb, profile.getName(), profile.getUuid());
                        field(sb, INDENT, "Enabled", profile.getEnabled());
                        field(sb, INDENT, "Description", profile.getDescription());
                        field(sb, INDENT, "Authority", profile.getAuthorityInstanceName());
                        if (profile.getEnabledProtocols() != null) {
                            field(sb, INDENT, "Protocols", String.join(", ", profile.getEnabledProtocols()));
                        }
                    });
        });
    }

    @Override
    public String listConnectors() {
        return ToolResponses.run("listing connectors", () -> {
            Collection<ConnectorDto> connectors = ilmApiClient.listConnectors();
            return titledList("Connectors (" + connectors.size() + ")", connectors, "No connectors found.",
                    (sb, connector) -> {
                        item(sb, connector.getName(), connector.getUuid());
                        field(sb, INDENT, STATUS, code(connector.getStatus()));
                        field(sb, INDENT, "URL", connector.getUrl());
                        field(sb, INDENT, "Auth Type", code(connector.getAuthType()));
                        if (connector.getFunctionGroups() != null && !connector.getFunctionGroups().isEmpty()) {
                            field(sb, INDENT, "Function Groups",
                                    connector
                                            .getFunctionGroups()
                                            .stream()
                                            .map(InfrastructureServiceImpl::functionGroup)
                                            .collect(Collectors.joining("; ")));
                        }
                    });
        });
    }

    @Override
    public String listGroups() {
        return ToolResponses.run("listing groups", () -> {
            Collection<GroupDto> groups = ilmApiClient.listGroups();
            return titledList("Groups (" + groups.size() + ")", groups, "No groups found.", (sb, group) -> {
                item(sb, group.getName(), group.getUuid());
                field(sb, INDENT, "Description", group.getDescription());
                field(sb, INDENT, "Email", group.getEmail());
            });
        });
    }

    @Override
    public String listEntities(String filters) {
        return ToolResponses.run("listing entities", () -> {
            EntityInstanceResponseDto response = ilmApiClient
                    .listEntities(SearchRequests.of(new SearchRequestDto(), filters, null, null));
            return titledList(
                    pageTitle("Entity Instances", response.getTotalItems(), response.getPageNumber(),
                            response.getTotalPages()),
                    response.getEntities(), "No entity instances found.", InfrastructureServiceImpl::formatEntity);
        });
    }

    @Override
    public String listCredentials() {
        return ToolResponses.run("listing credentials", () -> {
            Collection<CredentialDto> credentials = ilmApiClient.listCredentials();
            return titledList("Credentials (" + credentials.size() + ")", credentials, "No credentials found.",
                    (sb, credential) -> {
                        item(sb, credential.getName(), credential.getUuid());
                        field(sb, INDENT, "Enabled", credential.getEnabled());
                        field(sb, INDENT, KIND, credential.getKind());
                        field(sb, INDENT, CONNECTOR, credential.getConnectorName());
                    });
        });
    }

    @Override
    public String listTokenInstances() {
        return ToolResponses.run("listing token instances", () -> {
            Collection<TokenInstanceDto> tokens = ilmApiClient.listTokenInstances();
            return titledList("Token Instances (" + tokens.size() + ")", tokens, "No token instances found.",
                    (sb, token) -> {
                        item(sb, token.getName(), token.getUuid());
                        field(sb, INDENT, STATUS, code(token.getStatus()));
                        field(sb, INDENT, CONNECTOR, token.getConnectorName());
                        field(sb, INDENT, KIND, token.getKind());
                        field(sb, INDENT, "Token Profiles", token.getTokenProfiles());
                    });
        });
    }

    @Override
    public String listDiscoveries(String filters) {
        return ToolResponses.run("listing discoveries", () -> {
            DiscoveryResponseDto response = ilmApiClient
                    .listDiscoveries(SearchRequests.of(new SearchRequestDto(), filters, null, null));
            return titledList(
                    pageTitle("Discoveries", response.getTotalItems(), response.getPageNumber(),
                            response.getTotalPages()),
                    response.getDiscoveries(), "No discoveries found.", InfrastructureServiceImpl::formatDiscovery);
        });
    }

    private static String pageTitle(String title, Long totalItems, Integer pageNumber, Integer totalPages) {
        return title + " (" + totalItems + " total, page " + pageNumber + " of " + totalPages + ")";
    }

    // ILM before 2.20 sends only connectorName; 2.20 adds the connector reference and deprecates the name.
    @SuppressWarnings({"deprecation", "removal"})
    private static String connectorName(AuthorityInstanceDto authority) {
        return authority.getConnector() != null ? authority.getConnector().getName() : authority.getConnectorName();
    }

    private static void formatEntity(StringBuilder sb, EntityInstanceDto entity) {
        item(sb, entity.getName(), entity.getUuid());
        field(sb, INDENT, STATUS, entity.getStatus());
        field(sb, INDENT, CONNECTOR, entity.getConnectorName());
        field(sb, INDENT, KIND, entity.getKind());
    }

    private static void formatDiscovery(StringBuilder sb, DiscoveryListDto discovery) {
        item(sb, discovery.getName(), discovery.getUuid());
        field(sb, INDENT, STATUS, code(discovery.getStatus()));
        field(sb, INDENT, KIND, discovery.getKind());
        field(sb, INDENT, CONNECTOR, discovery.getConnectorName());
        field(sb, INDENT, "Start Time", discovery.getStartTime());
        field(sb, INDENT, "End Time", discovery.getEndTime());
        field(sb, INDENT, "Certificates Discovered", discovery.getTotalCertificatesDiscovered());
    }

    @Override
    public String createGroup(String name, String description, String email) {
        return ToolResponses.run("creating group", () -> {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("A group needs a name.");
            }
            GroupRequestDto request = new GroupRequestDto();
            request.setName(name.trim());
            request.setDescription(description);
            request.setEmail(email);
            UuidDto created = ilmApiClient.createGroup(request);
            return "Created group " + request.getName() + " [" + created.getUuid() + "].";
        });
    }

    private static String functionGroup(FunctionGroupDto group) {
        String name = group.getFunctionGroupCode() == null ? group.getName() : group.getFunctionGroupCode().getCode();
        return group.getKinds() == null || group.getKinds().isEmpty()
                ? name
                : name + " (" + String.join(", ", group.getKinds()) + ")";
    }
}
