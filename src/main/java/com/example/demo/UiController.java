package com.example.demo;

import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;

@Controller
public class UiController {
    private final IncidentService incidentService;
    private final IncidentSearchService searchService;
    private final ReportService reportService;
    private final AssignmentService assignmentService;
    private final SlaService slaService;
    private final SlaPolicyRepository policyRepo;
    private final AuditRecordRepository auditRepo;
    private final NotificationRepository notificationRepo;
    private final UserRepository userRepo;
    private final ItServiceRepository serviceRepo;
    private final CategoryRepository categoryRepo;
    private final AccessGuard guard;
    private final PasswordEncoder passwordEncoder;
    private final AdminManagementService adminManagement;

    public UiController(IncidentService incidentService, IncidentSearchService searchService,
                        ReportService reportService, AssignmentService assignmentService,
                        SlaService slaService, SlaPolicyRepository policyRepo,
                        AuditRecordRepository auditRepo, NotificationRepository notificationRepo,
                        UserRepository userRepo, ItServiceRepository serviceRepo,
                        CategoryRepository categoryRepo, AccessGuard guard,
                        PasswordEncoder passwordEncoder, AdminManagementService adminManagement) {
        this.incidentService = incidentService;
        this.searchService = searchService;
        this.reportService = reportService;
        this.assignmentService = assignmentService;
        this.slaService = slaService;
        this.policyRepo = policyRepo;
        this.auditRepo = auditRepo;
        this.notificationRepo = notificationRepo;
        this.userRepo = userRepo;
        this.serviceRepo = serviceRepo;
        this.categoryRepo = categoryRepo;
        this.guard = guard;
        this.passwordEncoder = passwordEncoder;
        this.adminManagement = adminManagement;
    }

