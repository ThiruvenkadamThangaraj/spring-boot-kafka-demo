package com.example.common.controller;

import com.example.common.dto.ApiResponse;
import com.example.common.dto.UserCreateRequest;
import com.example.common.dto.UserDTO;
import com.example.common.service.AsyncUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for asynchronous user operations.
 * 
 * <p>This controller demonstrates international REST API standards:
 * <ul>
 *   <li>Uses standard HTTP methods (GET, POST, PUT, DELETE)</li>
 *   <li>Returns appropriate HTTP status codes</li>
 *   <li>Provides OpenAPI/Swagger documentation</li>
 *   <li>Implements async processing with CompletableFuture</li>
 *   <li>Separates I/O and CPU tasks for optimal performance</li>
 *   <li>Uses proper error handling and logging</li>
 * </ul>
 * 
 * <p>All endpoints return {@link CompletableFuture} for non-blocking async processing.
 * Spring automatically unwraps CompletableFuture in REST responses.
 * 
 * @author System
 * @version 1.0
 * @since 2026-01-13
 * @see CompletableFuture
 * @see AsyncUserService
 */
@RestController
@RequestMapping(value = "/api/async/users", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Async User Management", description = "Asynchronous user operations with I/O and CPU task separation")
public class AsyncUserController {
    
    private static final Logger logger = LoggerFactory.getLogger(AsyncUserController.class);

    @Autowired(required = false)
    private AsyncUserService asyncUserService;

    /**
     * Retrieves all users asynchronously.
     * 
     * <p>This endpoint demonstrates:
     * <ul>
     *   <li>I/O task: Database fetch</li>
     *   <li>CPU task: Entity to DTO conversion</li>
     * </ul>
     * 
     * @return CompletableFuture with response containing list of users
     */
    @Operation(
        summary = "Get all users",
        description = "Fetches all users from database (I/O task) and converts to DTOs (CPU task) asynchronously"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Users retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Internal server error"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "503",
            description = "Service unavailable - async service not configured"
        )
    })
    @GetMapping
    public CompletableFuture<ResponseEntity<ApiResponse<List<UserDTO>>>> getAllUsersAsync() {
        logger.info("Received request to fetch all users asynchronously");
        
        if (asyncUserService == null) {
            logger.error("AsyncUserService is not available");
            return CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Async User Service is not configured"))
            );
        }
        
        return asyncUserService.getAllUsersWithProcessing()
            .thenApply(users -> {
                logger.info("Successfully retrieved {} users", users.size());
                return ResponseEntity.ok(ApiResponse.success("Users retrieved successfully", users));
            })
            .exceptionally(ex -> {
                logger.error("Error retrieving users", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve users: " + getRootCause(ex).getMessage()));
            });
    }

    /**
     * GET user by ID - Combined IO + CPU
     */
    @GetMapping("/{id}")
    public CompletableFuture<ResponseEntity<ApiResponse<UserDTO>>> getUserByIdAsync(@PathVariable Long id) {
        return asyncUserService.getUserByIdWithProcessing(id)
            .thenApply(user -> 
                ResponseEntity.ok(ApiResponse.success("User retrieved successfully", user))
            )
            .exceptionally(ex -> 
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("User not found: " + ex.getMessage()))
            );
    }

    /**
     * GET user by username - Combined IO + CPU
     */
    @GetMapping("/username/{username}")
    public CompletableFuture<ResponseEntity<ApiResponse<UserDTO>>> getUserByUsernameAsync(@PathVariable String username) {
        return asyncUserService.getUserByUsernameWithProcessing(username)
            .thenApply(user -> 
                ResponseEntity.ok(ApiResponse.success("User retrieved successfully", user))
            )
            .exceptionally(ex -> 
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("User not found: " + ex.getMessage()))
            );
    }

    /**
     * POST create user - Multi-step async operation
     * 1. Parallel validation (IO)
     * 2. Entity creation (CPU)
     * 3. Database save (IO)
     * 4. Event publishing (IO - fire and forget)
     * 5. DTO conversion (CPU)
     */
    @PostMapping
    public CompletableFuture<ResponseEntity<ApiResponse<UserDTO>>> createUserAsync(@Valid @RequestBody UserCreateRequest request) {
        return asyncUserService.createUserAsync(request)
            .thenApply(user -> 
                ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("User created successfully", user))
            )
            .exceptionally(ex -> 
                ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Failed to create user: " + ex.getMessage()))
            );
    }

    /**
     * PUT update user - IO + CPU tasks
     */
    @PutMapping("/{id}")
    public CompletableFuture<ResponseEntity<ApiResponse<UserDTO>>> updateUserAsync(
            @PathVariable Long id,
            @Valid @RequestBody UserCreateRequest request) {
        return asyncUserService.updateUserAsync(id, request)
            .thenApply(user -> 
                ResponseEntity.ok(ApiResponse.success("User updated successfully", user))
            )
            .exceptionally(ex -> 
                ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Failed to update user: " + ex.getMessage()))
            );
    }

    /**
     * DELETE user - IO task
     */
    @DeleteMapping("/{id}")
    public CompletableFuture<ResponseEntity<ApiResponse<Void>>> deleteUserAsync(@PathVariable Long id) {
        return asyncUserService.deleteUserAsync(id)
            .<ResponseEntity<ApiResponse<Void>>>thenApply(v -> 
                ResponseEntity.ok(ApiResponse.success("User deleted successfully", null))
            )
            .exceptionally(ex -> 
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.<Void>error("Failed to delete user: " + ex.getMessage()))
            );
    }

    /**
     * POST batch processing - Parallel IO + Sequential CPU
     * Demonstrates parallel execution of multiple operations
     */
    @PostMapping("/batch")
    public CompletableFuture<ResponseEntity<ApiResponse<List<UserDTO>>>> processBatchAsync(
            @RequestBody List<Long> userIds) {
        return asyncUserService.processBatchUsers(userIds)
            .thenApply(users -> 
                ResponseEntity.ok(ApiResponse.success("Batch processed successfully", users))
            )
            .exceptionally(ex -> 
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Batch processing failed: " + ex.getMessage()))
            );
    }

    /**
     * GET with complex CPU processing
     * Demonstrates CPU-intensive operations on retrieved data
     */
    @GetMapping("/{id}/processed")
    public CompletableFuture<ResponseEntity<ApiResponse<UserDTO>>> getUserWithProcessingAsync(@PathVariable Long id) {
        return asyncUserService.processUserWithComplexLogic(id)
            .thenApply(user -> 
                ResponseEntity.ok(ApiResponse.success("User processed successfully", user))
            )
            .exceptionally(ex -> 
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Processing failed: " + ex.getMessage()))
            );
    }

    /**
     * Health check endpoint to verify async service availability.
     * 
     * @return response indicating service health status
     */
    @Operation(summary = "Health check", description = "Verifies async user service is available")
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> health() {
        logger.debug("Health check requested for Async User Service");
        
        if (asyncUserService == null) {
            logger.warn("Async User Service is not available");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("Async User Service not available"));
        }
        
        logger.debug("Async User Service is healthy");
        return ResponseEntity.ok(ApiResponse.success("Async User Service is running", "OK"));
    }
    
    /**
     * Utility method to extract root cause from exception chain.
     * 
     * @param throwable the exception to analyze
     * @return the root cause throwable
     */
    private Throwable getRootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
