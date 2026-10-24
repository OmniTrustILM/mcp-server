package com.otilm.mcp.tool;

import com.otilm.mcp.service.InfrastructureService;
import org.springframework.stereotype.Component;

@Component
public class InfrastructureTool {

    private final InfrastructureService infrastructureService;

    public InfrastructureTool(InfrastructureService infrastructureService) {
        this.infrastructureService = infrastructureService;
    }

    public String listAuthorities() {
        return infrastructureService.listAuthorities();
    }

    public String listRaProfiles() {
        return infrastructureService.listRaProfiles();
    }

    public String listConnectors() {
        return infrastructureService.listConnectors();
    }

    public String listGroups() {
        return infrastructureService.listGroups();
    }

    public String listEntities(String filters) {
        return infrastructureService.listEntities(filters);
    }

    public String listCredentials() {
        return infrastructureService.listCredentials();
    }

    public String listTokenInstances() {
        return infrastructureService.listTokenInstances();
    }

    public String listDiscoveries(String filters) {
        return infrastructureService.listDiscoveries(filters);
    }

    public String createGroup(String name, String description, String email) {
        return infrastructureService.createGroup(name, description, email);
    }
}
