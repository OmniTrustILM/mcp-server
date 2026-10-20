package com.otilm.mcp.service;

import com.czertainly.api.model.client.certificate.DiscoveryResponseDto;
import com.czertainly.api.model.client.certificate.EntityInstanceResponseDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.client.discovery.DiscoveryHistoryDto;
import com.czertainly.api.model.core.authority.AuthorityInstanceDto;
import com.czertainly.api.model.core.certificate.group.GroupDto;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.api.model.core.credential.CredentialDto;
import com.czertainly.api.model.core.cryptography.token.TokenInstanceDto;
import com.czertainly.api.model.core.entity.EntityInstanceDto;
import com.czertainly.api.model.core.raprofile.RaProfileDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otilm.mcp.client.IlmApiClient;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class InfrastructureServiceImpl implements InfrastructureService {

    private static final Logger logger = LoggerFactory.getLogger(InfrastructureServiceImpl.class);

    private final IlmApiClient ilmApiClient;
    private final ObjectMapper objectMapper;

    public InfrastructureServiceImpl(IlmApiClient ilmApiClient, ObjectMapper objectMapper) {
        this.ilmApiClient = ilmApiClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String listAuthorities() {
        try {
            List<AuthorityInstanceDto> authorities = ilmApiClient.listAuthorities();
            return formatAuthorities(authorities);
        } catch (Exception e) {
            logger.error("Failed to list authorities", e);
            return "Error listing authorities: " + e.getMessage();
        }
    }

    @Override
    public String listRaProfiles() {
        try {
            List<RaProfileDto> raProfiles = ilmApiClient.listRaProfiles();
            return formatRaProfiles(raProfiles);
        } catch (Exception e) {
            logger.error("Failed to list RA profiles", e);
            return "Error listing RA profiles: " + e.getMessage();
        }
    }

    @Override
    public String listConnectors() {
        try {
            List<ConnectorDto> connectors = ilmApiClient.listConnectors();
            return formatConnectors(connectors);
        } catch (Exception e) {
            logger.error("Failed to list connectors", e);
            return "Error listing connectors: " + e.getMessage();
        }
    }

    @Override
    public String listGroups() {
        try {
            List<GroupDto> groups = ilmApiClient.listGroups();
            return formatGroups(groups);
        } catch (Exception e) {
            logger.error("Failed to list groups", e);
            return "Error listing groups: " + e.getMessage();
        }
    }

    @Override
    public String listEntities(String filters) {
        try {
            SearchRequestDto request = new SearchRequestDto();
            request.setFilters(SearchFilterParser.parseFilters(filters, objectMapper));
            EntityInstanceResponseDto response = ilmApiClient.listEntities(request);
            return formatEntities(response);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            logger.error("Failed to list entities", e);
            return "Error listing entities: " + e.getMessage();
        }
    }

    @Override
    public String listCredentials() {
        try {
            List<CredentialDto> credentials = ilmApiClient.listCredentials();
            return formatCredentials(credentials);
        } catch (Exception e) {
            logger.error("Failed to list credentials", e);
            return "Error listing credentials: " + e.getMessage();
        }
    }

    @Override
    public String listTokenInstances() {
        try {
            List<TokenInstanceDto> tokens = ilmApiClient.listTokenInstances();
            return formatTokenInstances(tokens);
        } catch (Exception e) {
            logger.error("Failed to list token instances", e);
            return "Error listing token instances: " + e.getMessage();
        }
    }

    @Override
    public String listDiscoveries(String filters) {
        try {
            SearchRequestDto request = new SearchRequestDto();
            request.setFilters(SearchFilterParser.parseFilters(filters, objectMapper));
            DiscoveryResponseDto response = ilmApiClient.listDiscoveries(request);
            return formatDiscoveries(response);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            logger.error("Failed to list discoveries", e);
            return "Error listing discoveries: " + e.getMessage();
        }
    }

    private String formatAuthorities(List<AuthorityInstanceDto> authorities) {
        StringBuilder sb = new StringBuilder();
        sb.append("Authority Instances (").append(authorities.size()).append(")\n");
        sb.append("=======================\n\n");

        if (authorities.isEmpty()) {
            sb.append("No authority instances found.\n");
            return sb.toString();
        }

        for (AuthorityInstanceDto auth : authorities) {
            sb.append("- ").append(auth.getName() != null ? auth.getName() : "N/A");
            sb.append(" [").append(auth.getUuid()).append("]\n");
            sb.append("  Status: ").append(auth.getStatus()).append("\n");
            if (auth.getConnectorName() != null) {
                sb.append("  Connector: ").append(auth.getConnectorName()).append("\n");
            }
            if (auth.getKind() != null) {
                sb.append("  Kind: ").append(auth.getKind()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String formatRaProfiles(List<RaProfileDto> raProfiles) {
        StringBuilder sb = new StringBuilder();
        sb.append("RA Profiles (").append(raProfiles.size()).append(")\n");
        sb.append("=======================\n\n");

        if (raProfiles.isEmpty()) {
            sb.append("No RA profiles found.\n");
            return sb.toString();
        }

        for (RaProfileDto profile : raProfiles) {
            sb.append("- ").append(profile.getName() != null ? profile.getName() : "N/A");
            sb.append(" [").append(profile.getUuid()).append("]\n");
            sb.append("  Enabled: ").append(profile.getEnabled()).append("\n");
            if (profile.getDescription() != null) {
                sb.append("  Description: ").append(profile.getDescription()).append("\n");
            }
            if (profile.getAuthorityInstanceName() != null) {
                sb.append("  Authority: ").append(profile.getAuthorityInstanceName()).append("\n");
            }
            if (profile.getEnabledProtocols() != null && !profile.getEnabledProtocols().isEmpty()) {
                sb.append("  Protocols: ").append(String.join(", ", profile.getEnabledProtocols())).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String formatConnectors(List<ConnectorDto> connectors) {
        StringBuilder sb = new StringBuilder();
        sb.append("Connectors (").append(connectors.size()).append(")\n");
        sb.append("=======================\n\n");

        if (connectors.isEmpty()) {
            sb.append("No connectors found.\n");
            return sb.toString();
        }

        for (ConnectorDto connector : connectors) {
            sb.append("- ").append(connector.getName() != null ? connector.getName() : "N/A");
            sb.append(" [").append(connector.getUuid()).append("]\n");
            sb
                    .append("  Status: ")
                    .append(connector.getStatus() != null ? connector.getStatus().getCode() : "N/A")
                    .append("\n");
            if (connector.getUrl() != null) {
                sb.append("  URL: ").append(connector.getUrl()).append("\n");
            }
            sb
                    .append("  Auth Type: ")
                    .append(connector.getAuthType() != null ? connector.getAuthType().getCode() : "N/A")
                    .append("\n");
            if (connector.getFunctionGroups() != null && !connector.getFunctionGroups().isEmpty()) {
                sb.append("  Function Groups: ").append(connector.getFunctionGroups().size()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String formatGroups(List<GroupDto> groups) {
        StringBuilder sb = new StringBuilder();
        sb.append("Groups (").append(groups.size()).append(")\n");
        sb.append("=======================\n\n");

        if (groups.isEmpty()) {
            sb.append("No groups found.\n");
            return sb.toString();
        }

        for (GroupDto group : groups) {
            sb.append("- ").append(group.getName() != null ? group.getName() : "N/A");
            sb.append(" [").append(group.getUuid()).append("]\n");
            if (group.getDescription() != null) {
                sb.append("  Description: ").append(group.getDescription()).append("\n");
            }
            if (group.getEmail() != null) {
                sb.append("  Email: ").append(group.getEmail()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String formatEntities(EntityInstanceResponseDto response) {
        StringBuilder sb = new StringBuilder();
        sb.append("Entity Instances (").append(response.getTotalItems()).append(" total");
        sb
                .append(", page ")
                .append(response.getPageNumber())
                .append(" of ")
                .append(response.getTotalPages())
                .append(")\n");
        sb.append("=======================\n\n");

        if (response.getEntities() == null || response.getEntities().isEmpty()) {
            sb.append("No entity instances found.\n");
            return sb.toString();
        }

        for (EntityInstanceDto entity : response.getEntities()) {
            sb.append("- ").append(entity.getName() != null ? entity.getName() : "N/A");
            sb.append(" [").append(entity.getUuid()).append("]\n");
            sb.append("  Status: ").append(entity.getStatus()).append("\n");
            if (entity.getConnectorName() != null) {
                sb.append("  Connector: ").append(entity.getConnectorName()).append("\n");
            }
            if (entity.getKind() != null) {
                sb.append("  Kind: ").append(entity.getKind()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String formatCredentials(List<CredentialDto> credentials) {
        StringBuilder sb = new StringBuilder();
        sb.append("Credentials (").append(credentials.size()).append(")\n");
        sb.append("=======================\n\n");

        if (credentials.isEmpty()) {
            sb.append("No credentials found.\n");
            return sb.toString();
        }

        for (CredentialDto cred : credentials) {
            sb.append("- ").append(cred.getName() != null ? cred.getName() : "N/A");
            sb.append(" [").append(cred.getUuid()).append("]\n");
            sb.append("  Enabled: ").append(cred.getEnabled()).append("\n");
            if (cred.getKind() != null) {
                sb.append("  Kind: ").append(cred.getKind()).append("\n");
            }
            if (cred.getConnectorName() != null) {
                sb.append("  Connector: ").append(cred.getConnectorName()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String formatTokenInstances(List<TokenInstanceDto> tokens) {
        StringBuilder sb = new StringBuilder();
        sb.append("Token Instances (").append(tokens.size()).append(")\n");
        sb.append("=======================\n\n");

        if (tokens.isEmpty()) {
            sb.append("No token instances found.\n");
            return sb.toString();
        }

        for (TokenInstanceDto token : tokens) {
            sb.append("- ").append(token.getName() != null ? token.getName() : "N/A");
            sb.append(" [").append(token.getUuid()).append("]\n");
            sb
                    .append("  Status: ")
                    .append(token.getStatus() != null ? token.getStatus().getCode() : "N/A")
                    .append("\n");
            if (token.getConnectorName() != null) {
                sb.append("  Connector: ").append(token.getConnectorName()).append("\n");
            }
            if (token.getKind() != null) {
                sb.append("  Kind: ").append(token.getKind()).append("\n");
            }
            if (token.getTokenProfiles() != null) {
                sb.append("  Token Profiles: ").append(token.getTokenProfiles()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String formatDiscoveries(DiscoveryResponseDto response) {
        StringBuilder sb = new StringBuilder();
        sb.append("Discoveries (").append(response.getTotalItems()).append(" total");
        sb
                .append(", page ")
                .append(response.getPageNumber())
                .append(" of ")
                .append(response.getTotalPages())
                .append(")\n");
        sb.append("=======================\n\n");

        if (response.getDiscoveries() == null || response.getDiscoveries().isEmpty()) {
            sb.append("No discoveries found.\n");
            return sb.toString();
        }

        for (DiscoveryHistoryDto discovery : response.getDiscoveries()) {
            sb.append("- ").append(discovery.getName() != null ? discovery.getName() : "N/A");
            sb.append(" [").append(discovery.getUuid()).append("]\n");
            sb
                    .append("  Status: ")
                    .append(discovery.getStatus() != null ? discovery.getStatus().getCode() : "N/A")
                    .append("\n");
            if (discovery.getKind() != null) {
                sb.append("  Kind: ").append(discovery.getKind()).append("\n");
            }
            if (discovery.getConnectorName() != null) {
                sb.append("  Connector: ").append(discovery.getConnectorName()).append("\n");
            }
            if (discovery.getStartTime() != null) {
                sb.append("  Start Time: ").append(discovery.getStartTime()).append("\n");
            }
            if (discovery.getEndTime() != null) {
                sb.append("  End Time: ").append(discovery.getEndTime()).append("\n");
            }
            if (discovery.getTotalCertificatesDiscovered() != null) {
                sb
                        .append("  Certificates Discovered: ")
                        .append(discovery.getTotalCertificatesDiscovered())
                        .append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
