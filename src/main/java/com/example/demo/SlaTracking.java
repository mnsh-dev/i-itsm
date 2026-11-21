package com.example.demo;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
public class SlaTracking {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private LocalDateTime startedAt;
    private LocalDateTime responseDeadline;
    private LocalDateTime resolutionDeadline;
    private LocalDateTime respondedAt;
    private LocalDateTime resolvedAt;
    private boolean responseWarned, resolutionWarned, responseBreached, resolutionBreached;

    protected SlaTracking() {}
    public SlaTracking(LocalDateTime startedAt, int responseMinutes, int resolutionMinutes) {
        this.startedAt = startedAt;
        this.responseDeadline = startedAt.plusMinutes(responseMinutes);
        this.resolutionDeadline = startedAt.plusMinutes(resolutionMinutes);
    }

    public void recordResponse(LocalDateTime at)   { if (respondedAt == null) respondedAt = at; }
    public void recordResolution(LocalDateTime at) { if (resolvedAt == null) resolvedAt = at; }

    /** Checks both clocks, updates the flags, and returns any NEW alerts. */
    public List<SlaAlert> evaluate(LocalDateTime now) {
        List<SlaAlert> alerts = new ArrayList<>();
        if (respondedAt == null && !responseBreached) {
            if (now.isAfter(responseDeadline)) {
                responseBreached = true; alerts.add(SlaAlert.RESPONSE_BREACH);
            } else if (!responseWarned && !now.isBefore(warnPoint(responseDeadline))) {
                responseWarned = true; alerts.add(SlaAlert.RESPONSE_WARNING);
            }
        }
        if (resolvedAt == null && !resolutionBreached) {
            if (now.isAfter(resolutionDeadline)) {
                resolutionBreached = true; alerts.add(SlaAlert.RESOLUTION_BREACH);
            } else if (!resolutionWarned && !now.isBefore(warnPoint(resolutionDeadline))) {
                resolutionWarned = true; alerts.add(SlaAlert.RESOLUTION_WARNING);
            }
        }
        return alerts;
    }

    private LocalDateTime warnPoint(LocalDateTime deadline) {
        long totalSeconds = Duration.between(startedAt, deadline).toSeconds();
        return startedAt.plusSeconds((long) (totalSeconds * 0.8));
    }

    public String currentState() {
        if (responseBreached || resolutionBreached) return "BREACHED";
        if ((responseWarned && respondedAt == null) || (resolutionWarned && resolvedAt == null)) return "AT_RISK";
        return "ON_TRACK";
    }

    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getResponseDeadline() { return responseDeadline; }
    public LocalDateTime getResolutionDeadline() { return resolutionDeadline; }
    public LocalDateTime getRespondedAt() { return respondedAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
}
