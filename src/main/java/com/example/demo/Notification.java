package com.example.demo;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.LocalDateTime;

@Entity
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long recipientId;
    private Long incidentId;
    @jakarta.persistence.Column(length = 4000) private String message;
    private LocalDateTime createdAt;
    private boolean seen;

    protected Notification() {}
    public Notification(Long recipientId, Long incidentId, String message) {
        this.recipientId = recipientId; this.incidentId = incidentId; this.message = message;
        this.createdAt = LocalDateTime.now();
    }
    public void markSeen() { this.seen = true; }
    public Long getId() { return id; }
    public Long getRecipientId() { return recipientId; }
    public Long getIncidentId() { return incidentId; }
    public String getMessage() { return message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isSeen() { return seen; }
}
