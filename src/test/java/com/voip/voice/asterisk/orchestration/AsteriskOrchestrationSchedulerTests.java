package com.voip.voice.asterisk.orchestration;

import com.voip.voice.asterisk.orchestration.schedulers.AsteriskOrchestrationScheduler;
import com.voip.voice.asterisk.orchestration.services.AsteriskOrchestrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

class AsteriskOrchestrationSchedulerTests {

    private final AsteriskOrchestrationService service = mock(AsteriskOrchestrationService.class);
    private final AsteriskOrchestrationScheduler scheduler = new AsteriskOrchestrationScheduler(service);

    @DisplayName("Each tick processes pending servers")
    @Test
    void tick_processesPendingServers() {
        scheduler.checkPendingAsteriskServers();

        verify(service).processPendingAsteriskServer();
    }

    @DisplayName("A failing run does not propagate (scheduler keeps running)")
    @Test
    void tick_swallowsFailures() {
        doThrow(new RuntimeException("SSH down")).when(service).processPendingAsteriskServer();

        assertThatCode(scheduler::checkPendingAsteriskServers).doesNotThrowAnyException();
    }

    @DisplayName("Uses Spring's @Scheduled with a configurable cron")
    @Test
    void tick_isScheduledByProperty() throws Exception {
        Scheduled scheduled = AsteriskOrchestrationScheduler.class
                .getMethod("checkPendingAsteriskServers")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("${asterisk.orchestration.cron:0 */1 * * * *}");
    }
}
