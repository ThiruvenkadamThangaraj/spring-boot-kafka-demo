package com.example.jira.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/jira")
public class JiraController {

    @GetMapping("/health")
    public String health() {
        return "Jira Service is running";
    }

    // Add your Jira integration endpoints here
}
