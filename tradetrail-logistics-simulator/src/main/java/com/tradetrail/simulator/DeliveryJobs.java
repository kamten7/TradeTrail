package com.tradetrail.simulator;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "tradetrail.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class DeliveryJobs {
    private final DeliveryService service;

    public DeliveryJobs(DeliveryService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${tradetrail.jobs.delay-ms:2000}")
    public void recover() {
        service.recover();
    }
}
