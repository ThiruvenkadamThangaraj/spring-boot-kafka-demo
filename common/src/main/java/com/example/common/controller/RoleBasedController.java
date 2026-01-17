package com.example.common.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Example Controller demonstrating role-based access control
 * Shows different endpoints for different user roles: ADMIN, OPERATOR, REVIEWER, USER
 * 
 * @author DevOps Team
 * @version 1.0
 * @since 2026-01-13
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Role-Based Access", description = "Endpoints demonstrating role-based authorization")
@SecurityRequirement(name = "Bearer Authentication")
public class RoleBasedController {
    
    // ========================================
    // ADMIN ONLY ENDPOINTS
    // ========================================
    
    @GetMapping("/admin/dashboard")
    @Operation(summary = "Admin Dashboard", description = "Only accessible by users with ADMIN role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> adminDashboard() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Welcome to Admin Dashboard");
        response.put("username", auth.getName());
        response.put("roles", auth.getAuthorities());
        response.put("capabilities", new String[]{
            "Manage all users",
            "Configure system settings",
            "View all reports",
            "Perform all operations"
        });
        
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/admin/users/{id}")
    @Operation(summary = "Delete User", description = "Delete user - ADMIN only")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of(
            "message", "User deleted successfully",
            "userId", id,
            "deletedBy", SecurityContextHolder.getContext().getAuthentication().getName()
        ));
    }
    
    // ========================================
    // OPERATOR ENDPOINTS (OPERATOR or ADMIN)
    // ========================================
    
    @GetMapping("/operator/dashboard")
    @Operation(summary = "Operator Dashboard", description = "Accessible by OPERATOR and ADMIN roles")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<?> operatorDashboard() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Welcome to Operator Dashboard");
        response.put("username", auth.getName());
        response.put("roles", auth.getAuthorities());
        response.put("capabilities", new String[]{
            "Process evaluations",
            "Manage sampling operations",
            "Update evidence records",
            "Review and approve items"
        });
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/operator/evaluation/{id}/process")
    @Operation(summary = "Process Evaluation", description = "Process evaluation - OPERATOR or ADMIN")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<?> processEvaluation(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of(
            "message", "Evaluation processed successfully",
            "evaluationId", id,
            "processedBy", SecurityContextHolder.getContext().getAuthentication().getName(),
            "status", "PROCESSED"
        ));
    }
    
    @PutMapping("/operator/sampling/{id}/approve")
    @Operation(summary = "Approve Sampling", description = "Approve sampling request - OPERATOR or ADMIN")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<?> approveSampling(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of(
            "message", "Sampling approved",
            "samplingId", id,
            "approvedBy", SecurityContextHolder.getContext().getAuthentication().getName()
        ));
    }
    
    // ========================================
    // REVIEWER ENDPOINTS (REVIEWER, OPERATOR, or ADMIN)
    // ========================================
    
    @GetMapping("/reviewer/dashboard")
    @Operation(summary = "Reviewer Dashboard", description = "Accessible by REVIEWER, OPERATOR, and ADMIN roles")
    @PreAuthorize("hasAnyRole('REVIEWER', 'OPERATOR', 'ADMIN')")
    public ResponseEntity<?> reviewerDashboard() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Welcome to Reviewer Dashboard");
        response.put("username", auth.getName());
        response.put("roles", auth.getAuthorities());
        response.put("capabilities", new String[]{
            "Review submissions",
            "Add comments",
            "View reports",
            "Track review status"
        });
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/reviewer/items/{id}/review")
    @Operation(summary = "Review Item", description = "Review an item - REVIEWER, OPERATOR, or ADMIN")
    @PreAuthorize("hasAnyRole('REVIEWER', 'OPERATOR', 'ADMIN')")
    public ResponseEntity<?> reviewItem(@PathVariable Long id, @RequestBody ReviewRequest request) {
        return ResponseEntity.ok(Map.of(
            "message", "Item reviewed successfully",
            "itemId", id,
            "reviewedBy", SecurityContextHolder.getContext().getAuthentication().getName(),
            "status", request.getStatus(),
            "comments", request.getComments()
        ));
    }
    
    @GetMapping("/reviewer/pending-reviews")
    @Operation(summary = "Get Pending Reviews", description = "List all pending reviews - REVIEWER, OPERATOR, or ADMIN")
    @PreAuthorize("hasAnyRole('REVIEWER', 'OPERATOR', 'ADMIN')")
    public ResponseEntity<?> getPendingReviews() {
        return ResponseEntity.ok(Map.of(
            "message", "Pending reviews retrieved",
            "count", 5,
            "items", new Object[]{
                Map.of("id", 1, "type", "Evaluation", "status", "PENDING"),
                Map.of("id", 2, "type", "Sampling", "status", "PENDING"),
                Map.of("id", 3, "type", "Evidence", "status", "PENDING")
            }
        ));
    }
    
    // ========================================
    // USER ENDPOINTS (All authenticated users)
    // ========================================
    
    @GetMapping("/users/profile")
    @Operation(summary = "User Profile", description = "Accessible by all authenticated users")
    @PreAuthorize("hasAnyRole('USER', 'REVIEWER', 'OPERATOR', 'ADMIN')")
    public ResponseEntity<?> getUserProfile() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        Map<String, Object> response = new HashMap<>();
        response.put("username", auth.getName());
        response.put("roles", auth.getAuthorities());
        response.put("authenticated", true);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/users/my-items")
    @Operation(summary = "My Items", description = "Get current user's items - All authenticated users")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMyItems() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        return ResponseEntity.ok(Map.of(
            "message", "Your items",
            "username", auth.getName(),
            "items", new Object[]{
                Map.of("id", 1, "name", "Item 1", "status", "ACTIVE"),
                Map.of("id", 2, "name", "Item 2", "status", "PENDING")
            }
        ));
    }
    
    // ========================================
    // COMBINED AUTHORIZATION EXAMPLES
    // ========================================
    
    @GetMapping("/items/{id}")
    @Operation(summary = "Get Item", description = "Anyone can view, but only owner or ADMIN can see sensitive data")
    public ResponseEntity<?> getItem(@PathVariable Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        
        Map<String, Object> response = new HashMap<>();
        response.put("id", id);
        response.put("name", "Sample Item");
        response.put("status", "ACTIVE");
        
        // Show sensitive data only to ADMIN
        if (isAdmin) {
            response.put("internalNotes", "Confidential information");
            response.put("auditLog", "Last modified by admin");
        }
        
        return ResponseEntity.ok(response);
    }
    
    @PutMapping("/items/{id}")
    @Operation(summary = "Update Item", description = "OPERATOR and ADMIN can update items")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<?> updateItem(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        return ResponseEntity.ok(Map.of(
            "message", "Item updated successfully",
            "itemId", id,
            "updatedBy", SecurityContextHolder.getContext().getAuthentication().getName()
        ));
    }
    
    // ========================================
    // HELPER CLASSES
    // ========================================
    
    public static class ReviewRequest {
        private String status;
        private String comments;
        
        public String getStatus() {
            return status;
        }
        
        public void setStatus(String status) {
            this.status = status;
        }
        
        public String getComments() {
            return comments;
        }
        
        public void setComments(String comments) {
            this.comments = comments;
        }
    }
}
