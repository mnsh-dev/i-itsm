package com.example.demo;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;


import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

@Service
public class IncidentService {


	private final IncidentRepository repo;
	private final UserRepository users;
	private final ItServiceRepository services;
	private final CategoryRepository categories;
	private final PriorityStrategy priorityStrategy;
	private final AssignmentService assignmentService;
	private final IncidentEventPublisher publisher;
	private final CategoryClassifier classifier;
	private final SlaService slaService;
	private final IncidentCorrelationService correlationService;

	public IncidentService(IncidentRepository repo, UserRepository users, ItServiceRepository services,
						   CategoryRepository categories, PriorityStrategy priorityStrategy,
						   AssignmentService assignmentService, IncidentEventPublisher publisher, CategoryClassifier classifier,
						   SlaService slaService, IncidentCorrelationService correlationService) {
		this.repo = repo; this.users = users; this.services = services; this.categories = categories;
		this.priorityStrategy = priorityStrategy; this.assignmentService = assignmentService;
		this.publisher = publisher; this.classifier=classifier; this.slaService=slaService;
		this.correlationService = correlationService;
	}
	private String currentActor() {
		var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated()
				|| auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
			return "SYSTEM";
		return auth.getName();
	}
	@Transactional
	public Incident create(CreateIncidentRequest req, String reporterEmail) {
		User reporter = users.findByEmail(reporterEmail)
				.orElseThrow(() -> new UnauthorizedOperationException("Unknown user: " + reporterEmail));
		ItService svc = services.findById(req.serviceId())
				.orElseThrow(() -> new ResourceNotFoundException("Service", req.serviceId()));
		Category cat = (req.categoryId() != null)
				? categories.findById(req.categoryId())
				.orElseThrow(() -> new ResourceNotFoundException("Category", req.categoryId()))
				: null;

		PriorityResult pr = priorityStrategy.calculate(req.impact(), req.urgency(), svc.getBusinessCriticality());
		Incident saved = repo.save(new Incident(req.description(), reporter, svc, cat,
				req.impact(), req.urgency(), pr));
		publish(saved, EventType.CREATED, null);
		correlationService.correlateIfRelated(saved);
		return saved;
	}
	@Transactional
	public Incident classify(Long id, Long categoryId) {
		Incident i = get(id);
		Category cat;
		if (categoryId != null) {                       // manual override
			cat = categories.findById(categoryId)
					.orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
		} else if (i.getCategory() != null) {           // reporter's choice, confirmed
			cat = i.getCategory();
		} else {                                        // automatic suggestion
			cat = classifier.classify(i.getDescription(), categories.findAll())
					.orElseThrow(() -> new IllegalArgumentException(
							"Could not determine a category; classify manually with ?categoryId="));
		}
		IncidentStatus before = i.getStatus();
		i.classify(cat, slaService.policyFor(i));
		publish(i, EventType.CLASSIFIED, before);
		return i;
	}

	public List<Incident> findAll() { return repo.findAll(); }

	public Incident get(Long id) {
		return repo.findById(id).orElseThrow(() -> new IncidentNotFoundException(id));
	}

	public Technician recommend(Long id) {
		Incident i = get(id);
		if (i.getCategory() == null)
			throw new IllegalArgumentException("Incident must be classified first");
		return assignmentService.pick(i);
	}


	@Transactional public Incident assign(Long id) {
		return transition(id, EventType.ASSIGNED, i -> i.assign(assignmentService::pick));
	}
	@Transactional public Incident acknowledge(Long id) { return transition(id, EventType.ACKNOWLEDGED, Incident::acknowledge); }
	@Transactional public Incident startWork(Long id)   { return transition(id, EventType.STARTED, Incident::startWork); }
	@Transactional public Incident resolve(Long id)     { return resolve(id, null); }
	@Transactional public Incident resolve(Long id, String resolution) {
		return transition(id, EventType.RESOLVED, i -> {
			if (resolution != null && !resolution.isBlank()) i.setResolution(resolution.trim());
			i.resolve();
		});
	}
	@Transactional public Incident verify(Long id)      { return transition(id, EventType.VERIFIED, Incident::verify); }
	@Transactional public Incident close(Long id)       { return transition(id, EventType.CLOSED, Incident::close); }

