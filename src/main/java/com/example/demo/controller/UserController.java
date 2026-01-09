package com.example.demo.controller;

import com.example.demo.model.User;
import com.example.demo.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin("*")
@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    // GET /api/users
    @GetMapping("/users")
    public List<User> getUsers() {
        return service.getUsers();
    }

    // GET /api/users/{id}
    @GetMapping("/users/{id}")
    public User getUser(@PathVariable Long id) {
        return service.getUser(id);
    }

    // GET /api/users/{id}/posts (mock posts)
    @GetMapping("/users/{id}/posts")
    public List<Map<String, String>> getUserPosts(@PathVariable Long id) {
        return List.of(
                Map.of("title", "Post 1 for user " + id),
                Map.of("title", "Post 2 for user " + id)
        );
    }

    // GET /api/settings/{id} (mock settings)
    @GetMapping("/settings/{id}")
    public Map<String, Object> getSettings(@PathVariable Long id) {
        return Map.of(
                "theme", "dark",
                "notifications", true,
                "userId", id
        );
    }
}
