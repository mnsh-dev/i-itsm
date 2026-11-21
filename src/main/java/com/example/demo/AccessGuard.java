package com.example.demo;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
class AccessGuard {
    private boolean has(Authentication a, String role) {
        return a.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_" + role));
    }

    boolean isStaff(Authentication a) { return has(a, "ADMIN") || has(a, "TECHNICIAN"); }
    boolean isAdmin(Authentication a) { return has(a, "ADMIN"); }

    void requireStaff(Authentication a) {
        if (!isStaff(a)) throw new UnauthorizedOperationException("Only technicians or administrators can do this");
    }

    void requireCanCreate(Authentication a) {
        if (!has(a, "EMPLOYEE") && !has(a, "ADMIN"))
            throw new UnauthorizedOperationException("Only employees or administrators can report incidents");
    }

    boolean canAct(Incident i, Authentication a, String action) {
        if (action == null) return false;
        try {
            requireCanAct(i, a, action);
            return true;
        } catch (UnauthorizedOperationException denied) {
            return false;
        }
    }

    void requireCanAct(Incident i, Authentication a, String action) {
        switch (action) {
            case "classify", "assign", "close" -> requireStaff(a);
            case "acknowledge", "start", "resolve", "request-info", "notes" -> {
                if (!has(a, "TECHNICIAN") && !has(a, "ADMIN"))
                    throw new UnauthorizedOperationException("Only technicians can work on incidents");
                requireAssignedTechnician(i, a);
            }
            case "verify" -> requireReporter(i, a);
            case "info", "feedback" -> requireIncidentReporter(i, a);
            default -> throw new UnauthorizedOperationException("Unsupported incident action: " + action);
        }
    }

    void requireCanView(Incident i, Authentication a) {
        if (isStaff(a)) return;
        if (!i.getReporter().getEmail().equals(a.getName()))
            throw new UnauthorizedOperationException("You can only view your own incidents");
    }

    void requireAssignedTechnician(Incident i, Authentication a) {
        if (has(a, "ADMIN")) return;
        if (i.getAssignedTo() == null) return;   // nobody assigned yet: the state machine rejects it with a 409
        if (!i.getAssignedTo().getEmail().equals(a.getName()))
            throw new UnauthorizedOperationException("Only the assigned technician can do this");
    }

    void requireReporter(Incident i, Authentication a) {
        if (has(a, "ADMIN")) return;
        if (!i.getReporter().getEmail().equals(a.getName()))
            throw new UnauthorizedOperationException("Only the reporter can verify the resolution");
    }

    private void requireIncidentReporter(Incident i, Authentication a) {
        if (!i.getReporter().getEmail().equals(a.getName()))
            throw new UnauthorizedOperationException("Only the reporter can do this");
    }
}