	@Transactional
	public Incident requestInformation(Long id, String question) {
		String clean = requireMessage(question, "Question");
		Incident incident = get(id);
		IncidentStatus before = incident.getStatus();
		incident.requestInformation();
		publishMessage(incident, EventType.INFO_REQUESTED, before, "Information requested: " + clean);
		return incident;
	}

	@Transactional
	public Incident addInformation(Long id, String message) {
		String clean = requireMessage(message, "Additional information");
		Incident incident = get(id);
		if (!isOpen(incident.getStatus())) throw new IllegalArgumentException("Additional information is only accepted on open incidents");
		IncidentStatus before = incident.getStatus();
		incident.recordInformationResponse();
		publishMessage(incident, EventType.INFO_PROVIDED, before, "Reporter provided additional information: " + clean);
		return incident;
	}

	@Transactional
	public Incident addInvestigationNote(Long id, String message) {
		String clean = requireMessage(message, "Investigation note");
		Incident incident = get(id);
		if (!EnumSet.of(IncidentStatus.ASSIGNED, IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS,
				IncidentStatus.AWAITING_INFO).contains(incident.getStatus()))
			throw new IllegalArgumentException("Investigation notes can only be added while work is active");
		publishMessage(incident, EventType.INVESTIGATION_NOTE, incident.getStatus(), "Investigation note: " + clean);
		return incident;
	}

	@Transactional
	public Incident submitFeedback(Long id, Integer rating, String comment) {
		Incident incident = get(id);
		IncidentStatus state = incident.getStatus();
		if (comment != null && comment.length() > 3500)
			throw new IllegalArgumentException("Feedback comment must be 3500 characters or fewer");
		incident.submitFeedback(rating, comment);
		String details = "Reporter feedback submitted (" + rating + "/5)"
				+ (comment == null || comment.isBlank() ? "" : ": " + comment.trim());
		publishMessage(incident, EventType.FEEDBACK_SUBMITTED, state, details);
		return incident;
	}

	private void publishMessage(Incident incident, EventType type, IncidentStatus before, String description) {
		publisher.publish(new IncidentEvent(incident, type, before, incident.getStatus(), currentActor(),
				description, LocalDateTime.now()));
	}

	private String requireMessage(String message, String label) {
		if (message == null || message.isBlank()) throw new IllegalArgumentException(label + " is required");
		String clean = message.trim();
		if (clean.length() > 3500) throw new IllegalArgumentException(label + " must be 3500 characters or fewer");
		return clean;
	}

	private boolean isOpen(IncidentStatus status) {
		return EnumSet.of(IncidentStatus.NEW, IncidentStatus.CLASSIFIED, IncidentStatus.ASSIGNED,
				IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS, IncidentStatus.AWAITING_INFO).contains(status);
	}

	private Incident transition(Long id, EventType type, Consumer<Incident> action) {
		Incident i = get(id);
		IncidentStatus before = i.getStatus();
		action.accept(i);                 // throws InvalidStateTransitionException if not allowed
		publish(i, type, before);
		return i;
	}

	private void publish(Incident i, EventType type, IncidentStatus before) {
		publisher.publish(new IncidentEvent(i, type, before, i.getStatus(), currentActor(),
				describe(i, type), LocalDateTime.now()));
	}

	private String describe(Incident i, EventType type) {
		return switch (type) {
			case SLA_WARNING, SLA_VIOLATED -> "SLA event";
			case CORRELATED -> "Incident correlated with a related incident";
			case ESCALATED -> "Incident priority automatically escalated";
			case INFO_REQUESTED, INFO_PROVIDED, INVESTIGATION_NOTE, FEEDBACK_SUBMITTED -> "Incident update";
			case CREATED      -> "Incident created, priority " + i.getPriority() + " (" + i.getService().getName() + ")";
			case CLASSIFIED   -> "Incident classified as " + i.getCategory().getName().toUpperCase();
			case ASSIGNED     -> "Assigned to " + i.getAssignedTo().getName();
			case ACKNOWLEDGED -> "Technician acknowledged incident";
			case STARTED      -> "Investigation started";
			case RESOLVED     -> "Incident marked RESOLVED";
			case VERIFIED     -> "Resolution verified";
			case CLOSED       -> "Incident CLOSED";
		};
	}
}
