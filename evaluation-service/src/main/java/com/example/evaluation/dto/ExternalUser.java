package com.example.evaluation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for ReqRes User API (different from internal User entity)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalUser {
    private Long id;
    private String name;
    private String username;
    private String email;
    private String phone;
    private String website;
}
