package com.example.sampling.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sampling")
public class SamplingController {

    @GetMapping("/health")
    public String health() {
        return "Sampling Service is running";
    }

    // Add your sampling endpoints here
}
