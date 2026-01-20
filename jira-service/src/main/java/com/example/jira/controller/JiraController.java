package com.example.jira.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.*;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/jira")
public class JiraController {

    @GetMapping("/health")
    public String health() {
        return "Jira Service is running";
    }

    // Add your Jira integration endpoints here
}
