package com.example.userservice.service;

import com.example.userservice.model.AuditTrail;

public interface AuditTrailService {
    void logEvent(AuditTrail auditTrail);
}
