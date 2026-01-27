   
package com.example.userservice.controller;

import com.example.userservice.model.User;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.dto.UserInfoDTO;
import com.example.userservice.service.UserService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;

@CrossOrigin(origins = "http://localhost:8088", allowCredentials = "true")
@RestController
@RequestMapping("/api")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody User user) {
        if (userRepository.existsByEmail(user.getEmail()) || userRepository.existsByUsername(user.getUsername())) {
            return ResponseEntity.badRequest().body("User already exists");
        }
        // TODO: Hash password before saving!
        userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body("User created");
    }

    @Operation(summary = "Get all users", description = "Returns a paginated list of all users (without passwords). Use lastSeenId for keyset pagination.")
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(@RequestParam(required = false) String lastSeenId, @RequestParam(defaultValue = "20") int limit) {
        try {
            if (lastSeenId == null) lastSeenId = "";
            CompletableFuture<java.util.List<UserInfoDTO>> future = userService.getUsersAfterId(lastSeenId, limit);
            java.util.List<UserInfoDTO> users = future.get();
            return ResponseEntity.ok(users);
        } catch (InterruptedException | ExecutionException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error retrieving users");
        }
    }
}
