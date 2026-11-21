package com.example.demo;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public record IncidentEvent(Incident incident, EventType type, IncidentStatus previousState,
                            IncidentStatus newState, String actor, String description, LocalDateTime at) {

    public List<User> recipients() {
        List<User> r = new ArrayList<>();
        switch (type) {
            case INFO_REQUESTED -> r.add(incident.getReporter());
            case INFO_PROVIDED, FEEDBACK_SUBMITTED -> {
                if (incident.getAssignedTo() != null) r.add(incident.getAssignedTo());
            }
            case INVESTIGATION_NOTE -> { }
            default -> {
                r.add(incident.getReporter());
                if (incident.getAssignedTo() != null) r.add(incident.getAssignedTo());
            }
        }
        return r;
    }
}


