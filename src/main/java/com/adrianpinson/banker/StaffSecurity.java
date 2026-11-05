package com.adrianpinson.banker;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class StaffSecurity {
    @Bean UserDetailsService staffUsers(
            @Value("${banker.security.teller-password:teller-demo-only}") String tellerPassword,
            @Value("${banker.security.manager-password:manager-demo-only}") String managerPassword) {
        if (tellerPassword.length() < 12 || managerPassword.length() < 12)
            throw new IllegalArgumentException("Staff passwords must contain at least 12 characters.");
        var encoder = new BCryptPasswordEncoder();
        return new InMemoryUserDetailsManager(
                User.withUsername("teller-1").password("{bcrypt}" + encoder.encode(tellerPassword)).roles("TELLER").build(),
                User.withUsername("teller-2").password("{bcrypt}" + encoder.encode(tellerPassword)).roles("TELLER").build(),
                User.withUsername("manager").password("{bcrypt}" + encoder.encode(managerPassword)).roles("TELLER", "MANAGER").build());
    }
    @Bean @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain staffFilter(HttpSecurity http,
            @Value("${banker.security.enabled:true}") boolean enabled) throws Exception {
        if (!enabled) return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
        return http.authorizeHttpRequests(a -> a
                    .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC).permitAll()
                    .requestMatchers("/api/auth", "/api/login").permitAll()
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().permitAll())
                .formLogin(form -> form.loginProcessingUrl("/api/login")
                    .successHandler((req, res, auth) -> res.setStatus(204))
                    .failureHandler((req, res, ex) -> problem(res, 401, "The staff username or password is incorrect.")))
                .logout(logout -> logout.logoutUrl("/api/logout")
                    .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
                .exceptionHandling(errors -> errors
                    .authenticationEntryPoint((req, res, ex) -> problem(res, 401, "Sign in to use the teller workspace."))
                    .accessDeniedHandler((req, res, ex) -> problem(res, 403, "The session token is invalid. Sign in again.")))
                .requestCache(cache -> cache.disable())
                .build();
    }
    private static void problem(HttpServletResponse response, int status, String detail) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"status\":" + status + ",\"detail\":\"" + detail + "\"}");
    }
    @RestController
    public static class SessionController {
        private final StaffAccess staff;
        private final boolean enabled;
        public SessionController(StaffAccess staff, @Value("${banker.security.enabled:true}") boolean enabled) {
            this.staff = staff; this.enabled = enabled;
        }
        @GetMapping("/api/auth") public Map<String, Object> session(
                java.security.Principal principal, CsrfToken csrf, HttpServletResponse response) {
            response.setHeader("Cache-Control", "no-store");
            return Map.of("enabled", enabled, "username", principal == null ? "" : principal.getName(),
                    "manager", principal != null && staff.isManager(), "csrfToken", csrf == null ? "" : csrf.getToken());
        }
    }
}
