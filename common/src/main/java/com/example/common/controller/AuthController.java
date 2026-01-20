package com.example.common.controller;

import com.example.common.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Authentication Controller
 * Handles user login and JWT token generation
 * 
 * @author DevOps Team
 * @version 1.0
 * @since 2026-01-13
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "JWT Authentication APIs")
public class AuthController {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    
    @Autowired
    private JwtTokenProvider tokenProvider;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    /**
     * Login endpoint - generates JWT token
     * 
     * POST /api/auth/login
     * Body: {"username": "admin", "password": "password123"}
     */
    @PostMapping(value = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "User login", description = "Authenticate user and generate JWT token")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Login successful"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest) {
        logger.info("Login attempt for user: {}", loginRequest.getUsername());
        
        try {
            // For demo purposes - hardcoded users with roles (replace with database lookup in production)
            Map<String, UserCredentials> users = new HashMap<>();
            users.put("admin", new UserCredentials(
                passwordEncoder.encode("admin123"),
                Arrays.asList("ROLE_ADMIN", "ROLE_OPERATOR", "ROLE_REVIEWER", "ROLE_USER")
            ));
            users.put("operator", new UserCredentials(
                passwordEncoder.encode("operator123"),
                Arrays.asList("ROLE_OPERATOR", "ROLE_REVIEWER", "ROLE_USER")
            ));
            users.put("reviewer", new UserCredentials(
                passwordEncoder.encode("reviewer123"),
                Arrays.asList("ROLE_REVIEWER", "ROLE_USER")
            ));
            users.put("user", new UserCredentials(
                passwordEncoder.encode("user123"),
                Arrays.asList("ROLE_USER")
            ));
            users.put("john.doe", new UserCredentials(
                passwordEncoder.encode("password123"),
                Arrays.asList("ROLE_USER")
            ));
            
            // Validate credentials
            UserCredentials userCreds = users.get(loginRequest.getUsername());
            if (userCreds == null || !passwordEncoder.matches(loginRequest.getPassword(), userCreds.password)) {
                logger.warn("Invalid credentials for user: {}", loginRequest.getUsername());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid username or password"));
            }
            
            // Get user roles
            List<String> roles = userCreds.roles;
            
            // Generate JWT token
            String jwt = tokenProvider.generateToken(loginRequest.getUsername(), roles);
            
            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("token", jwt);
            response.put("type", "Bearer");
            response.put("username", loginRequest.getUsername());
            response.put("roles", roles);
            response.put("expiresIn", 86400000); // 24 hours in milliseconds
            
            logger.info("User {} logged in successfully", loginRequest.getUsername());
            
            return ResponseEntity.ok(response);
            
        } catch (AuthenticationException e) {
            logger.error("Authentication failed for user: {}", loginRequest.getUsername(), e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication failed"));
        }
    }
    
    /**
     * Validate token endpoint
     * 
     * GET /api/auth/validate
     * Header: Authorization: Bearer <token>
     */
    @GetMapping(value = "/validate", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Validate JWT token", description = "Check if JWT token is valid")
    public ResponseEntity<?> validateToken(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            
            if (tokenProvider.validateToken(token)) {
                String username = tokenProvider.getUsernameFromToken(token);
                List<String> roles = tokenProvider.getRolesFromToken(token);
                
                Map<String, Object> response = new HashMap<>();
                response.put("valid", true);
                response.put("username", username);
                response.put("roles", roles);
                
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("valid", false, "error", "Invalid token"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("valid", false, "error", e.getMessage()));
        }
    }
    
    /**
     * Get current user info
     * 
     * GET /api/auth/me
     * Header: Authorization: Bearer <token>
     */
    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get current user", description = "Get authenticated user information")
    public ResponseEntity<?> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("username", authentication.getName());
        response.put("roles", authentication.getAuthorities());
        response.put("authenticated", true);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Login request DTO
     */
    public static class LoginRequest {
        private String username;
        private String password;
        
        public String getUsername() {
            return username;
        }
        
        public void setUsername(String username) {
            this.username = username;
        }
        
        public String getPassword() {
            return password;
        }
        
        public void setPassword(String password) {
            this.password = password;
        }
    }
    
    /**
     * Internal class to store user credentials and roles
     */
    private static class UserCredentials {
        final String password;
        final List<String> roles;
        
        UserCredentials(String password, List<String> roles) {
            this.password = password;
            this.roles = roles;
        }
    }
}
