package com.example.demo;

import org.springframework.stereotype.Component;

@Component
class AuditObserver implements IncidentObserver {
    private final AuditRecordRepository repo;
    AuditObserver(AuditRecordRepository repo) { this.repo = repo; }

    @Override public void onEvent(IncidentEvent e) {
        repo.save(new AuditRecord(e.at(), e.actor(), e.type().name(), e.incident().getId(),
                e.previousState(), e.newState(), e.description()));
    }
}