package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, Long> {
    List<AuditRecord> findByIncidentIdOrderByTimestampAsc(Long incidentId);
    List<AuditRecord> findTop50ByOrderByTimestampDesc();
}
