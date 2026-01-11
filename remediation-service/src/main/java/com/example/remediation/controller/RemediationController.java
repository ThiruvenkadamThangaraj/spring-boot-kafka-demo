package com.example.remediation.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/remediation")
public class RemediationController {

    @GetMapping("/health")
    public String health() {
        return "Remediation Service is running";
    }

    // Add your remediation endpoints here
}
