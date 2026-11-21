package com.example.demo;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class IncidentCorrelationService {
    private static final Set<String> STOPWORDS = Set.of(
            "the", "is", "not", "working", "a", "an", "to", "in", "on", "for", "of", "and");

    private final IncidentRepository repo;
    private final IncidentEventPublisher publisher;

    public IncidentCorrelationService(IncidentRepository repo, IncidentEventPublisher publisher) {
        this.repo = repo;
        this.publisher = publisher;
    }

    @Transactional
    public void correlateIfRelated(Incident newlyCreated) {
        LocalDateTime since = LocalDateTime.now().minusHours(2);
        Set<String> newTokens = tokens(newlyCreated.getDescription());
        Incident root = repo.findAll().stream()
                .filter(candidate -> !candidate.getId().equals(newlyCreated.getId()))
                .filter(candidate -> candidate.getStatus() != IncidentStatus.RESOLVED
                        && candidate.getStatus() != IncidentStatus.CLOSED)
                .filter(candidate -> candidate.getCreatedAt() != null && !candidate.getCreatedAt().isBefore(since))
                .filter(candidate -> candidate.getService().getId().equals(newlyCreated.getService().getId()))
                .filter(candidate -> similarity(newTokens, tokens(candidate.getDescription())) >= 0.35)
                .sorted((first, second) -> first.getCreatedAt().compareTo(second.getCreatedAt()))
                .map(candidate -> candidate.getParentIncident() != null ? candidate.getParentIncident() : candidate)
                .findFirst()
                .orElse(null);

        if (root == null) return;

        newlyCreated.setParentIncident(root);
        repo.save(newlyCreated);
        IncidentStatus newStatus = newlyCreated.getStatus();
        publisher.publish(new IncidentEvent(newlyCreated, EventType.CORRELATED, newStatus, newStatus,
                "SYSTEM", "Correlated as duplicate/related to INC" + root.getId(), LocalDateTime.now()));

        if (repo.countByParentIncidentId(root.getId()) >= 2 && root.escalatePriority()) {
            repo.save(root);
            IncidentStatus rootStatus = root.getStatus();
            publisher.publish(new IncidentEvent(root, EventType.ESCALATED, rootStatus, rootStatus,
                    "SYSTEM", "Auto-escalated to " + root.getPriority()
                    + " due to multiple correlated incidents", LocalDateTime.now()));
        }
    }

    private Set<String> tokens(String description) {
        if (description == null || description.isBlank()) return Set.of();
        return Arrays.stream(description.toLowerCase().split("[^a-z0-9]+"))
                .filter(token -> !token.isBlank() && !STOPWORDS.contains(token))
                .collect(Collectors.toSet());
    }

    private double similarity(Set<String> left, Set<String> right) {
        if (left.isEmpty() && right.isEmpty()) return 0.0;
        Set<String> intersection = left.stream().filter(right::contains).collect(Collectors.toSet());
        Set<String> union = new java.util.HashSet<>(left);
        union.addAll(right);
        return (double) intersection.size() / union.size();
    }
}
