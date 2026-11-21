package com.example.demo;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Set;

record TechnicianAdminView(Long id, String name, String email, Set<String> skills,
                           int experienceYears, int maxWorkload, int activeWorkload, boolean active) {
    public String skillsText() { return String.join(", ", skills); }
}

@Service
public class AdminManagementService {
    private final UserRepository users;
    private final TechnicianRepository technicians;
    private final IncidentRepository incidents;
    private final ItServiceRepository services;
    private final CategoryRepository categories;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    public AdminManagementService(UserRepository users, TechnicianRepository technicians,
                                  IncidentRepository incidents, ItServiceRepository services,
                                  CategoryRepository categories, PasswordEncoder passwordEncoder,
                                  JdbcTemplate jdbc, EntityManager entityManager) {
        this.users = users;
        this.technicians = technicians;
        this.incidents = incidents;
        this.services = services;
        this.categories = categories;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    public List<User> users() { return users.findAll(); }

    @Transactional
    public User createUser(String name, String email, String password, Role role, String department,
                           Set<String> skills, Integer experienceYears, Integer maxWorkload) {
        validateIdentity(name, email, password);
        if (role == null) throw new IllegalArgumentException("User role is required");
        String cleanEmail = email.trim();
        if (users.findByEmail(cleanEmail).isPresent()) throw new IllegalArgumentException("Email is already registered");
        User user = switch (role) {
            case EMPLOYEE -> new Employee(name.trim(), cleanEmail, department == null ? "Unspecified" : department.trim());
            case ADMIN -> new Administrator(name.trim(), cleanEmail);
            case TECHNICIAN -> new Technician(name.trim(), cleanEmail, skills == null ? Set.of() : skills,
                    experienceYears == null ? 0 : experienceYears, maxWorkload == null ? 5 : maxWorkload);
        };
        user.changePassword(passwordEncoder.encode(password));
        return users.save(user);
    }

    @Transactional
    public Technician createOrPromoteTechnician(String name, String email, String password,
                                                 Set<String> skills, int experienceYears, int maxWorkload) {
        User existing = users.findByEmail(email.trim()).orElse(null);
        if (existing == null) {
            return (Technician) createUser(name, email, password, Role.TECHNICIAN, null,
                    skills, experienceYears, maxWorkload);
        }
        User promoted = changeRole(existing.getId(), Role.TECHNICIAN);
        Technician technician = (Technician) promoted;
        return updateTechnician(technician.getId(), skills, experienceYears, maxWorkload, technician.isActive());
    }

    @Transactional
    public User changeRole(Long id, Role role) {
        if (role == null) throw new IllegalArgumentException("User role is required");
        User user = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
        user.changeRole(role);
        users.save(user);
        if (role == Role.TECHNICIAN && !(user instanceof Technician)) {
            entityManager.flush();
            jdbc.update("delete from employee where id = ?", id);
            jdbc.update("delete from administrator where id = ?", id);
            jdbc.update("insert into technician (id, experience_years, max_workload, active) values (?, ?, ?, ?)",
                    id, 0, 5, true);
            entityManager.clear();
            return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
        }
        return user;
    }

    public List<TechnicianAdminView> technicians() {
        return technicians.findAll().stream().filter(t -> t.getRole() == Role.TECHNICIAN)
                .map(t -> new TechnicianAdminView(t.getId(), t.getName(), t.getEmail(),
                Set.copyOf(t.getSkills()), t.getExperienceYears(), t.getMaxWorkload(),
                (int) incidents.countByAssignedToIdAndStatusIn(t.getId(),
                        java.util.EnumSet.of(IncidentStatus.ASSIGNED, IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS)),
                t.isActive())).toList();
    }

    @Transactional
    public Technician updateTechnician(Long id, Set<String> skills, int experienceYears,
                                       int maxWorkload, boolean active) {
        Technician technician = technicians.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Technician", id));
        technician.updateProfile(skills == null ? Set.of() : skills, experienceYears, maxWorkload, active);
        return technician;
    }

    public List<ItService> services() { return services.findAll(); }

    @Transactional
    public ItService createService(String name, int criticality) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Service name is required");
        return services.save(new ItService(name.trim(), criticality));
    }

    @Transactional
    public ItService updateService(Long id, String name, int criticality) {
        ItService service = services.findById(id).orElseThrow(() -> new ResourceNotFoundException("Service", id));
        service.update(name, criticality);
        return service;
    }

    public List<Category> categories() { return categories.findAll(); }

    @Transactional
    public Category createCategory(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Category name is required");
        return categories.save(new Category(name.trim()));
    }

    @Transactional
    public Category updateCategory(Long id, String name) {
        Category category = categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category", id));
        category.updateName(name);
        return category;
    }

    private void validateIdentity(String name, String email, String password) {
        if (name == null || name.isBlank() || email == null || email.isBlank() || password == null || password.isBlank())
            throw new IllegalArgumentException("Name, email, and password are required");
    }
}
