package com.example.userservice.service;

import com.example.userservice.model.AuditTrail;
import com.example.userservice.repository.AuditTrailRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AuditTrailServiceImpl implements AuditTrailService {
    @Autowired
    private AuditTrailRepository auditTrailRepository;

    @Override
    @Async
    public void logEvent(AuditTrail auditTrail) {
        auditTrailRepository.save(auditTrail);
    }
}
