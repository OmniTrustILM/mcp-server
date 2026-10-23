package com.otilm.mcp.service;

import com.otilm.mcp.client.ConfigurationApiClient;
import java.util.Map;
import org.quartz.CronExpression;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
public class SchedulerServiceImpl implements SchedulerService {
    private static final String UUID_ENDPOINT = "/{uuid}";
    private static final String JOB_UUID = "Job UUID";
    private static final String SCHEDULED_JOB = "Scheduled job [";

    private static final String JOBS = "/v1/scheduler/jobs";
    private static final String[] FIELDS = {
            "uuid",
            "jobName",
            "jobType",
            "cronExpression",
            "enabled",
            "oneTime",
            "system",
            "lastExecutionStatus",
            "scheduleState",
            "nextFireTime",
            "previousFireTime",
            "lastExecutionStartTime",
            "lastSkippedAt",
            "userUuid"};
    private final ConfigurationApiClient client;

    public SchedulerServiceImpl(ConfigurationApiClient client) {
        this.client = client;
    }

    @Override
    public String listScheduledJobs(Integer itemsPerPage, Integer pageNumber) {
        return ConfigurationSupport.run("listing scheduled jobs", () -> {
            String query = pagination(itemsPerPage, pageNumber);
            JsonNode result = client.get(JOBS + query);
            return ConfigurationSupport.list("Scheduled jobs", result, FIELDS);
        });
    }

    @Override
    public String getScheduledJob(String uuid) {
        return ConfigurationSupport
                .run("retrieving scheduled job", () -> ConfigurationSupport
                        .describe("Scheduled job",
                                client.get(JOBS + UUID_ENDPOINT, ConfigurationSupport.uuid(uuid, JOB_UUID)), FIELDS));
    }

    @Override
    public String getScheduledJobHistory(String uuid, Integer itemsPerPage, Integer pageNumber) {
        return ConfigurationSupport.run("retrieving scheduled job history", () -> {
            String id = ConfigurationSupport.uuid(uuid, JOB_UUID);
            String query = pagination(itemsPerPage, pageNumber);
            JsonNode result = client.get(JOBS + "/{uuid}/history" + query, id);
            return ConfigurationSupport
                    .list("Scheduled job history", result, "jobUuid", "startTime", "endTime", "status",
                            "resultObjectType", "resultObjectIdentification");
        });
    }

    @Override
    public String updateScheduledJob(String uuid, String cronExpression) {
        return ConfigurationSupport.run("updating scheduled job", () -> {
            String id = ConfigurationSupport.uuid(uuid, JOB_UUID);
            String cron = cron(cronExpression);
            client.put(JOBS + UUID_ENDPOINT, Map.of("cronExpression", cron), id);
            return SCHEDULED_JOB + id + "] cron updated. The stored job request is unchanged.";
        });
    }

    @Override
    public String enableScheduledJob(String uuid) {
        return state(uuid, true);
    }

    @Override
    public String disableScheduledJob(String uuid) {
        return state(uuid, false);
    }

    private String state(String uuid, boolean enabled) {
        return ConfigurationSupport.run("changing scheduled job state", () -> {
            String id = ConfigurationSupport.uuid(uuid, JOB_UUID);
            client.patch(JOBS + "/{uuid}/" + (enabled ? "enable" : "disable"), null, id);
            return SCHEDULED_JOB + id + "] " + (enabled ? "enabled." : "disabled.");
        });
    }

    @Override
    public String deleteScheduledJob(String uuid) {
        return ConfigurationSupport.run("deleting scheduled job", () -> {
            String id = ConfigurationSupport.uuid(uuid, JOB_UUID);
            client.delete(JOBS + UUID_ENDPOINT, id);
            return SCHEDULED_JOB + id + "] deleted.";
        });
    }

    private static String pagination(Integer itemsPerPage, Integer pageNumber) {
        int size = itemsPerPage == null ? 10 : itemsPerPage;
        int page = pageNumber == null ? 1 : pageNumber;
        if (size < 1 || size > 1000 || page < 1) {
            throw new IllegalArgumentException("Page size must be 1 to 1000 and page number must be positive.");
        }
        return "?itemsPerPage=" + size + "&pageNumber=" + page;
    }

    private static String cron(String expression) {
        String value = ConfigurationSupport.required(expression, "Cron expression");
        if (!CronExpression.isValidExpression(value)) {
            throw new IllegalArgumentException("Cron expression is invalid.");
        }
        return value;
    }
}
