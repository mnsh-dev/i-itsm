package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Long> {
    Optional<SlaPolicy> findFirstByPriorityAndServiceId(Priority priority, Long serviceId);
    Optional<SlaPolicy> findFirstByPriorityAndServiceIsNull(Priority priority);
}
