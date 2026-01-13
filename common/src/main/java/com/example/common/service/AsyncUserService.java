package com.example.common.service;

import com.example.common.dto.UserCreateRequest;
import com.example.common.dto.UserDTO;
import com.example.common.entity.User;
import com.example.common.event.UserCreatedEvent;
import com.example.common.repository.UserRepository;
import com.example.common.util.EntityMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Asynchronous User Service with CompletableFuture support.
 * 
 * <p>This service follows international coding standards by:
 * <ul>
 *   <li>Separating I/O tasks (database operations) from CPU tasks (data processing)</li>
 *   <li>Using dedicated thread pools for optimal resource utilization</li>
 *   <li>Providing comprehensive logging for observability</li>
 *   <li>Implementing proper exception handling and error propagation</li>
 *   <li>Following reactive programming patterns with CompletableFuture</li>
 * </ul>
 * 
 * @author System
 * @version 1.0
 * @since 2026-01-13
 * @see CompletableFuture
 * @see Transactional
 */
@Service
public class AsyncUserService {
    
    private static final Logger logger = LoggerFactory.getLogger(AsyncUserService.class);

    @Autowired(required = false)
    private UserRepository userRepository;

    @Autowired(required = false)
    private EventPublisher eventPublisher;

    @Autowired
    @Qualifier("ioTaskExecutor")
    private Executor ioExecutor;

    @Autowired
    @Qualifier("cpuTaskExecutor")
    private Executor cpuExecutor;

    @Value("${spring.application.name}")
    private String serviceName;

