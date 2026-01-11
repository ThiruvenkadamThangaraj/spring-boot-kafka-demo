package com.example.evidence.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/evidence")
public class EvidenceController {

    @GetMapping("/health")
    public String health() {
        return "Evidence Service is running";
    }

    // Add your evidence endpoints here
}
