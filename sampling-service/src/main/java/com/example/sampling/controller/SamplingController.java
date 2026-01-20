package com.example.sampling.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.*;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/sampling")
public class SamplingController {

    @GetMapping("/health")
    public String health() {
        return "Sampling Service is running";
    }

    // Add your sampling endpoints here
}
