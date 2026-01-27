package com.example.userservice.repository;

import com.example.userservice.model.AuditTrail;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditTrailRepository extends MongoRepository<AuditTrail, String> {
}
