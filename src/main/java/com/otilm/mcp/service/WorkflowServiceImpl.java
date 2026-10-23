package com.otilm.mcp.service;

import com.otilm.api.model.common.NameAndUuidDto;
import com.otilm.api.model.core.other.ResourceEvent;
import com.otilm.api.model.core.settings.EventSettingsDto;
import com.otilm.api.model.core.settings.EventsSettingsDto;
import com.otilm.api.model.core.workflows.ActionDetailDto;
import com.otilm.api.model.core.workflows.RuleDetailDto;
import com.otilm.api.model.core.workflows.TriggerDetailDto;
import com.otilm.api.model.core.workflows.TriggerDto;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.client.WorkflowCollection;
import com.otilm.mcp.service.TriggerRequests.TriggerPlan;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import static com.otilm.mcp.service.TextFormat.field;
import static com.otilm.mcp.service.TextFormat.item;
import static com.otilm.mcp.service.TextFormat.title;
import static com.otilm.mcp.service.TextFormat.titledList;

@Service
public class WorkflowServiceImpl implements WorkflowService {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowServiceImpl.class);

    private static final String INDENT = "  ";
    private static final String CREATING_TRIGGER = "creating trigger";
    private static final String DELETING_TRIGGER = "deleting trigger";

    private final IlmApiClient ilmApiClient;

    // ILM replaces an event's whole trigger list on each change, so this server's own changes to it run one at a time.
    private final Object eventListLock = new Object();

    public WorkflowServiceImpl(IlmApiClient ilmApiClient) {
        this.ilmApiClient = ilmApiClient;
    }

    @Override
    public String createTrigger(TriggerInput input) {
        return ToolResponses.run(CREATING_TRIGGER, () -> {
            TriggerPlan plan = TriggerRequests.plan(input);
            checkReferences(plan);
            TriggerChain chain = new TriggerChain(ilmApiClient);
            try {
                TriggerDetailDto trigger = chain.create(plan);
                String attachment = plan.attach()
                        ? attachCreated(trigger.getUuid(), plan.event())
                        : "Not attached. Attach it with attach_trigger when it should start running.\n";
                StringBuilder sb = new StringBuilder();
                sb.append("Created trigger ").append(trigger.getName()).append(" [").append(trigger.getUuid());
                sb.append("].\n\n");
                WorkflowText.trigger(sb, trigger);
                return sb.append(attachment).toString();
            } catch (RuntimeException e) {
                String error = ToolResponses.errorText(CREATING_TRIGGER, e);
                return chain.isEmpty() ? error : error + "\n" + chain.undo();
            }
        });
    }

    private void checkReferences(TriggerPlan plan) {
        String resource = plan.event().getResource().getCode();
        if (!plan.conditions().isEmpty()) {
            TriggerRequests.checkConditionFields(plan.conditions(), ilmApiClient.listRuleFilterFields(resource, false));
        }
        if (!plan.setFields().isEmpty()) {
            TriggerRequests.checkSetFields(plan.setFields(), ilmApiClient.listRuleFilterFields(resource, true));
        }
        if (plan.notificationProfileUuid() != null) {
            try {
                ilmApiClient.getNotificationProfile(plan.notificationProfileUuid());
            } catch (HttpClientErrorException.NotFound e) {
                throw new IllegalArgumentException("Notification profile %s was not found; list_notification_profiles "
                        .formatted(plan.notificationProfileUuid()) + "lists them.", e);
            }
        }
    }

    private String attachCreated(String triggerUuid, ResourceEvent event) {
        OptionalInt position = attachToEvent(triggerUuid, event);
        if (position.isEmpty()) {
            return "Already attached to " + event.getCode() + " platform-wide.\n";
        }
        return "Attached to " + event.getCode() + " platform-wide (position " + position.getAsInt() + " of "
                + position.getAsInt() + ").\n" + WorkflowText.runAsNote(event) + "\n";
    }

    /**
     * Adds the trigger at the end of its event's platform list, keeping the others; returns its position, or empty when
     * it was already there.
     */
    private OptionalInt attachToEvent(String triggerUuid, ResourceEvent event) {
        synchronized (eventListLock) {
            List<UUID> attached = new ArrayList<>(WorkflowText.eventTriggers(ilmApiClient.getEventsSettings(), event));
            UUID uuid = UUID.fromString(triggerUuid);
            if (attached.contains(uuid)) {
                return OptionalInt.empty();
            }
            attached.add(uuid);
            updateEventTriggers(event, attached);
            return OptionalInt.of(attached.size());
        }
    }

    /**
     * Removes the trigger from its event's platform list, keeping the others; returns how many remain, or empty when it
     * was not there.
     */
    private OptionalInt detachFromEvent(String triggerUuid, ResourceEvent event) {
        synchronized (eventListLock) {
            List<UUID> attached = new ArrayList<>(WorkflowText.eventTriggers(ilmApiClient.getEventsSettings(), event));
            if (!attached.remove(UUID.fromString(triggerUuid))) {
                return OptionalInt.empty();
            }
            updateEventTriggers(event, attached);
            return OptionalInt.of(attached.size());
        }
    }

    private void updateEventTriggers(ResourceEvent event, List<UUID> triggers) {
        EventSettingsDto request = new EventSettingsDto();
        request.setEvent(event);
        request.setTriggerUuids(triggers);
        ilmApiClient.updateEventSettings(request);
    }

    @Override
    public String attachTrigger(String uuid) {
        return ToolResponses.run("attaching trigger", () -> {
            TriggerDetailDto trigger = ilmApiClient.getTrigger(uuid);
            ResourceEvent event = trigger.getEvent();
            if (event == null) {
                return noEvent(trigger);
            }
            OptionalInt position = attachToEvent(trigger.getUuid(), event);
            if (position.isEmpty()) {
                return label(trigger) + " is already attached to " + event.getCode()
                        + " platform-wide; nothing changed.";
            }
            return "Attached " + label(trigger) + " to " + event.getCode() + " platform-wide (position "
                    + position.getAsInt() + " of " + position.getAsInt() + ").\n" + WorkflowText.runAsNote(event)
                    + "\n";
        });
    }

    @Override
    public String detachTrigger(String uuid) {
        return ToolResponses.run("detaching trigger", () -> {
            TriggerDetailDto trigger = ilmApiClient.getTrigger(uuid);
            ResourceEvent event = trigger.getEvent();
            if (event == null) {
                return noEvent(trigger);
            }
            OptionalInt remaining = detachFromEvent(trigger.getUuid(), event);
            if (remaining.isEmpty()) {
                return label(trigger) + " is not attached to " + event.getCode() + " platform-wide; nothing changed.";
            }
            String detached = "Detached " + label(trigger) + " from " + event.getCode() + "; " + remaining.getAsInt()
                    + " platform triggers remain.\n";
            return remaining.getAsInt() == 0 ? detached : detached + WorkflowText.runAsNote(event) + "\n";
        });
    }

    @Override
    public String deleteTrigger(String uuid) {
        return ToolResponses.run(DELETING_TRIGGER, () -> {
            TriggerDetailDto trigger = ilmApiClient.getTrigger(uuid);
            try {
                ilmApiClient.deleteWorkflowObject(WorkflowCollection.TRIGGERS, trigger.getUuid());
            } catch (RuntimeException e) {
                return ToolResponses.errorText(DELETING_TRIGGER, e) + "\nNothing else was deleted. If the trigger is "
                        + "still attached, detach it first: detach_trigger for its platform attachment; discoveries, "
                        + "RA profiles and groups keep their own.";
            }
            PartDeletion parts = new PartDeletion(trigger.getName());
            // All rules go before any condition and all actions before any execution, so a shared part is no longer
            // in use when its turn comes.
            List<RuleDetailDto> rules = list(trigger.getRules());
            List<ActionDetailDto> actions = list(trigger.getActions());
            rules.forEach(rule -> parts.delete(WorkflowCollection.RULES, "rule", rule));
            rules
                    .stream()
                    .flatMap(rule -> list(rule.getConditions()).stream())
                    .forEach(condition -> parts.delete(WorkflowCollection.CONDITIONS, "condition", condition));
            actions.forEach(action -> parts.delete(WorkflowCollection.ACTIONS, "action", action));
            actions
                    .stream()
                    .flatMap(action -> list(action.getExecutions()).stream())
                    .forEach(execution -> parts.delete(WorkflowCollection.EXECUTIONS, "execution", execution));
            return "Deleted trigger " + label(trigger) + "." + parts.report();
        });
    }

    /**
     * Deletes the parts of a deleted trigger that carry its name, each once, and keeps the ones ILM refuses to delete.
     */
    private final class PartDeletion {

        private final String triggerName;
        private final List<String> deleted = new ArrayList<>();
        private final List<String> kept = new ArrayList<>();
        private final Set<String> tried = new HashSet<>();

        private PartDeletion(String triggerName) {
            this.triggerName = triggerName;
        }

        private void delete(WorkflowCollection collection, String kind, NameAndUuidDto part) {
            String name = part.getName();
            if (name == null || !(name.equals(triggerName) || name.startsWith(triggerName + " - "))) {
                return;
            }
            if (!tried.add(part.getUuid())) {
                return;
            }
            String described = kind + " " + name + " [" + part.getUuid() + "]";
            try {
                ilmApiClient.deleteWorkflowObject(collection, part.getUuid());
                deleted.add(described);
            } catch (RuntimeException e) {
                kept.add("- " + described + ": " + ToolResponses.errorText("deleting the " + kind, e));
            }
        }

        private String report() {
            StringBuilder sb = new StringBuilder();
            if (!deleted.isEmpty()) {
                sb.append("\nAlso deleted: ").append(String.join(", ", deleted));
            }
            if (!kept.isEmpty()) {
                sb.append("\nKept:\n").append(String.join("\n", kept));
            }
            return sb.toString();
        }
    }

    private static String noEvent(TriggerDto trigger) {
        return "Trigger " + label(trigger) + " has no event, so it cannot be attached platform-wide.";
    }

    private static String label(TriggerDto trigger) {
        return trigger.getName() + " [" + trigger.getUuid() + "]";
    }

    private static <T> List<T> list(List<T> items) {
        return items == null ? List.of() : items;
    }

    @Override
    public String listTriggers(String event) {
        return ToolResponses.run("listing triggers", () -> {
            ResourceEvent filter = isBlank(event) ? null : TriggerRequests.event(event);
            List<TriggerDto> triggers = ilmApiClient
                    .listTriggers()
                    .stream()
                    .filter(trigger -> filter == null || trigger.getEvent() == filter)
                    .toList();
            String scope = filter == null ? "Triggers" : "Triggers for " + filter.getCode();
            return titledList(scope + " (" + triggers.size() + ")", triggers, "No triggers found.", (sb, trigger) -> {
                item(sb, trigger.getName(), trigger.getUuid());
                field(sb, INDENT, "Event", WorkflowText.eventLine(trigger));
                field(sb, INDENT, "Ignore Trigger", trigger.isIgnoreTrigger() ? "yes" : null);
                field(sb, INDENT, "Description", trigger.getDescription());
            });
        });
    }

    @Override
    public String getTrigger(String uuid) {
        return ToolResponses.run("retrieving trigger", () -> {
            TriggerDetailDto trigger = ilmApiClient.getTrigger(uuid);
            StringBuilder sb = new StringBuilder();
            WorkflowText.trigger(sb, trigger);
            sb.append(attachment(trigger)).append("\n\n");
            sb.append(WorkflowText.PLATFORM_NOTE).append("\n");
            return sb.toString();
        });
    }

    private String attachment(TriggerDetailDto trigger) {
        try {
            return WorkflowText.attachment(trigger, ilmApiClient.getEventsSettings());
        } catch (RestClientException e) {
            logger.debug("Could not read the platform event settings: {}", e.getClass().getSimpleName());
            return "Platform attachment: unknown (the platform event settings could not be read)";
        }
    }

    @Override
    public String listTriggerFields(String event) {
        return ToolResponses.run("listing trigger fields", () -> {
            ResourceEvent resourceEvent = TriggerRequests.event(event);
            String resource = resourceEvent.getResource().getCode();
            StringBuilder sb = new StringBuilder();
            title(sb, "Trigger fields for " + resourceEvent.getCode() + " (" + resource + ")");
            sb.append("Conditions can check:\n");
            WorkflowText.fields(sb, ilmApiClient.listRuleFilterFields(resource, false), true);
            sb.append("\nsetFields can set:\n");
            WorkflowText.fields(sb, ilmApiClient.listRuleFilterFields(resource, true), false);
            return sb.toString();
        });
    }

    @Override
    public String listEventTriggers(String event) {
        return ToolResponses.run("listing event triggers", () -> {
            ResourceEvent only = isBlank(event) ? null : TriggerRequests.event(event);
            EventsSettingsDto settings = ilmApiClient.getEventsSettings();
            Map<String, TriggerDto> triggers = ilmApiClient
                    .listTriggers()
                    .stream()
                    .collect(Collectors.toMap(TriggerDto::getUuid, Function.identity(), (first, second) -> first));
            StringBuilder sb = new StringBuilder();
            if (only != null) {
                eventTriggers(sb, only, WorkflowText.eventTriggers(settings, only), triggers);
            } else {
                allEventTriggers(sb, settings, triggers);
            }
            sb.append("\n").append(WorkflowText.PLATFORM_NOTE).append("\n");
            return sb.toString();
        });
    }

    private static void eventTriggers(StringBuilder sb, ResourceEvent event, List<UUID> attached,
            Map<String, TriggerDto> triggers) {
        if (attached.isEmpty()) {
            sb.append("No platform triggers for ").append(event.getCode()).append(".\n");
            return;
        }
        title(sb, "Platform triggers for " + event.getCode() + " (" + attached.size() + ")");
        numbered(sb, attached, triggers);
    }

    private static void allEventTriggers(StringBuilder sb, EventsSettingsDto settings,
            Map<String, TriggerDto> triggers) {
        title(sb, "Platform event triggers");
        boolean any = false;
        for (ResourceEvent event : ResourceEvent.values()) {
            List<UUID> attached = WorkflowText.eventTriggers(settings, event);
            if (!attached.isEmpty()) {
                any = true;
                sb.append(event.getCode()).append(" (").append(attached.size()).append("):\n");
                numbered(sb, attached, triggers);
            }
        }
        if (!any) {
            sb.append("No platform triggers are attached to any event.\n");
        }
    }

    private static void numbered(StringBuilder sb, List<UUID> attached, Map<String, TriggerDto> triggers) {
        int position = 1;
        for (UUID uuid : attached) {
            TriggerDto trigger = triggers.get(uuid.toString());
            sb.append(position++).append(". ").append(trigger == null ? TextFormat.NOT_AVAILABLE : trigger.getName());
            sb.append(" [").append(uuid).append("]");
            if (trigger != null && trigger.isIgnoreTrigger()) {
                sb.append(" (ignore trigger)");
            }
            sb.append("\n");
            if (trigger != null && !isBlank(trigger.getDescription())) {
                sb.append("   ").append(trigger.getDescription()).append("\n");
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
