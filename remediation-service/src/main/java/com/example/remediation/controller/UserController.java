package com.example.remediation.controller;

import com.example.common.dto.ApiResponse;
import com.example.common.dto.UserCreateRequest;
import com.example.common.dto.UserDTO;
import com.example.remediation.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserDTO>>> getAllUsers() {
        List<UserDTO> users = userService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.success("Users retrieved successfully", users));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDTO>> getUserById(@PathVariable Long id) {
        UserDTO user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @GetMapping("/username/{username}")
    public ResponseEntity<ApiResponse<UserDTO>> getUserByUsername(@PathVariable String username) {
        UserDTO user = userService.getUserByUsername(username);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserDTO>> createUser(@Valid @RequestBody UserCreateRequest request) {
        UserDTO user = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User created successfully", user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDTO>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserCreateRequest request) {
        UserDTO user = userService.updateUser(id, request);
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success("User deleted successfully", null));
    }
}
