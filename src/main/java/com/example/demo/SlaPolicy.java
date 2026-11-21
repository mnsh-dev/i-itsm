package com.example.demo;

import jakarta.persistence.*;

@Entity
public class SlaPolicy {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) private Priority priority;
    @ManyToOne private ItService service;
    private int responseMinutes;
    private int resolutionMinutes;

    protected SlaPolicy() {}
    public SlaPolicy(Priority priority, ItService service, int responseMinutes, int resolutionMinutes) {
        validate(responseMinutes, resolutionMinutes);
        this.priority = priority; this.service = service;
        this.responseMinutes = responseMinutes; this.resolutionMinutes = resolutionMinutes;
    }
    public void update(int responseMinutes, int resolutionMinutes) {
        validate(responseMinutes, resolutionMinutes);
        this.responseMinutes = responseMinutes; this.resolutionMinutes = resolutionMinutes;
    }
    private static void validate(int response, int resolution) {
        if (response <= 0 || resolution < response)
            throw new IllegalArgumentException("SLA needs response > 0 and resolution >= response");
    }
    public Long getId() { return id; }
    public Priority getPriority() { return priority; }
    public ItService getService() { return service; }
    public int getResponseMinutes() { return responseMinutes; }
    public int getResolutionMinutes() { return resolutionMinutes; }
}
