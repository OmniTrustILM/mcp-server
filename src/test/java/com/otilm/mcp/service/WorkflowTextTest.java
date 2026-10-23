package com.otilm.mcp.service;

import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.other.ResourceEvent;
import com.otilm.api.model.core.workflows.TriggerDetailDto;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowTextTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "CERTIFICATE_DISCOVERED|Then: skip the certificate, so discovery does not import it (ignore trigger)",
            "CERTIFICATE_UPLOADED|Then: skip the certificate, so the upload is not stored (ignore trigger)",
            "CERTIFICATE_EXPIRING|Then: ignore the object, so the triggers attached alongside it do not run for it (ignore trigger)"})
    void ignoreTriggersSayWhatTheySkip(ResourceEvent event, String expected) {
        TriggerDetailDto trigger = new TriggerDetailDto();
        trigger.setUuid("00000000-0000-4000-8000-000000000006");
        trigger.setName("skip-test");
        trigger.setResource(Resource.CERTIFICATE);
        trigger.setEvent(event);
        trigger.setIgnoreTrigger(true);
        StringBuilder sb = new StringBuilder();

        WorkflowText.trigger(sb, trigger);

        assertThat(sb).contains(expected + "\n");
    }
}
