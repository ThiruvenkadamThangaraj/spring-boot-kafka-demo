package com.example.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCreatedEvent {
    private Long userId;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String department;
    private Double salary;
    private String serviceName;
    private LocalDateTime createdAt;
}
