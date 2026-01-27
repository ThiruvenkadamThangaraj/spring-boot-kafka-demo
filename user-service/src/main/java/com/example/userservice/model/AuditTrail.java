package com.example.userservice.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "audit_trail")
@Data
public class AuditTrail {
    @Id
    private String id;
    private String username;
    private String action;
    private String details;
    private Instant timestamp;
    private String sourceIp;
    private String status;
}
