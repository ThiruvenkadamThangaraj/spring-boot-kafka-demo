package com.example.evaluation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/evaluations")
@Tag(name = "Evaluation", description = "Evaluation management APIs")
@SecurityRequirement(name = "bearerAuth")
public class EvaluationController {

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check if the evaluation service is running")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Service is healthy")
    })
    public String health() {
        return "Evaluation Service is running";
    }

    @GetMapping
    @Operation(summary = "Get all evaluations", description = "Retrieve all evaluations from the database")
    public String getAllEvaluations() {
        return "List of evaluations";
    }


    @GetMapping("/{id}")
    @Operation(summary = "Get evaluation by ID", description = "Retrieve a specific evaluation by its ID")
    public String getEvaluationById(@PathVariable String id) {
        return "Evaluation with ID: " + id;
    }

    @PostMapping
    @Operation(summary = "Create evaluation", description = "Create a new evaluation")
    public String createEvaluation(@RequestBody String evaluation) {
        return "Evaluation created";
    }
}