    /**
     * Fetches all users from the database asynchronously.
     * 
     * <p>This is an I/O-bound operation that runs on the dedicated I/O thread pool.
     * The operation is non-blocking and returns immediately with a CompletableFuture.
     * 
     * @return CompletableFuture containing list of all users
     * @throws CompletionException if database access fails
     */
    public CompletableFuture<List<User>> getAllUsersAsync() {
        logger.debug("Initiating async fetch of all users");
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.trace("Executing database query to fetch all users");
                validateRepositoryAvailable();
                List<User> users = userRepository.findAll();
                logger.debug("Successfully fetched {} users from database", users.size());
                return users;
            } catch (Exception ex) {
                logger.error("Error fetching users from database", ex);
                throw new CompletionException("Failed to fetch users from database", ex);
            }
        }, ioExecutor);
    }

    /**
     * Processes a list of users and converts them to DTOs.
     * 
     * <p>This is a CPU-bound operation that runs on the dedicated CPU thread pool.
     * The conversion involves data mapping and transformation which is computationally intensive.
     * 
     * @param users list of user entities to process
     * @return CompletableFuture containing list of user DTOs
     * @throws IllegalArgumentException if users list is null
     */
    public CompletableFuture<List<UserDTO>> processUsersToDTO(List<User> users) {
        Objects.requireNonNull(users, "Users list cannot be null");
        logger.debug("Initiating async processing of {} users to DTO", users.size());
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.trace("Converting {} user entities to DTOs", users.size());
                List<UserDTO> dtos = users.stream()
                        .map(EntityMapper::toDTO)
                        .collect(Collectors.toList());
                logger.debug("Successfully converted {} users to DTOs", dtos.size());
                return dtos;
            } catch (Exception ex) {
                logger.error("Error processing users to DTO", ex);
                throw new CompletionException("Failed to convert users to DTO", ex);
            }
        }, cpuExecutor);
    }

    /**
     * Fetches all users and processes them to DTOs in a pipeline.
     * 
     * <p>This method demonstrates proper task composition:
     * <ol>
     *   <li>Fetch users from database (I/O task)</li>
     *   <li>Convert to DTOs (CPU task)</li>
     * </ol>
     * 
     * @return CompletableFuture containing processed user DTOs
     */
    public CompletableFuture<List<UserDTO>> getAllUsersWithProcessing() {
        logger.debug("Initiating complete user fetch and processing pipeline");
        return getAllUsersAsync()
                .thenComposeAsync(users -> processUsersToDTO(users), cpuExecutor);
    }

    /**
     * IO Task: Get user by ID
     */
    public CompletableFuture<User> getUserByIdAsync(Long id) {
        return CompletableFuture.supplyAsync(() -> {
            return userRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        }, ioExecutor);
    }

    /**
     * Combined: Get user by ID and convert to DTO
     */
    public CompletableFuture<UserDTO> getUserByIdWithProcessing(Long id) {
        return getUserByIdAsync(id)
                .thenApplyAsync(EntityMapper::toDTO, cpuExecutor);
    }

    /**
     * IO Task: Get user by username
     */
    public CompletableFuture<User> getUserByUsernameAsync(String username) {
        return CompletableFuture.supplyAsync(() -> {
            return userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found with username: " + username));
        }, ioExecutor);
    }

    /**
     * Combined: Get user by username and convert to DTO
     */
    public CompletableFuture<UserDTO> getUserByUsernameWithProcessing(String username) {
        return getUserByUsernameAsync(username)
                .thenApplyAsync(EntityMapper::toDTO, cpuExecutor);
    }

    /**
     * Complex operation: Create user with multiple async steps
     * 1. IO: Validation checks (parallel)
     * 2. CPU: Entity creation
     * 3. IO: Save to database
     * 4. IO: Publish Kafka event (fire and forget)
     * 5. CPU: Convert to DTO
     */
    @Transactional
    public CompletableFuture<UserDTO> createUserAsync(UserCreateRequest request) {
        // Step 1: Parallel validation (IO tasks)
        CompletableFuture<Boolean> usernameCheck = CompletableFuture.supplyAsync(() -> 
            userRepository.existsByUsername(request.getUsername()), ioExecutor);
        
        CompletableFuture<Boolean> emailCheck = CompletableFuture.supplyAsync(() -> 
            userRepository.existsByEmail(request.getEmail()), ioExecutor);

        // Wait for both validations
        return CompletableFuture.allOf(usernameCheck, emailCheck)
            .thenApplyAsync(v -> {
                if (usernameCheck.join()) {
                    throw new RuntimeException("Username already exists: " + request.getUsername());
                }
                if (emailCheck.join()) {
                    throw new RuntimeException("Email already exists: " + request.getEmail());
                }
                return request;
            }, cpuExecutor)
            // Step 2: CPU - Create entity
            .thenApplyAsync(req -> {
                User user = EntityMapper.toEntity(req);
                user.setIsActive(true);
                return user;
            }, cpuExecutor)
            // Step 3: IO - Save to database
            .thenComposeAsync(user -> 
                CompletableFuture.supplyAsync(() -> userRepository.save(user), ioExecutor)
            )
            // Step 4: IO - Publish event (async, non-blocking)
            .thenApplyAsync(savedUser -> {
                publishUserCreatedEventAsync(savedUser);
                return savedUser;
            }, ioExecutor)
            // Step 5: CPU - Convert to DTO
            .thenApplyAsync(EntityMapper::toDTO, cpuExecutor);
    }

    /**
     * Fire-and-forget async event publishing
     */
    private void publishUserCreatedEventAsync(User user) {
        CompletableFuture.runAsync(() -> {
            UserCreatedEvent event = new UserCreatedEvent(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getDepartment(),
                user.getSalary(),
                serviceName,
                user.getCreatedAt()
            );
            eventPublisher.publishUserCreatedEvent(event);
        }, ioExecutor);
    }

    /**
     * Update user with async processing
     */
    @Transactional
    public CompletableFuture<UserDTO> updateUserAsync(Long id, UserCreateRequest request) {
        return getUserByIdAsync(id)
            // CPU: Update user fields
            .thenApplyAsync(user -> {
                user.setFirstName(request.getFirstName());
                user.setLastName(request.getLastName());
                user.setPhoneNumber(request.getPhoneNumber());
                user.setDepartment(request.getDepartment());
                user.setSalary(request.getSalary());
                return user;
            }, cpuExecutor)
            // IO: Save to database
            .thenComposeAsync(user -> 
                CompletableFuture.supplyAsync(() -> userRepository.save(user), ioExecutor)
            )
            // CPU: Convert to DTO
            .thenApplyAsync(EntityMapper::toDTO, cpuExecutor);
    }

    /**
     * Delete user async
     */
    @Transactional
    public CompletableFuture<Void> deleteUserAsync(Long id) {
        return CompletableFuture.runAsync(() -> {
            if (!userRepository.existsById(id)) {
                throw new RuntimeException("User not found with id: " + id);
            }
            userRepository.deleteById(id);
        }, ioExecutor);
    }

    /**
     * CPU-intensive task example: Process user data with complex calculations
     */
    public CompletableFuture<UserDTO> processUserWithComplexLogic(Long id) {
        return getUserByIdAsync(id)
            .thenApplyAsync(user -> {
                // Simulate CPU-intensive processing
                UserDTO dto = EntityMapper.toDTO(user);
                
                // Complex calculations
                double salaryBonus = calculateBonus(user);
                dto.setSalary(user.getSalary() + salaryBonus);
                
                return dto;
            }, cpuExecutor);
    }

    /**
     * Batch processing with parallel execution
     */
    public CompletableFuture<List<UserDTO>> processBatchUsers(List<Long> userIds) {
        // Fetch all users in parallel (IO)
        List<CompletableFuture<User>> futures = userIds.stream()
            .map(this::getUserByIdAsync)
            .collect(Collectors.toList());

        // Wait for all to complete
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApplyAsync(v -> {
                // Process all users (CPU)
                return futures.stream()
                    .map(CompletableFuture::join)
                    .map(EntityMapper::toDTO)
                    .collect(Collectors.toList());
            }, cpuExecutor);
    }

    private double calculateBonus(User user) {
        // Simulate complex CPU calculation
        return user.getSalary() * 0.1;
    }
    
    /**
     * Validates that the user repository is available.
     * 
     * @throws IllegalStateException if repository is not available
     */
    private void validateRepositoryAvailable() {
        if (userRepository == null) {
            throw new IllegalStateException("UserRepository is not available. " +
                "Ensure database configuration is properly set up.");
        }
    }
    
    /**
     * Validates user creation request.
     * 
     * @param request the user creation request to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateUserRequest(UserCreateRequest request) {
        Objects.requireNonNull(request, "User creation request cannot be null");
        
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required and cannot be empty");
        }
        
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required and cannot be empty");
        }
        
        if (!request.getEmail().contains("@")) {
            throw new IllegalArgumentException("Email must be in valid format");
        }
    }
}
