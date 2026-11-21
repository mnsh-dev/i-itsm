package com.example.demo;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

record AdminUserRequest(String name, String email, String password, Role role, String department,
                        Set<String> skills, Integer experienceYears, Integer maxWorkload) {}
record RoleUpdateRequest(Role role) {}
record TechnicianRequest(String name, String email, String password, Set<String> skills,
                         Integer experienceYears, Integer maxWorkload) {}
record TechnicianProfileRequest(Set<String> skills, int experienceYears, int maxWorkload, boolean active) {}
record ServiceRequest(String name, int businessCriticality) {}
record CategoryRequest(String name) {}

@RestController
@RequestMapping("/api/admin")
class AdminManagementController {
    private final AdminManagementService management;

    AdminManagementController(AdminManagementService management) { this.management = management; }

    @GetMapping("/users")
    List<User> users() { return management.users(); }

    @PostMapping("/users")
    User createUser(@RequestBody AdminUserRequest request) {
        return management.createUser(request.name(), request.email(), request.password(), request.role(),
                request.department(), request.skills(), request.experienceYears(), request.maxWorkload());
    }

    @PostMapping("/users/{id}/role")
    User changeRole(@PathVariable Long id, @RequestBody RoleUpdateRequest request) {
        return management.changeRole(id, request.role());
    }

    @GetMapping("/technicians")
    List<TechnicianAdminView> technicians() { return management.technicians(); }

    @PostMapping("/technicians")
    User createTechnician(@RequestBody TechnicianRequest request) {
        return management.createOrPromoteTechnician(request.name(), request.email(), request.password(),
                request.skills(), request.experienceYears() == null ? 0 : request.experienceYears(),
                request.maxWorkload() == null ? 5 : request.maxWorkload());
    }

    @PostMapping("/technicians/{id}")
    Technician updateTechnician(@PathVariable Long id, @RequestBody TechnicianProfileRequest request) {
        return management.updateTechnician(id, request.skills(), request.experienceYears(),
                request.maxWorkload(), request.active());
    }

    @GetMapping("/services")
    List<ItService> services() { return management.services(); }

    @PostMapping("/services")
    ItService createService(@RequestBody ServiceRequest request) {
        return management.createService(request.name(), request.businessCriticality());
    }

    @PostMapping("/services/{id}")
    ItService updateService(@PathVariable Long id, @RequestBody ServiceRequest request) {
        return management.updateService(id, request.name(), request.businessCriticality());
    }

    @GetMapping("/categories")
    List<Category> categories() { return management.categories(); }

    @PostMapping("/categories")
    Category createCategory(@RequestBody CategoryRequest request) {
        return management.createCategory(request.name());
    }

    @PostMapping("/categories/{id}")
    Category updateCategory(@PathVariable Long id, @RequestBody CategoryRequest request) {
        return management.updateCategory(id, request.name());
    }
}
