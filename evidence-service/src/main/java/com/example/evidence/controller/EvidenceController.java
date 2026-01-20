package com.example.evidence.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.*;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/evidence")
public class EvidenceController {

    @GetMapping("/health")
    public String health() {
        return "Evidence Service is running";
    }

    // Add your evidence endpoints here
}
