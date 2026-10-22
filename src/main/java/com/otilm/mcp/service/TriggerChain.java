package com.otilm.mcp.service;

import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.workflows.ActionDetailDto;
import com.otilm.api.model.core.workflows.ActionRequestDto;
import com.otilm.api.model.core.workflows.ConditionDto;
import com.otilm.api.model.core.workflows.ConditionRequestDto;
import com.otilm.api.model.core.workflows.ConditionType;
import com.otilm.api.model.core.workflows.ExecutionDto;
import com.otilm.api.model.core.workflows.ExecutionItemRequestDto;
import com.otilm.api.model.core.workflows.ExecutionRequestDto;
import com.otilm.api.model.core.workflows.ExecutionType;
import com.otilm.api.model.core.workflows.RuleDetailDto;
import com.otilm.api.model.core.workflows.RuleRequestDto;
import com.otilm.api.model.core.workflows.TriggerDetailDto;
import com.otilm.api.model.core.workflows.TriggerRequestDto;
import com.otilm.api.model.core.workflows.TriggerType;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.client.WorkflowCollection;
import com.otilm.mcp.service.TriggerRequests.TriggerPlan;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates the ILM objects behind one trigger, in order, and remembers them so that a failed creation can be undone.
 */
final class TriggerChain {

    static final String DESCRIPTION = "Created with the ILM MCP server.";

    private final IlmApiClient ilmApiClient;
    private final List<Created> created = new ArrayList<>();

    TriggerChain(IlmApiClient ilmApiClient) {
        this.ilmApiClient = ilmApiClient;
    }

    private record Created(WorkflowCollection collection, String kind, String name, String uuid) {

        String label() {
            return kind + " " + name + " [" + uuid + "]";
        }
    }

    TriggerDetailDto create(TriggerPlan plan) {
        Resource resource = plan.event().getResource();
        List<String> rules = plan.conditions().isEmpty() ? List.of() : List.of(rule(plan, resource));
        List<String> executions = new ArrayList<>();
        if (!plan.setFields().isEmpty()) {
            executions
                    .add(execution(plan.name() + " - set fields", ExecutionType.SET_FIELD, resource, plan.setFields()));
        }
        if (plan.notificationProfileUuid() != null) {
            ExecutionItemRequestDto item = new ExecutionItemRequestDto();
            item.setNotificationProfileUuid(plan.notificationProfileUuid());
            executions
                    .add(execution(plan.name() + " - notify", ExecutionType.SEND_NOTIFICATION, resource,
                            List.of(item)));
        }
        List<String> actions = executions.isEmpty() ? List.of() : List.of(action(plan.name(), resource, executions));

        TriggerRequestDto request = new TriggerRequestDto();
        request.setName(plan.name());
        request.setDescription(DESCRIPTION);
        request.setType(TriggerType.EVENT);
        request.setResource(resource);
        request.setEvent(plan.event());
        request.setIgnoreTrigger(plan.ignore());
        request.setRulesUuids(rules);
        request.setActionsUuids(actions);
        TriggerDetailDto trigger = ilmApiClient.createTrigger(request);
        remember(WorkflowCollection.TRIGGERS, "trigger", trigger.getName(), trigger.getUuid());
        return trigger;
    }

    boolean isEmpty() {
        return created.isEmpty();
    }

    String undo() {
        List<String> kept = new ArrayList<>();
        for (Created part : created.reversed()) {
            try {
                ilmApiClient.deleteWorkflowObject(part.collection(), part.uuid());
            } catch (RuntimeException e) {
                kept.add("- " + part.label() + ": " + ToolResponses.errorText("removing the " + part.kind(), e));
            }
        }
        created.clear();
        return kept.isEmpty() ? "Removed what this call had created." : "Could not remove:\n" + String.join("\n", kept);
    }

    private String rule(TriggerPlan plan, Resource resource) {
        ConditionRequestDto condition = new ConditionRequestDto();
        condition.setName(plan.name());
        condition.setDescription(DESCRIPTION);
        condition.setType(ConditionType.CHECK_FIELD);
        condition.setResource(resource);
        condition.setItems(plan.conditions());
        ConditionDto createdCondition = ilmApiClient.createCondition(condition);
        remember(WorkflowCollection.CONDITIONS, "condition", createdCondition.getName(), createdCondition.getUuid());

        RuleRequestDto rule = new RuleRequestDto();
        rule.setName(plan.name());
        rule.setDescription(DESCRIPTION);
        rule.setResource(resource);
        rule.setConditionsUuids(List.of(createdCondition.getUuid()));
        RuleDetailDto createdRule = ilmApiClient.createRule(rule);
        remember(WorkflowCollection.RULES, "rule", createdRule.getName(), createdRule.getUuid());
        return createdRule.getUuid();
    }

    private String execution(String name, ExecutionType type, Resource resource, List<ExecutionItemRequestDto> items) {
        ExecutionRequestDto execution = new ExecutionRequestDto();
        execution.setName(name);
        execution.setDescription(DESCRIPTION);
        execution.setType(type);
        execution.setResource(resource);
        execution.setItems(items);
        ExecutionDto createdExecution = ilmApiClient.createExecution(execution);
        remember(WorkflowCollection.EXECUTIONS, "execution", createdExecution.getName(), createdExecution.getUuid());
        return createdExecution.getUuid();
    }

    private String action(String name, Resource resource, List<String> executions) {
        ActionRequestDto action = new ActionRequestDto();
        action.setName(name);
        action.setDescription(DESCRIPTION);
        action.setResource(resource);
        action.setExecutionsUuids(executions);
        ActionDetailDto createdAction = ilmApiClient.createAction(action);
        remember(WorkflowCollection.ACTIONS, "action", createdAction.getName(), createdAction.getUuid());
        return createdAction.getUuid();
    }

    private void remember(WorkflowCollection collection, String kind, String name, String uuid) {
        created.add(new Created(collection, kind, name, uuid));
    }
}
