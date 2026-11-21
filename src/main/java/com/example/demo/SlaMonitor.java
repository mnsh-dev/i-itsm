package com.example.demo;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class SlaMonitor {
    private final SlaService sla;
    SlaMonitor(SlaService sla) { this.sla = sla; }

    @Scheduled(fixedRate = 30_000, initialDelay = 30_000)
    void run() { sla.check(); }
}
