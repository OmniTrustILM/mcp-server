package com.otilm.mcp.service;

public interface WorkflowService {

    String listTriggers(String event);

    String getTrigger(String uuid);

    String listEventTriggers(String event);

    String createTrigger(TriggerInput input);

    String attachTrigger(String uuid);

    String detachTrigger(String uuid);

    String deleteTrigger(String uuid);
}
