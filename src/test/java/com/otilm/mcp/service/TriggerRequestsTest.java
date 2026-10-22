package com.otilm.mcp.service;

import com.otilm.api.model.core.other.ResourceEvent;
import com.otilm.api.model.core.search.FilterConditionOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TriggerRequestsTest {

    @ParameterizedTest
    @ValueSource(strings = {"certificate_discovered", "CERTIFICATE_DISCOVERED", " Certificate_Discovered "})
    void eventAcceptsCodesAndNames(String value) {
        assertThat(TriggerRequests.event(value)).isEqualTo(ResourceEvent.CERTIFICATE_DISCOVERED);
    }

    @Test
    void unknownEventListsTheValidOnes() {
        assertThatThrownBy(() -> TriggerRequests.event("certificate_found"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Unknown event 'certificate_found'. Valid events: certificate_status_changed, ")
                .hasMessageEndingWith("scheduled_job_finished.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void missingEventSaysSo(String value) {
        assertThatThrownBy(() -> TriggerRequests.event(value)).hasMessageStartingWith("Missing event. Valid events: ");
    }

    @Test
    void planReadsConditionsAndSetFields() {
        TriggerRequests.TriggerPlan plan = TriggerRequests
                .plan(input(
                        "[{\"fieldSource\":\"property\",\"fieldIdentifier\":\"COMMON_NAME\",\"condition\":\"ENDS_WITH\",\"value\":\".example.com\"}]",
                        "[{\"fieldSource\":\"property\",\"fieldIdentifier\":\"OWNER\",\"value\":\"00000000-0000-4000-8000-000000000101\"}]",
                        null, null));

        assertThat(plan.conditions()).singleElement().satisfies(item -> {
            assertThat(item.getFieldIdentifier()).isEqualTo("COMMON_NAME");
            assertThat(item.getOperator()).isEqualTo(FilterConditionOperator.ENDS_WITH);
            assertThat(item.getValue()).isEqualTo(".example.com");
        });
        assertThat(plan.setFields())
                .singleElement()
                .satisfies(item -> assertThat(item.getData()).isEqualTo("00000000-0000-4000-8000-000000000101"));
        assertThat(plan.attach()).isTrue();
        assertThat(plan.ignore()).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "' '|certificate_expiring|||ignore-null|A trigger needs a name.",
            "t|certificate_expiring|||false|A trigger needs something to do: setFields, notificationProfileUuid, or ignore=true.",
            "t|certificate_expiring||p|true|An ignore trigger cannot also set fields or send notifications.",
            "t|certificate_expiring|||true|An ignore trigger needs at least one condition; without one it would ignore every object of the event."})
    void planRejectsIncompleteTriggers(String name, String event, String setFields, String profile, String ignore,
            String message) {
        TriggerInput input = new TriggerInput(name, event, null, setFields, profile,
                "ignore-null".equals(ignore) ? null : Boolean.valueOf(ignore), null);

        assertThatThrownBy(() -> TriggerRequests.plan(input)).hasMessage(message);
    }

    @Test
    void planRejectsMalformedAndUnsupportedSetFields() {
        TriggerInput malformed = input(null, "{not json", null, null);
        TriggerInput meta = input(null, "[{\"fieldSource\":\"meta\",\"fieldIdentifier\":\"x|STRING\",\"value\":\"y\"}]",
                null, null);
        TriggerInput unnamed = input(null, "[{\"fieldSource\":\"property\",\"value\":\"y\"}]", null, null);
        TriggerInput nullEntry = input(null, "[null]", null, null);

        assertThatThrownBy(() -> TriggerRequests.plan(malformed)).hasMessageStartingWith("Invalid setFields JSON.");
        assertThatThrownBy(() -> TriggerRequests.plan(meta))
                .hasMessage("setFields can only set property or custom fields, not meta.");
        assertThatThrownBy(() -> TriggerRequests.plan(unnamed))
                .hasMessage("Each setFields entry needs a fieldIdentifier.");
        assertThatThrownBy(() -> TriggerRequests.plan(nullEntry))
                .hasMessage("Each setFields entry needs a fieldSource, a fieldIdentifier and a value.");
    }

    @Test
    void planReadsJsonNullAsNothingGiven() {
        TriggerRequests.TriggerPlan plan = TriggerRequests
                .plan(input("null", "null", "00000000-0000-4000-8000-000000000102", null));

        assertThat(plan.conditions()).isEmpty();
        assertThat(plan.setFields()).isEmpty();
    }

    @Test
    void planRejectsSetFieldsWithoutAValue() {
        TriggerInput missing = input(null, "[{\"fieldSource\":\"property\",\"fieldIdentifier\":\"OWNER\"}]", null,
                null);
        TriggerInput ilmKey = input(null,
                "[{\"fieldSource\":\"property\",\"fieldIdentifier\":\"OWNER\",\"data\":\"00000000-0000-4000-8000-000000000101\"}]",
                null, null);

        assertThatThrownBy(() -> TriggerRequests.plan(missing))
                .hasMessage("Each setFields entry needs a value; to clear a field, give an empty list [].");
        assertThatThrownBy(() -> TriggerRequests.plan(ilmKey))
                .hasMessageStartingWith("Invalid setFields JSON.")
                .hasMessageContaining("\"data\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[{}]",
            "[null]",
            "[{\"fieldSource\":\"property\",\"fieldIdentifier\":\"COMMON_NAME\",\"operator\":\"ENDS_WITH\",\"value\":\".example.com\"}]",
            "[{\"fieldSource\":\"property\",\"condition\":\"EMPTY\"}]",
            "[{\"fieldIdentifier\":\"COMMON_NAME\",\"condition\":\"EMPTY\"}]"})
    void planRejectsIncompleteConditions(String conditions) {
        TriggerInput input = input(conditions, null, null, true);

        assertThatThrownBy(() -> TriggerRequests.plan(input))
                .hasMessage("Each condition needs a fieldSource, a fieldIdentifier and a condition; "
                        + "get_searchable_fields lists the fields.");
    }

    @Test
    void planAcceptsAConditionWithoutAValue() {
        TriggerRequests.TriggerPlan plan = TriggerRequests
                .plan(input("[{\"fieldSource\":\"property\",\"fieldIdentifier\":\"OWNER\",\"condition\":\"EMPTY\"}]",
                        null, null, true));

        assertThat(plan.conditions())
                .singleElement()
                .satisfies(item -> assertThat(item.getOperator()).isEqualTo(FilterConditionOperator.EMPTY));
    }

    private static TriggerInput input(String conditions, String setFields, String profile, Boolean ignore) {
        return new TriggerInput("expiring-web", "certificate_expiring", conditions, setFields, profile, ignore, null);
    }
}
