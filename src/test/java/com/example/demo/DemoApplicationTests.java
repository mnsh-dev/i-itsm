package com.example.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DemoApplicationTests {

    @Autowired MockMvc mvc;
    @Autowired IncidentService incidentService;
    @Autowired IncidentRepository incidentRepository;
    @Autowired UserRepository userRepository;
    @Autowired ItServiceRepository serviceRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired SlaPolicyRepository policyRepository;
    @Autowired AssignmentService assignmentService;
    @Autowired SlaService slaService;
    @Autowired ReportService reportService;
    @Autowired AuditRecordRepository auditRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void ensureFixtures() {
        ensureEmployee("asha@corp.test", "Asha");
        ensureEmployee("dev@corp.test", "Dev");
        ensureTechnician("ravi@corp.test", "Ravi", Set.of("Network", "Database"), 5, 5);
        ensureTechnician("meera@corp.test", "Meera", Set.of("Application", "Cloud"), 3, 4);
        if (userRepository.findByEmail("admin@corp.test").isEmpty()) {
            Administrator admin = new Administrator("Admin", "admin@corp.test");
            admin.changePassword(passwordEncoder.encode("password123"));
            userRepository.save(admin);
        }
        ItService payment = ensureService("Payment", 5);
        ensureService("Network", 4);
        ensureService("Email", 2);
        for (Priority priority : Priority.values()) {
            if (policyRepository.findFirstByPriorityAndServiceIsNull(priority).isEmpty()) {
                int response = switch (priority) {
                    case P1 -> 5;
                    case P2 -> 15;
                    case P3 -> 60;
                    case P4 -> 240;
                };
                int resolution = switch (priority) {
                    case P1 -> 30;
                    case P2 -> 120;
                    case P3 -> 480;
                    case P4 -> 1440;
                };
                policyRepository.save(new SlaPolicy(priority, null, response, resolution));
            }
        }
        if (policyRepository.findFirstByPriorityAndServiceId(Priority.P2, payment.getId()).isEmpty())
            policyRepository.save(new SlaPolicy(Priority.P2, payment, 10, 90));
        ensureCategory("Application");
        ensureCategory("Network");
        ensureCategory("Database");
    }

    @ParameterizedTest
    @CsvSource({"5,5,5,P1,125", "3,3,4,P2,36", "2,2,3,P3,12", "1,1,1,P4,1"})
    void priorityCalculationCoversP1ThroughP4(int impact, int urgency, int criticality,
                                               Priority expected, int expectedScore) {
        PriorityResult result = new ScoreBasedPriorityStrategy().calculate(impact, urgency, criticality);
        assertEquals(expected, result.priority());
        assertEquals(expectedScore, result.score());
    }

    @ParameterizedTest
    @CsvSource({
            "VPN network connection is down,Network",
            "SQL database deadlock on transactions,Database",
            "payment application error page unavailable,Application"
    })
    void keywordClassifierSelectsMatchingCategory(String description, String expectedName) {
        List<Category> categories = List.of(new Category("Application"), new Category("Network"), new Category("Database"));
        Category result = new KeywordCategoryClassifier().classify(description, categories).orElseThrow();
        assertEquals(expectedName, result.getName());
    }

    @Test
    void fullIncidentLifecycleReachesClosedAndStoresResolution() {
        Incident incident = createIncident("Payment application is unavailable", 5, 5);
        assertEquals(IncidentStatus.NEW, incident.getStatus());

        incident = incidentService.classify(incident.getId(), null);
        assertEquals(IncidentStatus.CLASSIFIED, incident.getStatus());
        assertEquals("Application", incident.getCategory().getName());

        incident = incidentService.assign(incident.getId());
        assertEquals(IncidentStatus.ASSIGNED, incident.getStatus());
        assertNotNull(incident.getAssignedTo());
        incident = incidentService.acknowledge(incident.getId());
        assertEquals(IncidentStatus.ACKNOWLEDGED, incident.getStatus());
        incident = incidentService.startWork(incident.getId());
        assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());
        incident = incidentService.resolve(incident.getId(), "Restarted payment service");
        assertEquals(IncidentStatus.RESOLVED, incident.getStatus());
        assertEquals("Restarted payment service", incident.getResolution());
        incident = incidentService.verify(incident.getId());
        assertEquals(IncidentStatus.VERIFIED, incident.getStatus());
        incident = incidentService.close(incident.getId());
        assertEquals(IncidentStatus.CLOSED, incident.getStatus());
    }

    @Test
    void illegalStateTransitionThrowsInvalidStateTransitionException() {
        Incident incident = unsavedIncident("Illegal transition");
        assertEquals(IncidentStatus.NEW, incident.getStatus());
        InvalidStateTransitionException error = assertThrows(InvalidStateTransitionException.class,
                () -> incident.assign(ignored -> new Technician("T", "t@example.test", Set.of("Network"), 1, 1)));
        assertTrue(error.getMessage().contains("NEW"));
    }

    @Test
    void serviceRejectsAssignmentBeforeClassification() {
        Incident incident = createIncident("Unclassified access point", 2, 2);
        assertThrows(InvalidStateTransitionException.class, () -> incidentService.assign(incident.getId()));
        assertEquals(IncidentStatus.NEW, incidentService.get(incident.getId()).getStatus());
    }

    @Test
    void skillBasedAndLoadBasedStrategiesChooseDifferentBestCandidates() {
        Technician specialist = mockTechnician(1L, "Network specialist", Set.of("Network"), 2, 5);
        Technician generalist = mockTechnician(2L, "Available generalist", Set.of("Application"), 5, 5);
        TechnicianRepository technicians = mock(TechnicianRepository.class);
        IncidentRepository incidents = mock(IncidentRepository.class);
        when(technicians.findAll()).thenReturn(List.of(specialist, generalist));
        when(incidents.countByAssignedToIdAndStatusIn(eq(1L), anyCollection())).thenReturn(3L);
        when(incidents.countByAssignedToIdAndStatusIn(eq(2L), anyCollection())).thenReturn(0L);
        AssignmentService service = new AssignmentService(technicians, incidents,
                List.of(new SkillBasedAssignmentStrategy(), new LoadBasedAssignmentStrategy()));
        Incident networkIncident = unsavedIncident("Router connection", new Category("Network"));

        assertEquals("Network specialist", service.pick(networkIncident).getName());
        service.useStrategy("LOAD_BASED");
        assertEquals("Available generalist", service.pick(networkIncident).getName());
    }

    @Test
    void assignmentRejectsTechniciansAtCapacity() {
        Technician full = mockTechnician(7L, "Full technician", Set.of("Network"), 4, 1);
        TechnicianRepository technicians = mock(TechnicianRepository.class);
        IncidentRepository incidents = mock(IncidentRepository.class);
        when(technicians.findAll()).thenReturn(List.of(full));
        when(incidents.countByAssignedToIdAndStatusIn(eq(7L), anyCollection())).thenReturn(1L);
        AssignmentService service = new AssignmentService(technicians, incidents,
                List.of(new SkillBasedAssignmentStrategy(), new LoadBasedAssignmentStrategy()));

        assertThrows(TechnicianUnavailableException.class,
                () -> service.pick(unsavedIncident("Network unavailable", new Category("Network"))));
    }

    @Test
    void slaPolicyUsesServiceSpecificTargetWhenAvailable() {
        Incident p2Payment = createIncident("Payment issue", 3, 2);
        assertEquals(Priority.P2, p2Payment.getPriority());
        SlaPolicy target = slaService.policyFor(p2Payment);
        assertEquals(10, target.getResponseMinutes());
        assertEquals(90, target.getResolutionMinutes());
    }

    @Test
    void slaTrackingEmitsWarningAtEightyPercent() {
        LocalDateTime start = LocalDateTime.of(2025, 1, 1, 12, 0);
        SlaTracking tracking = new SlaTracking(start, 10, 20);
        List<SlaAlert> alerts = tracking.evaluate(start.plusMinutes(8));
        assertEquals(List.of(SlaAlert.RESPONSE_WARNING), alerts);
        assertEquals("AT_RISK", tracking.currentState());
    }

    @Test
    void slaTrackingEmitsBreachOnceAfterDeadline() {
        LocalDateTime start = LocalDateTime.of(2025, 1, 1, 12, 0);
        SlaTracking tracking = new SlaTracking(start, 10, 20);
        List<SlaAlert> alerts = tracking.evaluate(start.plusMinutes(21));
        assertTrue(alerts.contains(SlaAlert.RESPONSE_BREACH));
        assertTrue(alerts.contains(SlaAlert.RESOLUTION_BREACH));
        assertEquals("BREACHED", tracking.currentState());
        assertTrue(tracking.evaluate(start.plusMinutes(22)).isEmpty());
    }

    @Test
    void scheduledSlaMonitorDelegatesToSlaService() {
        SlaService service = mock(SlaService.class);
        new SlaMonitor(service).run();
        verify(service).check();
    }

    @Test
    void creationPublishesAuditAndInAppNotificationEvents() {
        Incident incident = createIncident("Database query is slow", 2, 3);
        assertTrue(auditRepository.findAll().stream().anyMatch(a ->
                incident.getId().equals(a.getIncidentId()) && "CREATED".equals(a.getAction())));
        Long reporterId = incident.getReporter().getId();
        assertTrue(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(reporterId).stream()
                .anyMatch(n -> incident.getId().equals(n.getIncidentId()) && n.getMessage().contains("Incident created")));
    }

    @Test
    void secondSimilarIncidentCorrelatesToFirstIncident() {
        Incident first = createIncident("Codexunique payment application server unavailable", 2, 2);
        Incident second = createIncident("Codexunique payment application server is unavailable", 2, 2);

        assertEquals(first.getId(), second.getParentIncident().getId());
        assertEquals(1, incidentRepository.countByParentIncidentId(first.getId()));
    }

    @Test
    void thirdSimilarIncidentEscalatesRootAndRecordsCorrelationEvents() {
        Incident root = createIncident("Codexunique payment application server unavailable", 2, 2);
        assertEquals(Priority.P3, root.getPriority());
        Incident second = createIncident("Codexunique payment application server is unavailable", 2, 2);
        Incident third = createIncident("Codexunique payment application server is not available", 2, 2);

        Incident escalatedRoot = incidentService.get(root.getId());
        assertEquals(root.getId(), second.getParentIncident().getId());
        assertEquals(root.getId(), third.getParentIncident().getId());
        assertEquals(Priority.P2, escalatedRoot.getPriority());
        List<String> actions = auditRepository.findAll().stream()
                .filter(a -> a.getIncidentId().equals(root.getId())
                        || a.getIncidentId().equals(second.getId())
                        || a.getIncidentId().equals(third.getId()))
                .map(AuditRecord::getAction).toList();
        assertTrue(actions.contains(EventType.CORRELATED.name()));
        assertTrue(actions.contains(EventType.ESCALATED.name()));
    }

    @Test
    void technicianCannotOpenAdminPage() throws Exception {
        mvc.perform(get("/ui/admin").with(user("ravi@corp.test").roles("TECHNICIAN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeCannotOpenDashboard() throws Exception {
        mvc.perform(get("/ui/dashboard").with(user("asha@corp.test").roles("EMPLOYEE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void technicianCannotOpenIncidentCreationForm() throws Exception {
        mvc.perform(get("/ui/incidents/new").with(user("ravi@corp.test").roles("TECHNICIAN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyTheAssignedTechnicianCanResolveAnIncident() throws Exception {
        Incident inProgress = createInProgressIncident("Payment application unavailable");
        String otherTechnician = inProgress.getAssignedTo().getEmail().equals("ravi@corp.test")
                ? "meera@corp.test" : "ravi@corp.test";
        mvc.perform(post("/ui/incidents/{id}/resolve", inProgress.getId())
                        .with(user(otherTechnician).roles("TECHNICIAN"))
                        .with(csrf()).param("resolution", "Restarted the service"))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignedTechnicianCanSaveResolutionDetails() throws Exception {
        Incident inProgress = createInProgressIncident("Payment application unavailable");
        String assignedTechnician = inProgress.getAssignedTo().getEmail();
        mvc.perform(post("/ui/incidents/{id}/resolve", inProgress.getId())
                        .with(user(assignedTechnician).roles("TECHNICIAN"))
                        .with(csrf()).param("resolution", "Restarted the payment service"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ui/incidents/" + inProgress.getId()));
        Incident resolved = incidentService.get(inProgress.getId());
        assertEquals(IncidentStatus.RESOLVED, resolved.getStatus());
        assertEquals("Restarted the payment service", resolved.getResolution());
    }

    @Test
    void dashboardReportsIncidentCounts() {
        long totalBefore = reportService.dashboard().overview().total();
        long newBefore = reportService.dashboard().byStatus().getOrDefault("NEW", 0L);
        createIncident("New reporting metric incident", 1, 1);
        ReportService.Dashboard dashboard = reportService.dashboard();
        assertEquals(totalBefore + 1, dashboard.overview().total());
        assertEquals(newBefore + 1, dashboard.byStatus().get("NEW"));
    }

    @Test
    void allThymeleafPagesAndRootRouteAreWired() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(view().name("login"));
        mvc.perform(get("/register")).andExpect(status().isOk()).andExpect(view().name("register"));
        mvc.perform(get("/").with(user("asha@corp.test").roles("EMPLOYEE")))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/ui/incidents"));
        Incident incident = createIncident("A test incident for rendering", 2, 2);
        mvc.perform(get("/ui/incidents").with(user("asha@corp.test").roles("EMPLOYEE")))
                .andExpect(status().isOk()).andExpect(view().name("incidents"));
        mvc.perform(get("/ui/incidents/new").with(user("asha@corp.test").roles("EMPLOYEE")))
                .andExpect(status().isOk()).andExpect(view().name("new-incident"));
        mvc.perform(get("/ui/incidents/{id}", incident.getId()).with(user("asha@corp.test").roles("EMPLOYEE")))
                .andExpect(status().isOk()).andExpect(view().name("incident"));
        mvc.perform(get("/ui/dashboard").with(user("ravi@corp.test").roles("TECHNICIAN")))
                .andExpect(status().isOk()).andExpect(view().name("dashboard"));
        mvc.perform(get("/ui/admin").with(user("admin@corp.test").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(view().name("admin"));
        mvc.perform(get("/ui/notifications").with(user("asha@corp.test").roles("EMPLOYEE")))
                .andExpect(status().isOk()).andExpect(view().name("notifications"));
        mvc.perform(get("/css/app.css")).andExpect(status().isOk());
    }

    @Test
    void registrationFormStoresAnEncodedPassword() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("name", "New Employee")
                        .param("email", "new.employee@corp.test")
                        .param("password", "secret123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        User registered = userRepository.findByEmail("new.employee@corp.test").orElseThrow();
        assertTrue(passwordEncoder.matches("secret123", registered.passwordHash()));
        assertEquals(Role.EMPLOYEE, registered.getRole());
    }

    private Incident createIncident(String description, int impact, int urgency) {
        User reporter = ensureEmployee("asha@corp.test", "Asha");
        ItService payment = serviceRepository.findAll().stream()
                .filter(service -> "Payment".equals(service.getName())).findFirst().orElseThrow();
        return incidentService.create(new CreateIncidentRequest(description, payment.getId(), null, impact, urgency),
                reporter.getEmail());
    }

    private Incident createInProgressIncident(String description) {
        Incident incident = createIncident(description, 5, 5);
        incident = incidentService.classify(incident.getId(), null);
        incident = incidentService.assign(incident.getId());
        incident = incidentService.acknowledge(incident.getId());
        return incidentService.startWork(incident.getId());
    }

    private Incident unsavedIncident(String description) {
        return unsavedIncident(description, new Category("Application"));
    }

    private Incident unsavedIncident(String description, Category category) {
        return new Incident(description, new Employee("Reporter", "reporter@example.test", "Test"),
                new ItService("Test service", 3), category, 2, 2,
                new PriorityResult(Priority.P3, 12));
    }

    private Employee ensureEmployee(String email, String name) {
        return userRepository.findByEmail(email).filter(Employee.class::isInstance).map(Employee.class::cast)
                .orElseGet(() -> {
                    Employee employee = new Employee(name, email, "Test");
                    employee.changePassword(passwordEncoder.encode("password123"));
                    return userRepository.save(employee);
                });
    }

    private Technician ensureTechnician(String email, String name, Set<String> skills, int experience, int maxWorkload) {
        return userRepository.findByEmail(email).filter(Technician.class::isInstance).map(Technician.class::cast)
                .orElseGet(() -> {
                    Technician technician = new Technician(name, email, skills, experience, maxWorkload);
                    technician.changePassword(passwordEncoder.encode("password123"));
                    return userRepository.save(technician);
                });
    }

    private ItService ensureService(String name, int criticality) {
        return serviceRepository.findAll().stream().filter(service -> name.equals(service.getName())).findFirst()
                .orElseGet(() -> serviceRepository.save(new ItService(name, criticality)));
    }

    private Category ensureCategory(String name) {
        return categoryRepository.findAll().stream().filter(category -> name.equals(category.getName())).findFirst()
                .orElseGet(() -> categoryRepository.save(new Category(name)));
    }

    private Technician mockTechnician(Long id, String name, Set<String> skills, int experience, int capacity) {
        Technician technician = mock(Technician.class);
        when(technician.getId()).thenReturn(id);
        when(technician.getName()).thenReturn(name);
        when(technician.getRole()).thenReturn(Role.TECHNICIAN);
        when(technician.getSkills()).thenReturn(skills);
        when(technician.getExperienceYears()).thenReturn(experience);
        when(technician.getMaxWorkload()).thenReturn(capacity);
        when(technician.isActive()).thenReturn(true);
        return technician;
    }
}