    @ModelAttribute
    public void addCommonAttributes(Model model, Authentication auth) {
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            model.addAttribute("me", auth.getName());
            boolean isAdmin = guard.isAdmin(auth);
            boolean isTechnician = auth.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals("ROLE_TECHNICIAN"));
            model.addAttribute("isAdmin", isAdmin);
            model.addAttribute("isStaff", guard.isStaff(auth));
            model.addAttribute("isTechnician", isTechnician);
        } else {
            model.addAttribute("me", "");
            model.addAttribute("isAdmin", false);
            model.addAttribute("isStaff", false);
            model.addAttribute("isTechnician", false);
        }
    }

    @GetMapping("/")
    public String home() { return "redirect:/ui/incidents"; }

    @GetMapping("/login")
    public String login() { return "login"; }

    @GetMapping("/register")
    public String registerForm() { return "register"; }

    @PostMapping("/register")
    public String registerSubmit(@RequestParam String name, @RequestParam String email,
                                 @RequestParam String password, RedirectAttributes ra) {
        String cleanEmail = email.trim();
        if (userRepo.findByEmail(cleanEmail).isPresent()) {
            ra.addFlashAttribute("error", "Email is already registered");
            return "redirect:/register";
        }
        Employee employee = new Employee(name.trim(), cleanEmail, "Unspecified");
        employee.changePassword(passwordEncoder.encode(password));
        userRepo.save(employee);
        ra.addFlashAttribute("ok", "Account created! Please sign in.");
        return "redirect:/login";
    }

    @GetMapping("/ui/incidents")
    public String listIncidents(@RequestParam(required = false) IncidentStatus status,
                                @RequestParam(required = false) Priority priority,
                                @RequestParam(required = false) String text,
                                @RequestParam(required = false, defaultValue = "false") boolean mine,
                                @RequestParam(required = false) Long categoryId,
                                @RequestParam(required = false) Long serviceId,
                                @RequestParam(required = false) String slaStatus,
                                @RequestParam(required = false) Long technicianId,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                Authentication auth, Model model) {
        boolean isTechnician = auth != null && auth.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_TECHNICIAN"));
        model.addAttribute("isTechnician", isTechnician);
        Long assignedTechId = null;
        if (mine && isTechnician) {
            User user = userRepo.findByEmail(auth.getName()).orElse(null);
            if (user instanceof Technician) assignedTechId = user.getId();
        }
        Long filterTechnicianId = isTechnician ? (mine ? assignedTechId : null) : technicianId;
        String categoryName = categoryId == null ? null : categoryRepo.findById(categoryId)
                .map(Category::getName).orElse("__unknown_category__");
        String serviceName = serviceId == null ? null : serviceRepo.findById(serviceId)
                .map(ItService::getName).orElse("__unknown_service__");
        IncidentSearchCriteria criteria = new IncidentSearchCriteria(null, status, priority,
                categoryName, serviceName, filterTechnicianId, slaStatus, fromDate, null, text);
        model.addAttribute("incidents", searchService.search(criteria, incident ->
                guard.isStaff(auth) || incident.getReporter().getEmail().equals(auth.getName())));
        model.addAttribute("statuses", IncidentStatus.values());
        model.addAttribute("priorities", Priority.values());
        model.addAttribute("categories", categoryRepo.findAll());
        model.addAttribute("services", serviceRepo.findAll());
        model.addAttribute("technicians", isTechnician ? List.of() : adminManagement.technicians());
        model.addAttribute("slaStatuses", List.of("ON_TRACK", "WARNING", "BREACHED"));
        model.addAttribute("status", status);
        model.addAttribute("priority", priority);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("serviceId", serviceId);
        model.addAttribute("slaStatus", slaStatus);
        model.addAttribute("technicianId", isTechnician ? null : technicianId);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("text", text);
        model.addAttribute("mine", mine && isTechnician && assignedTechId != null);
        return "incidents";
    }

    @GetMapping("/ui/incidents/new")
    public String newIncidentForm(Model model, Authentication auth) {
        guard.requireCanCreate(auth);
        model.addAttribute("services", serviceRepo.findAll());
        model.addAttribute("categories", categoryRepo.findAll());
        return "new-incident";
    }

    @PostMapping("/ui/incidents")
    public String createIncident(@RequestParam String description, @RequestParam Long serviceId,
                                 @RequestParam(required = false) Long categoryId,
                                 @RequestParam int impact, @RequestParam int urgency,
                                 @RequestParam(defaultValue = "1") int affectedUsers,
                                 Authentication auth, RedirectAttributes ra) {
        guard.requireCanCreate(auth);
        try {
            Incident created = incidentService.create(
                    new CreateIncidentRequest(description, serviceId, categoryId, impact, urgency, affectedUsers), auth.getName());
            ra.addFlashAttribute("ok", "Incident INC" + created.getId() + " created.");
            return "redirect:/ui/incidents/" + created.getId();
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/ui/incidents/new";
        }
    }

    @GetMapping("/ui/incidents/{id}")
    public String viewIncident(@PathVariable Long id, Authentication auth, Model model) {
        Incident incident = incidentService.get(id);
        guard.requireCanView(incident, auth);
        String nextAction = nextAction(incident.getStatus());
        model.addAttribute("incident", incident);
        model.addAttribute("categories", categoryRepo.findAll());
        model.addAttribute("history", auditRepo.findByIncidentIdOrderByTimestampAsc(id));
        model.addAttribute("nextAction", nextAction);
        model.addAttribute("nextLabel", nextLabel(nextAction));
        model.addAttribute("canAct", guard.canAct(incident, auth, nextAction));
        model.addAttribute("canRequestInfo", guard.canAct(incident, auth, "request-info")
                && isTechnicianWorkState(incident.getStatus()));
        model.addAttribute("canAddNote", guard.canAct(incident, auth, "notes")
                && isTechnicianWorkState(incident.getStatus()));
        model.addAttribute("canProvideInfo", guard.canAct(incident, auth, "info")
                && isOpenForInfo(incident.getStatus()));
        model.addAttribute("canFeedback", incident.getReporter().getEmail().equals(auth.getName())
                && incident.getFeedbackRating() == null
                && (incident.getStatus() == IncidentStatus.RESOLVED || incident.getStatus() == IncidentStatus.CLOSED));
        return "incident";
    }

    @PostMapping("/ui/incidents/{id}/{act}")
    public String actOnIncident(@PathVariable Long id, @PathVariable String act,
                                @RequestParam(required = false) Long categoryId,
                                @RequestParam(required = false) String resolution,
                                Authentication auth, RedirectAttributes ra) {
        try {
            Incident incident = incidentService.get(id);
            guard.requireCanView(incident, auth);
            guard.requireCanAct(incident, auth, act);
            switch (act) {
                case "classify" -> incidentService.classify(id, categoryId);
                case "assign" -> incidentService.assign(id);
                case "acknowledge" -> incidentService.acknowledge(id);
                case "start" -> incidentService.startWork(id);
                case "resolve" -> incidentService.resolve(id, resolution);
                case "verify" -> incidentService.verify(id);
                case "close" -> incidentService.close(id);
                default -> throw new IllegalArgumentException("Unknown action: " + act);
            }
            ra.addFlashAttribute("ok", "Action '" + act + "' completed.");
        } catch (UnauthorizedOperationException ex) {
            throw ex;
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/ui/incidents/" + id;
    }

    @PostMapping("/ui/incidents/{id}/request-info")
    public String requestInfo(@PathVariable Long id, @RequestParam String question,
                              Authentication auth, RedirectAttributes ra) {
        try {
            Incident incident = incidentService.get(id);
            guard.requireCanView(incident, auth);
            guard.requireCanAct(incident, auth, "request-info");
            incidentService.requestInformation(id, question);
            ra.addFlashAttribute("ok", "Information request sent to the reporter.");
        } catch (UnauthorizedOperationException ex) {
            throw ex;
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/ui/incidents/" + id;
    }

    @PostMapping("/ui/incidents/{id}/info")
    public String addInfo(@PathVariable Long id, @RequestParam String message,
                          Authentication auth, RedirectAttributes ra) {
        try {
            Incident incident = incidentService.get(id);
            guard.requireCanView(incident, auth);
            guard.requireCanAct(incident, auth, "info");
            incidentService.addInformation(id, message);
            ra.addFlashAttribute("ok", "Additional information added.");
        } catch (UnauthorizedOperationException ex) {
            throw ex;
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/ui/incidents/" + id;
    }

    @PostMapping("/ui/incidents/{id}/notes")
    public String addInvestigationNote(@PathVariable Long id, @RequestParam String message,
                                       Authentication auth, RedirectAttributes ra) {
        try {
            Incident incident = incidentService.get(id);
            guard.requireCanView(incident, auth);
            guard.requireCanAct(incident, auth, "notes");
            incidentService.addInvestigationNote(id, message);
            ra.addFlashAttribute("ok", "Investigation note added to history.");
        } catch (UnauthorizedOperationException ex) {
            throw ex;
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/ui/incidents/" + id;
    }

    @PostMapping("/ui/incidents/{id}/feedback")
    public String submitFeedback(@PathVariable Long id, @RequestParam Integer rating,
                                 @RequestParam(required = false) String comment,
                                 Authentication auth, RedirectAttributes ra) {
        try {
            Incident incident = incidentService.get(id);
            guard.requireCanView(incident, auth);
            guard.requireCanAct(incident, auth, "feedback");
            incidentService.submitFeedback(id, rating, comment);
            ra.addFlashAttribute("ok", "Thank you for your feedback.");
        } catch (UnauthorizedOperationException ex) {
            throw ex;
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/ui/incidents/" + id;
    }

    @GetMapping("/ui/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("d", reportService.dashboard());
        model.addAttribute("workloads", assignmentService.workloads());
        return "dashboard";
    }

    @GetMapping("/ui/admin")
    public String admin(Model model) {
        model.addAttribute("strategies", assignmentService.availableStrategies());
        model.addAttribute("activeStrategy", assignmentService.currentStrategy());
        model.addAttribute("policies", policyRepo.findAll());
        model.addAttribute("audit", auditRepo.findTop50ByOrderByTimestampDesc());
        model.addAttribute("users", adminManagement.users());
        model.addAttribute("technicians", adminManagement.technicians());
        model.addAttribute("services", adminManagement.services());
        model.addAttribute("categories", adminManagement.categories());
        return "admin";
    }

    @PostMapping("/ui/admin/users")
    public String createAdminUser(@RequestParam String name, @RequestParam String email,
                                  @RequestParam String password, @RequestParam Role role,
                                  @RequestParam(required = false) String department,
                                  @RequestParam(required = false) String skills,
                                  @RequestParam(required = false) Integer experienceYears,
                                  @RequestParam(required = false) Integer maxWorkload,
                                  RedirectAttributes ra) {
        try {
            adminManagement.createUser(name, email, password, role, department, parseSkills(skills), experienceYears, maxWorkload);
            ra.addFlashAttribute("ok", "User account created.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/ui/admin";
    }

    @PostMapping("/ui/admin/users/{id}/role")
    public String changeUserRole(@PathVariable Long id, @RequestParam Role role, RedirectAttributes ra) {
        try {
            adminManagement.changeRole(id, role);
            ra.addFlashAttribute("ok", "User role updated.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/ui/admin";
    }

    @PostMapping("/ui/admin/technicians")
    public String createTechnician(@RequestParam String name, @RequestParam String email,
                                   @RequestParam String password, @RequestParam(required = false) String skills,
                                   @RequestParam(defaultValue = "0") int experienceYears,
                                   @RequestParam(defaultValue = "5") int maxWorkload,
                                   RedirectAttributes ra) {
        try {
            adminManagement.createOrPromoteTechnician(name, email, password,
                    parseSkills(skills), experienceYears, maxWorkload);
            ra.addFlashAttribute("ok", "Technician account created or promoted.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/ui/admin";
    }

    @PostMapping("/ui/admin/technicians/{id}")
    public String updateTechnician(@PathVariable Long id, @RequestParam(required = false) String skills,
                                   @RequestParam int experienceYears, @RequestParam int maxWorkload,
                                   @RequestParam(defaultValue = "false") boolean active,
                                   RedirectAttributes ra) {
        try {
            adminManagement.updateTechnician(id, parseSkills(skills), experienceYears, maxWorkload, active);
            ra.addFlashAttribute("ok", "Technician profile updated.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/ui/admin";
    }

    @PostMapping("/ui/admin/services")
    public String createService(@RequestParam String name, @RequestParam int businessCriticality, RedirectAttributes ra) {
        try {
            adminManagement.createService(name, businessCriticality);
            ra.addFlashAttribute("ok", "IT service created.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/ui/admin";
    }

    @PostMapping("/ui/admin/services/{id}")
    public String updateService(@PathVariable Long id, @RequestParam String name,
                                @RequestParam int businessCriticality, RedirectAttributes ra) {
        try {
            adminManagement.updateService(id, name, businessCriticality);
            ra.addFlashAttribute("ok", "IT service updated.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/ui/admin";
    }

    @PostMapping("/ui/admin/services/{id}/delete")
    public String deleteService(@PathVariable Long id, RedirectAttributes ra) {
        try {
            adminManagement.deleteService(id);
            ra.addFlashAttribute("ok", "IT service deleted.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/ui/admin";
    }

    @PostMapping("/ui/admin/categories")
    public String createCategory(@RequestParam String name, RedirectAttributes ra) {
        try {
            adminManagement.createCategory(name);
            ra.addFlashAttribute("ok", "Incident category created.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/ui/admin";
    }

    @PostMapping("/ui/admin/categories/{id}")
    public String updateCategory(@PathVariable Long id, @RequestParam String name, RedirectAttributes ra) {
        try {
            adminManagement.updateCategory(id, name);
            ra.addFlashAttribute("ok", "Incident category updated.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/ui/admin";
    }

    @PostMapping("/ui/admin/categories/{id}/delete")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes ra) {
        try {
            adminManagement.deleteCategory(id);
            ra.addFlashAttribute("ok", "Incident category deleted.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/ui/admin";
    }

    @PostMapping("/ui/admin/strategy")
    public String setStrategy(@RequestParam String name, RedirectAttributes ra) {
        try {
            assignmentService.useStrategy(name);
            ra.addFlashAttribute("ok", "Strategy switched to " + name);
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/ui/admin";
    }

    @PostMapping("/ui/admin/sla/{id}")
    public String updateSla(@PathVariable Long id, @RequestParam int response,
                            @RequestParam int resolution, RedirectAttributes ra) {
        try {
            slaService.updatePolicy(id, response, resolution);
            ra.addFlashAttribute("ok", "SLA policy updated.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/ui/admin";
    }

    @GetMapping("/ui/notifications")
    public String notifications(Authentication auth, Model model) {
        User user = userRepo.findByEmail(auth.getName()).orElseThrow();
        model.addAttribute("items", notificationRepo.findByRecipientIdOrderByCreatedAtDesc(user.getId()));
        return "notifications";
    }

    private String nextAction(IncidentStatus status) {
        return switch (status) {
            case NEW -> "classify";
            case CLASSIFIED -> "assign";
            case ASSIGNED -> "acknowledge";
            case ACKNOWLEDGED -> "start";
            case IN_PROGRESS -> "resolve";
            case AWAITING_INFO -> null;
            case RESOLVED -> "verify";
            case VERIFIED -> "close";
            case CLOSED -> null;
        };
    }

    private String nextLabel(String action) {
        if (action == null) return null;
        return switch (action) {
            case "classify" -> "Classify incident";
            case "assign" -> "Auto-assign technician";
            case "acknowledge" -> "Acknowledge incident";
            case "start" -> "Start investigation";
            case "resolve" -> "Mark as resolved";
            case "verify" -> "Verify resolution";
            case "close" -> "Close incident";
            default -> action;
        };
    }

    private Set<String> parseSkills(String skills) {
        if (skills == null || skills.isBlank()) return Set.of();
        return Arrays.stream(skills.split(",")).map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toSet());
    }

    private boolean isTechnicianWorkState(IncidentStatus status) {
        return status == IncidentStatus.ASSIGNED || status == IncidentStatus.ACKNOWLEDGED
                || status == IncidentStatus.IN_PROGRESS;
    }

    private boolean isOpenForInfo(IncidentStatus status) {
        return status == IncidentStatus.NEW || status == IncidentStatus.CLASSIFIED
                || status == IncidentStatus.ASSIGNED || status == IncidentStatus.ACKNOWLEDGED
                || status == IncidentStatus.IN_PROGRESS || status == IncidentStatus.AWAITING_INFO;
    }
}
