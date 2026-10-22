package com.otilm.mcp.service;

public interface AttributeService {

    String listCustomAttributes();

    String createCustomAttribute(String name, String label, String contentType, String resources, String description);
}
