package com.example.demo;

import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class SecurityConfig {
    private final OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;

    SecurityConfig(OAuth2LoginSuccessHandler oauth2LoginSuccessHandler) {
        this.oauth2LoginSuccessHandler = oauth2LoginSuccessHandler;
    }

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    SecurityFilterChain chain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/login", "/register", "/css/**", "/api/auth/register", "/error",
                                "/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers("/api/admin/**", "/ui/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/reports/**", "/ui/dashboard/**").hasAnyRole("TECHNICIAN", "ADMIN")
                        .requestMatchers("/api/technicians/**").hasAnyRole("TECHNICIAN", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/ui/incidents/new").hasAnyRole("EMPLOYEE", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/incidents", "/ui/incidents").hasAnyRole("EMPLOYEE", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/incidents/*/classify", "/api/incidents/*/assign",
                                "/api/incidents/*/acknowledge", "/api/incidents/*/start",
                                "/api/incidents/*/resolve", "/api/incidents/*/close").hasAnyRole("TECHNICIAN", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/incidents/*/recommended-technician").hasAnyRole("TECHNICIAN", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/incidents/*/verify").hasAnyRole("EMPLOYEE", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/incidents/*/info", "/api/incidents/*/feedback").hasAnyRole("EMPLOYEE", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/incidents/*/request-info", "/api/incidents/*/notes").hasAnyRole("TECHNICIAN", "ADMIN")
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .formLogin(f -> f
                        .loginPage("/login")
                        .defaultSuccessUrl("/ui/incidents", true)
                        .permitAll())
                .oauth2Login(oauth -> oauth.loginPage("/login")
                        .successHandler(oauth2LoginSuccessHandler))
                .logout(l -> l
                        .logoutSuccessUrl("/login?logout")
                        .permitAll())
                .csrf(c -> c.ignoringRequestMatchers("/api/**"));
        return http.build();
    }
}
