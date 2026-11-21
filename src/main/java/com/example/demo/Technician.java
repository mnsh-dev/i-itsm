package com.example.demo;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;

import java.util.HashSet;
import java.util.Set;

@Entity
public class Technician extends User {
    @ElementCollection(fetch = FetchType.EAGER)
    private Set<String> skills = new HashSet<>();
    private int experienceYears;
    private int maxWorkload;
    private Boolean active = true;

    protected Technician() {}
    public Technician(String name, String email, Set<String> skills, int experienceYears, int maxWorkload) {
        super(name, email);
        this.skills = new HashSet<>(skills);
        this.experienceYears = experienceYears;
        this.maxWorkload = maxWorkload;
    }
    public Set<String> getSkills() { return skills; }
    public int getExperienceYears() { return experienceYears; }
    public int getMaxWorkload() { return maxWorkload; }
    public boolean isActive() { return active == null || active; }
    public void updateProfile(Set<String> skills, int experienceYears, int maxWorkload, boolean active) {
        if (experienceYears < 0 || maxWorkload < 1) throw new IllegalArgumentException("Experience must be non-negative and capacity must be positive");
        this.skills = new HashSet<>(skills);
        this.experienceYears = experienceYears;
        this.maxWorkload = maxWorkload;
        this.active = active;
    }
    @Override protected Role defaultRole() { return Role.TECHNICIAN; }
}
