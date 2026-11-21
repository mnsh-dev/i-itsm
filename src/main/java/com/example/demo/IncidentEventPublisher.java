package com.example.demo;

import jakarta.persistence.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class IncidentEventPublisher {
    private final List<IncidentObserver> observers;   // Spring injects every implementation
    public IncidentEventPublisher(List<IncidentObserver> observers) { this.observers = observers; }

    public void publish(IncidentEvent event) {
        for (IncidentObserver o : observers) o.onEvent(event);
    }
}


