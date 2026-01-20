package com.example.evaluation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for JSONPlaceholder Post API
 * API: https://jsonplaceholder.typicode.com/posts
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Post {
    private Long id;
    private Long userId;
    private String title;
    private String body;
}
