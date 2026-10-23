package com.otilm.mcp.tool;

import com.otilm.mcp.service.SchedulerService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class SchedulerTool {
    private static final String UUID = "Existing scheduled job UUID";
    private final SchedulerService service;

    public SchedulerTool(SchedulerService service) {
        this.service = service;
    }

    @McpTool(name = "list_scheduled_jobs",
            description = "List existing scheduled jobs with their cron expressions, enabled state and execution status. Job payloads are omitted.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listScheduledJobs(
            @McpToolParam(description = "Page size, 1 to 1000; default 10", required = false) Integer itemsPerPage,
            @McpToolParam(description = "Page number starting at 1; default 1", required = false) Integer pageNumber) {
        return service.listScheduledJobs(itemsPerPage, pageNumber);
    }

    @McpTool(name = "get_scheduled_job",
            description = "Get an existing scheduled job's schedule, owner UUID and execution status. Serialized job requests and free-form execution messages are omitted.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getScheduledJob(@McpToolParam(description = UUID) String uuid) {
        return service.getScheduledJob(uuid);
    }

    @McpTool(name = "get_scheduled_job_history",
            description = "List execution times, states and result identifiers for an existing scheduled job. Free-form execution messages are omitted.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getScheduledJobHistory(@McpToolParam(description = UUID) String uuid,
            @McpToolParam(description = "Page size, 1 to 1000; default 10", required = false) Integer itemsPerPage,
            @McpToolParam(description = "Page number starting at 1; default 1", required = false) Integer pageNumber) {
        return service.getScheduledJobHistory(uuid, itemsPerPage, pageNumber);
    }

    @McpTool(name = "update_scheduled_job",
            description = "Change only an existing job's cronExpression. Its stored discovery/request configuration is unchanged; schedule discovery through its dedicated endpoint.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateScheduledJob(@McpToolParam(description = UUID) String uuid, @McpToolParam(
            description = "Six-field cron expression, optionally followed by a year") String cronExpression) {
        return service.updateScheduledJob(uuid, cronExpression);
    }

    @McpTool(name = "enable_scheduled_job", description = "Enable an existing scheduled job.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String enableScheduledJob(@McpToolParam(description = UUID) String uuid) {
        return service.enableScheduledJob(uuid);
    }

    @McpTool(name = "disable_scheduled_job", description = "Disable an existing scheduled job.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String disableScheduledJob(@McpToolParam(description = UUID) String uuid) {
        return service.disableScheduledJob(uuid);
    }

    @McpTool(name = "delete_scheduled_job",
            description = "Delete an existing scheduled job. Discovery scheduling creates jobs through the discovery endpoint.")
    public String deleteScheduledJob(@McpToolParam(description = UUID) String uuid) {
        return service.deleteScheduledJob(uuid);
    }
}
