package com.example.evaluation.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/evaluations")
public class EvaluationController {

    @GetMapping("/health")
    public String health() {
        return "Evaluation Service is running";
    }

    // Add your evaluation endpoints here
}
