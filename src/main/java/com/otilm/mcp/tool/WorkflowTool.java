package com.otilm.mcp.tool;

import com.otilm.mcp.service.TriggerInput;
import com.otilm.mcp.service.WorkflowService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class WorkflowTool {

    private static final String EVENT_CODES = "certificate_status_changed, certificate_action_performed, "
            + "certificate_discovered, certificate_expiring, certificate_not_compliant, certificate_uploaded, "
            + "certificate_registered, discovery_finished, approval_requested, approval_closed, comment_created, "
            + "comment_resolved, cbom_synced, crypto_asset_pqc_not_ready, scheduled_job_finished";

    private final WorkflowService workflowService;

    public WorkflowTool(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @McpTool(name = "list_triggers",
            description = "List the workflow triggers in ILM with their event, resource and description, optionally for one event. Use get_trigger to see what a trigger checks and does.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listTriggers(@McpToolParam(description = "Event code to filter by. One of: " + EVENT_CODES,
            required = false) String event) {
        return workflowService.listTriggers(event);
    }

    @McpTool(name = "get_trigger",
            description = "Explain one workflow trigger in plain words: the event that fires it, the conditions that must hold, what it does (set fields, send a notification, or ignore the object), and whether it is attached to its event platform-wide.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getTrigger(@McpToolParam(description = "The UUID of the trigger") String uuid) {
        return workflowService.getTrigger(uuid);
    }

    @McpTool(name = "list_event_triggers",
            description = "Show which triggers run when an event happens, in the order ILM runs them, for one event or for every event that has triggers. Covers platform-wide triggers; discoveries, RA profiles and groups can carry their own.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listEventTriggers(
            @McpToolParam(description = "Event code. One of: " + EVENT_CODES + ". Omit for every event with triggers.",
                    required = false) String event) {
        return workflowService.listEventTriggers(event);
    }

    @McpTool(name = "list_trigger_fields",
            description = "List the fields a trigger for an event can check in its conditions and set in its setFields, with their operators and up to ten possible values each. list_groups, list_users and list_ra_profiles give the complete lists of groups, users and RA profiles.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listTriggerFields(@McpToolParam(description = "Event code. One of: " + EVENT_CODES) String event) {
        return workflowService.listTriggerFields(event);
    }

    @McpTool(name = "create_trigger",
            description = "Set up an ILM workflow in one call: when the event happens and the conditions hold, set fields, send a notification, or ignore the object. Creates the condition, rule, executions, action and trigger behind it, named after the trigger, and removes them again if a step fails. By default it also attaches the trigger to its event platform-wide; ILM then re-saves every platform trigger of that event to run as the identity that made the change. Conditions and setFields can only use the fields list_trigger_fields lists.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createTrigger(
            @McpToolParam(
                    description = "Name of the trigger; the objects created behind it are named after it") String name,
            @McpToolParam(description = "Event that fires the trigger. One of: " + EVENT_CODES) String event,
            @McpToolParam(
                    description = "JSON array of conditions that must all hold, in the search filter format, e.g. [{\"fieldSource\":\"property\",\"fieldIdentifier\":\"COMMON_NAME\",\"condition\":\"ENDS_WITH\",\"value\":\".example.com\"}]. Omit to match every object of the event.",
                    required = false) String conditions,
            @McpToolParam(
                    description = "JSON array of fields to set, e.g. [{\"fieldSource\":\"property\",\"fieldIdentifier\":\"GROUP_NAME\",\"value\":[\"<group uuid>\"]}, {\"fieldSource\":\"property\",\"fieldIdentifier\":\"OWNER\",\"value\":\"<user uuid>\"}, {\"fieldSource\":\"property\",\"fieldIdentifier\":\"RA_PROFILE_NAME\",\"value\":\"<RA profile uuid>\"}, {\"fieldSource\":\"custom\",\"fieldIdentifier\":\"<attribute name>|<CONTENT TYPE>\",\"value\":\"<value>\"}]",
                    required = false) String setFields,
            @McpToolParam(
                    description = "UUID of the notification profile to send a notification through; create_notification_profile makes one",
                    required = false) String notificationProfileUuid,
            @McpToolParam(
                    description = "true to ignore matching objects: the event's other platform triggers do not run for them, discovery does not import them, and uploads are not stored; needs conditions and nothing to set or send",
                    required = false) Boolean ignore,
            @McpToolParam(description = "Attach the trigger to its event platform-wide (default true)",
                    required = false) Boolean attach) {
        return workflowService
                .createTrigger(
                        new TriggerInput(name, event, conditions, setFields, notificationProfileUuid, ignore, attach));
    }

    @McpTool(name = "attach_trigger",
            description = "Attach a trigger to its event platform-wide, at the end of the event's list, so it runs for every object of that event. ILM then re-saves every platform trigger of that event to run as the identity that made the change.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String attachTrigger(@McpToolParam(description = "The UUID of the trigger") String uuid) {
        return workflowService.attachTrigger(uuid);
    }

    @McpTool(name = "detach_trigger",
            description = "Detach a trigger from its event's platform-wide list. The trigger stops running but is kept, and attach_trigger brings it back. ILM then re-saves the event's remaining platform triggers to run as the identity that made the change.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String detachTrigger(@McpToolParam(description = "The UUID of the trigger") String uuid) {
        return workflowService.detachTrigger(uuid);
    }

    @McpTool(name = "delete_trigger",
            description = "Delete a trigger that is no longer attached, together with the condition, rule, executions and action named after it. Detach it first with detach_trigger.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteTrigger(@McpToolParam(description = "The UUID of the trigger") String uuid) {
        return workflowService.deleteTrigger(uuid);
    }
}
