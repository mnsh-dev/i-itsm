package com.example.demo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
class EmailNotificationObserver implements IncidentObserver {   // simulated email
    private static final Logger log = LoggerFactory.getLogger(EmailNotificationObserver.class);
    @Autowired(required = false) private JavaMailSender mailSender;
    @org.springframework.beans.factory.annotation.Value("${spring.mail.username:}") private String fromEmail;

    private static final Set<EventType> EMAILED =
            EnumSet.of(EventType.CREATED, EventType.ASSIGNED, EventType.RESOLVED, EventType.CLOSED,
                    EventType.SLA_WARNING, EventType.SLA_VIOLATED);

    @Override public void onEvent(IncidentEvent e) {
        if (!EMAILED.contains(e.type())) return;
        for (User u : e.recipients()) {
            log.info("[EMAIL] to {}: INC{} - {}", u.getEmail(), e.incident().getId(), e.description());
            if (mailSender != null && !fromEmail.isBlank()
                    && !u.getEmail().toLowerCase(java.util.Locale.ROOT).endsWith("@corp.test")) {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(fromEmail);
                msg.setTo(u.getEmail());
                msg.setSubject("I-ITSM incident INC" + e.incident().getId() + ": " + e.type());
                msg.setText(e.description());
                try {
                    mailSender.send(msg);
                } catch (Exception ex) {
                    log.warn("Unable to send incident email to {} for INC{}",
                            u.getEmail(), e.incident().getId(), ex);
                }
            }
        }
    }
}
