package com.example.demo;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SlaService {
    private final IncidentRepository incidents;
    private final SlaPolicyRepository policies;
    private final IncidentEventPublisher publisher;

    public SlaService(IncidentRepository incidents, SlaPolicyRepository policies, IncidentEventPublisher publisher) {
        this.incidents = incidents; this.policies = policies; this.publisher = publisher;
    }

    public SlaPolicy policyFor(Incident i) {
        return policies.findFirstByPriorityAndServiceId(i.getPriority(), i.getService().getId())
                .or(() -> policies.findFirstByPriorityAndServiceIsNull(i.getPriority()))
                .orElseThrow(() -> new MissingSlaConfigurationException(i.getPriority()));
    }

    @Transactional
    public int check() {
        LocalDateTime now = LocalDateTime.now();
        int count = 0;
        for (Incident i : incidents.findWithOpenSla()) {
            for (SlaAlert alert : i.getSla().evaluate(now)) {
                publisher.publish(new IncidentEvent(i, alert.eventType(), i.getStatus(), i.getStatus(),
                        "SLA_MONITOR", alert.message(), now));
                count++;
            }
        }
        return count;
    }

    public List<SlaPolicy> allPolicies() { return policies.findAll(); }

    @Transactional
    public SlaPolicy updatePolicy(Long id, int responseMinutes, int resolutionMinutes) {
        SlaPolicy p = policies.findById(id).orElseThrow(() -> new ResourceNotFoundException("SLA policy", id));
        p.update(responseMinutes, resolutionMinutes);
        return p;
    }

    public List<Incident> violations() { return incidents.findBreached(); }
}
