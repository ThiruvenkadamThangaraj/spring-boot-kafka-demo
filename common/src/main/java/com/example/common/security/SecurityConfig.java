package com.example.common.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security Configuration with JWT Authentication
 * Configures security for REST APIs with role-based authorization
 * 
 * @author DevOps Team
 * @version 1.0
 * @since 2026-01-13
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
public class SecurityConfig {
    
    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    
    @Autowired
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    
    /**
     * Configure HTTP security with JWT authentication
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for stateless REST APIs
            .csrf(csrf -> csrf.disable())
            
            // Disable CORS (configure properly in production)
            .cors(cors -> cors.disable())
            
            // Exception handling
            .exceptionHandling(exception -> exception
                    .authenticationEntryPoint(jwtAuthenticationEntryPoint))
            
            // Session management - stateless (no sessions)
            .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                    // Public endpoints - no authentication required
                    .requestMatchers(
                            "/api/auth/**",
                            "/api/public/**",
                            "/actuator/health",
                            "/actuator/info",
                            "/swagger-ui/**",
                            "/v3/api-docs/**",
                            "/swagger-ui.html"
                    ).permitAll()
                    
                    // Admin endpoints - require ADMIN role only
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    
                    // Operator endpoints - require OPERATOR or ADMIN role
                    .requestMatchers("/api/operator/**").hasAnyRole("OPERATOR", "ADMIN")
                    
                    // Reviewer endpoints - require REVIEWER, OPERATOR, or ADMIN role
                    .requestMatchers("/api/reviewer/**").hasAnyRole("REVIEWER", "OPERATOR", "ADMIN")
                    
                    // User endpoints - accessible by all authenticated users
                    .requestMatchers("/api/users/**").hasAnyRole("USER", "REVIEWER", "OPERATOR", "ADMIN")
                    
                    // Async endpoints - require authentication
                    .requestMatchers("/api/async/**").authenticated()
                    
                    // Demo endpoints - public for testing
                    .requestMatchers("/api/demo/**").permitAll()
                    
                    // All other requests require authentication
                    .anyRequest().authenticated()
            );
        
        // Add JWT filter before UsernamePasswordAuthenticationFilter
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
    
    /**
     * Password encoder bean - BCrypt
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    /**
     * Authentication manager bean
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}
