package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
    long countByParentIncidentId(Long parentId);
    List<Incident> findByParentIncidentId(Long parentId);
    boolean existsByCategoryId(Long categoryId);
    boolean existsByServiceId(Long serviceId);
    long countByAssignedToIdAndStatusIn(Long technicianId, java.util.Collection<IncidentStatus> statuses);
    @Query("select i from Incident i where i.sla is not null and i.sla.resolvedAt is null")
    List<Incident> findWithOpenSla();

    @Query("select i from Incident i where i.sla.responseBreached = true or i.sla.resolutionBreached = true")
    List<Incident> findBreached();
}

