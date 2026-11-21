package com.example.demo;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
public class AuditRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private LocalDateTime timestamp;
    private String actor;
    private String action;
    private Long incidentId;
    @Enumerated(EnumType.STRING) private IncidentStatus previousState;
    @Enumerated(EnumType.STRING) private IncidentStatus newState;
    @Column(length = 4000) private String description;

    protected AuditRecord() {}
    public AuditRecord(LocalDateTime timestamp, String actor, String action, Long incidentId,
                       IncidentStatus previousState, IncidentStatus newState, String description) {
        this.timestamp = timestamp; this.actor = actor; this.action = action; this.incidentId = incidentId;
        this.previousState = previousState; this.newState = newState; this.description = description;
    }
    public Long getId() { return id; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public Long getIncidentId() { return incidentId; }
    public IncidentStatus getPreviousState() { return previousState; }
    public IncidentStatus getNewState() { return newState; }
    public String getDescription() { return description; }
}
