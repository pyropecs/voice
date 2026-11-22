package com.voip.voice.asterisk.orchestration.schedulers;

import com.voip.voice.asterisk.orchestration.services.AsteriskOrchestrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically picks up the oldest PENDING Asterisk server and configures it.
 * Schedule is controlled by {@code asterisk.orchestration.cron} (set it to "-" to disable).
 */
@Component
public class AsteriskOrchestrationScheduler {

    private static final Logger log = LoggerFactory.getLogger(AsteriskOrchestrationScheduler.class);

    private final AsteriskOrchestrationService asteriskOrchestrationService;

    public AsteriskOrchestrationScheduler(AsteriskOrchestrationService asteriskOrchestrationService) {
        this.asteriskOrchestrationService = asteriskOrchestrationService;
    }

    @Scheduled(cron = "${asterisk.orchestration.cron:0 */1 * * * *}")
    public void checkPendingAsteriskServers() {
        try {
            asteriskOrchestrationService.processPendingAsteriskServer();
        } catch (Exception e) {
            // Never let one failure kill the scheduler thread; the next tick retries.
            log.error("Asterisk orchestration run failed: {}", e.getMessage(), e);
        }
    }
}
