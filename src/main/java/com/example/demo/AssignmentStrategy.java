package com.example.demo;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

public interface AssignmentStrategy {
    String name();
    Technician select(Incident incident, List<TechnicianCandidate> candidates);
}


@Component
class SkillBasedAssignmentStrategy implements AssignmentStrategy {
    @Override public String name() { return "SKILL_BASED"; }

    @Override
    public Technician select(Incident incident, List<TechnicianCandidate> candidates) {
        String skill = incident.getCategory().getName();
        Comparator<TechnicianCandidate> best = Comparator
                .comparingInt((TechnicianCandidate c) -> c.technician().getExperienceYears()).reversed()
                .thenComparingInt(TechnicianCandidate::activeWorkload);
        return candidates.stream()
                .filter(c -> c.technician().getSkills().contains(skill))
                .min(best)
                .map(TechnicianCandidate::technician)
                .orElseThrow(() -> new TechnicianUnavailableException(
                        "No available technician with skill: " + skill));
    }
}
@Component
class LoadBasedAssignmentStrategy implements AssignmentStrategy {
    @Override public String name() { return "LOAD_BASED"; }

    @Override
    public Technician select(Incident incident, List<TechnicianCandidate> candidates) {
        return candidates.stream()
                .min(Comparator.comparingInt(TechnicianCandidate::activeWorkload)
                        .thenComparingInt(c -> -c.technician().getExperienceYears()))
                .map(TechnicianCandidate::technician)
                .orElseThrow(() -> new TechnicianUnavailableException("No available technician"));
    }
}
