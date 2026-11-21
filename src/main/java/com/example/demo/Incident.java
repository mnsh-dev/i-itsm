package com.example.demo;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@Entity
public class Incident {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private String description;
	private String resolution;
	private Integer feedbackRating;
	@Column(length = 4000)
	private String feedbackComment;
	@Enumerated(EnumType.STRING)
	private IncidentStatus status;
	private LocalDateTime createdAt;
	@OneToOne(cascade = CascadeType.ALL)
	private SlaTracking sla;

	@ManyToOne(optional = false) private User reporter;
	@ManyToOne(optional = false) private ItService service;
	@ManyToOne private Category category;    // null until classified
	@ManyToOne private Technician assignedTo;
	@ManyToOne private Incident parentIncident;
	@OneToMany(mappedBy = "parentIncident") private List<Incident> relatedIncidents = new ArrayList<>();

	private int impact;
	private int urgency;
	@Column(nullable = false)
	private int affectedUsers = 1;
	private int priorityScore;
	@Enumerated(EnumType.STRING)
	private Priority priority;

	protected Incident() {}

	public Incident(String description, User reporter, ItService service, Category category,
					int impact, int urgency, PriorityResult priorityResult) {
		this(description, reporter, service, category, impact, urgency, 1, priorityResult);
	}

	public Incident(String description, User reporter, ItService service, Category category,
					int impact, int urgency, int affectedUsers, PriorityResult priorityResult) {
		this.description = description;
		this.reporter = reporter;
		this.service = service;
		this.category = category;
		this.impact = impact;
		this.urgency = urgency;
		setAffectedUsers(affectedUsers);
		this.priority = priorityResult.priority();
		this.priorityScore = priorityResult.score();
		this.status = IncidentStatus.NEW;
		this.createdAt = LocalDateTime.now();
	}

	public Long getId() { return id; }
	public String getDescription() { return description; }
	public String getResolution() { return resolution; }
	public void setResolution(String resolution) { this.resolution = resolution; }
	public Integer getFeedbackRating() { return feedbackRating; }
	public String getFeedbackComment() { return feedbackComment; }
	public IncidentStatus getStatus() { return status; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public SlaTracking getSla() { return sla; }
	public String getSlaStatus() { return sla == null ? "NOT_STARTED" : sla.currentState(); }
	public User getReporter() { return reporter; }
	public ItService getService() { return service; }
	public Category getCategory() { return category; }
	public int getImpact() { return impact; }
	public int getUrgency() { return urgency; }
	public int getAffectedUsers() { return Math.max(1, affectedUsers); }
	public void setAffectedUsers(int affectedUsers) { this.affectedUsers = Math.max(1, affectedUsers); }
	public Priority getPriority() { return priority; }
	public int getPriorityScore() { return priorityScore; }
	public Technician getAssignedTo() { return assignedTo; }
	public Incident getParentIncident() { return parentIncident; }
	public void setParentIncident(Incident parentIncident) { this.parentIncident = parentIncident; }
	public List<Incident> getRelatedIncidents() { return relatedIncidents; }
	public boolean escalatePriority() {
		if (priority == Priority.P1) return false;
		priority = switch (priority) {
			case P4 -> Priority.P3;
			case P3 -> Priority.P2;
			case P2 -> Priority.P1;
			case P1 -> Priority.P1;
		};
		return true;
	}
	private IncidentState state() {
		return IncidentStates.of(status);
	}

	public void classify(Category category, SlaPolicy policy) {
		IncidentStatus next = state().classify();
		this.category = category;
		this.sla = new SlaTracking(createdAt, policy.getResponseMinutes(), policy.getResolutionMinutes());
		this.status = next;
	}


	public void assign(java.util.function.Function<Incident, Technician> picker) {
		IncidentStatus next = state().assign();     // state check runs first, so a NEW incident gets a 409
		this.assignedTo = picker.apply(this);
		this.status = next;
	}

	public void acknowledge() {
		status = state().acknowledge();
		if (sla != null) sla.recordResponse(LocalDateTime.now());
	}
	public void startWork() { status = state().startWork(); }
	public void resolve() {
		status = state().resolve();
		if (sla != null) sla.recordResolution(LocalDateTime.now());
	}
	public void requestInformation() { status = state().requestInfo(); }
	public void recordInformationResponse() {
		if (status == IncidentStatus.AWAITING_INFO) status = state().provideInfo();
	}
	public void submitFeedback(Integer rating, String comment) {
		if (rating == null || rating < 1 || rating > 5) throw new IllegalArgumentException("Feedback rating must be from 1 to 5");
		if (status != IncidentStatus.RESOLVED && status != IncidentStatus.CLOSED)
			throw new IllegalArgumentException("Feedback can be submitted after resolution");
		if (feedbackRating != null) throw new IllegalArgumentException("Feedback has already been submitted");
		this.feedbackRating = rating;
		this.feedbackComment = comment == null || comment.isBlank() ? null : comment.trim();
	}
	public void verify()    { status = state().verify(); }
	public void close()     { status = state().close(); }
}
