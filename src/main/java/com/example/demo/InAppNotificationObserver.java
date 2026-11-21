package com.example.demo;

import org.springframework.stereotype.Component;

@Component
class InAppNotificationObserver implements IncidentObserver {
    private final NotificationRepository repo;
    InAppNotificationObserver(NotificationRepository repo) { this.repo = repo; }

    @Override public void onEvent(IncidentEvent e) {
        for (User u : e.recipients())
            repo.save(new Notification(u.getId(), e.incident().getId(),
                    "INC" + e.incident().getId() + ": " + e.description()));
    }
}
