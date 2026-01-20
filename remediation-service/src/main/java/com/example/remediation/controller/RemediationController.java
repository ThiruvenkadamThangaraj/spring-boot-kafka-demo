package com.example.remediation.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.*;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/remediation")
public class RemediationController {

    @GetMapping("/health")
    public String health() {
        return "Remediation Service is running";
    }

    // Add your remediation endpoints here
}
