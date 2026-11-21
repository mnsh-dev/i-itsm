package com.example.demo;

public enum SlaAlert {
    RESPONSE_WARNING(EventType.SLA_WARNING, "Response SLA at risk: 80% of response time used, not yet acknowledged"),
    RESPONSE_BREACH(EventType.SLA_VIOLATED, "Response SLA violated: not acknowledged in time"),
    RESOLUTION_WARNING(EventType.SLA_WARNING, "Resolution SLA at risk: 80% of resolution time used"),
    RESOLUTION_BREACH(EventType.SLA_VIOLATED, "Resolution SLA violated: not resolved in time");

    private final EventType eventType;
    private final String message;
    SlaAlert(EventType eventType, String message) { this.eventType = eventType; this.message = message; }
    public EventType eventType() { return eventType; }
    public String message() { return message; }
}
