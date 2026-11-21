package com.example.demo;

import org.springframework.stereotype.Service;

import java.util.*;
record WorkloadView(Long technicianId, String name, int activeIncidents, int maxWorkload) {}
@Service
public class AssignmentService {
	private static final Set<IncidentStatus> ACTIVE =
				EnumSet.of(IncidentStatus.ASSIGNED, IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS, IncidentStatus.AWAITING_INFO);

	private final TechnicianRepository technicians;
	private final IncidentRepository incidents;
	private final Map<String, AssignmentStrategy> strategies = new HashMap<>();
	private volatile String active = "SKILL_BASED";

	public AssignmentService(TechnicianRepository technicians, IncidentRepository incidents,
							 List<AssignmentStrategy> all) {
		this.technicians = technicians;
		this.incidents = incidents;
		all.forEach(s -> strategies.put(s.name(), s));
	}

	public Technician pick(Incident incident) {
		List<TechnicianCandidate> candidates = technicians.findAll().stream()
				.map(t -> new TechnicianCandidate(t, workloadOf(t)))
				.filter(c -> c.technician().getRole() == Role.TECHNICIAN)
				.filter(c -> c.technician().isActive())
				.filter(c -> c.activeWorkload() < c.technician().getMaxWorkload())
				.toList();
		if (candidates.isEmpty())
			throw new TechnicianUnavailableException("All technicians are at maximum workload");
		return strategies.get(active).select(incident, candidates);
	}

	public List<WorkloadView> workloads() {
		return technicians.findAll().stream()
				.filter(t -> t.getRole() == Role.TECHNICIAN)
				.map(t -> new WorkloadView(t.getId(), t.getName(), workloadOf(t), t.getMaxWorkload()))
				.toList();
	}

	public void useStrategy(String name) {
		if (!strategies.containsKey(name))
			throw new IllegalArgumentException("Unknown strategy: " + name + ", choose from " + strategies.keySet());
		active = name;
	}

	public String currentStrategy() { return active; }
	public Set<String> availableStrategies() { return strategies.keySet(); }

	private int workloadOf(Technician t) {
		return (int) incidents.countByAssignedToIdAndStatusIn(t.getId(), ACTIVE);
	}
}
