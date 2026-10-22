package com.otilm.mcp.service;

public interface InfrastructureService {

    String listAuthorities();

    String listRaProfiles();

    String listConnectors();

    String listGroups();

    String listEntities(String filters);

    String listCredentials();

    String listTokenInstances();

    String listDiscoveries(String filters);

    String createGroup(String name, String description, String email);
}
