package com.example.demo;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppUserDetailsService userDetailsService;

    public OAuth2LoginSuccessHandler(UserRepository userRepository,
                                     @org.springframework.context.annotation.Lazy PasswordEncoder passwordEncoder,
                                     AppUserDetailsService userDetailsService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        String email = oauthToken.getPrincipal().getAttribute("email");
        String name = oauthToken.getPrincipal().getAttribute("name");
        if (email == null || email.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Google account did not provide an email address");
            return;
        }

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            Employee employee = new Employee(name != null && !name.isBlank() ? name : email,
                    email, "Google");
            employee.changePassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            return userRepository.save(employee);
        });

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        UsernamePasswordAuthenticationToken appAuthentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(appAuthentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        response.sendRedirect(request.getContextPath() + "/ui/incidents");
    }
}
