package com.example.evaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Combined response for parallel API calls demo
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CombinedApiResponse {
    private ExternalUser user;
    private List<Post> posts;
    private String executionTime;
    private String mode; // "SEQUENTIAL" or "PARALLEL"
}
