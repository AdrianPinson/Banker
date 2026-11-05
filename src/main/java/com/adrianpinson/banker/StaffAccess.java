package com.adrianpinson.banker;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class StaffAccess {
    private final boolean enabled;
    public StaffAccess(@Value("${banker.security.enabled:true}") boolean enabled) { this.enabled = enabled; }
    private Authentication authentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal()))
            throw new BankException(401, "SIGN_IN_REQUIRED", "Sign in to use the teller workspace.");
        return auth;
    }
    public String actor() { return enabled ? authentication().getName() : "local-demo"; }
    public boolean isManager() {
        return !enabled || authentication().getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER"));
    }
    public void manager() {
        if (!isManager()) throw new BankException(403, "MANAGER_REQUIRED", "A manager must approve this action.");
    }
    public void claimAs(String teller) {
        if (enabled && !actor().equals(teller))
            throw new BankException(403, "TELLER_MISMATCH", "Claim clients using your signed-in staff account.");
    }
    public void owns(BankModels.Ticket ticket) {
        if (enabled && !actor().equals(ticket.teller()))
            throw new BankException(403, "TICKET_OWNER_REQUIRED", "This ticket belongs to another staff member.");
    }
}
