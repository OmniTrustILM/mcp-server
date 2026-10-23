package com.otilm.mcp.service;

public interface SchedulerService {
    String listScheduledJobs(Integer itemsPerPage, Integer pageNumber);

    String getScheduledJob(String uuid);

    String getScheduledJobHistory(String uuid, Integer itemsPerPage, Integer pageNumber);

    String updateScheduledJob(String uuid, String cronExpression);

    String enableScheduledJob(String uuid);

    String disableScheduledJob(String uuid);

    String deleteScheduledJob(String uuid);
}
