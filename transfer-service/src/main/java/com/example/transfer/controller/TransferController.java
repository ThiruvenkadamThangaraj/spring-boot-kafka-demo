package com.example.transfer.controller;

import com.example.transfer.service.CoordinatorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/transfer")
public class TransferController {
    
    @Autowired
    private CoordinatorService coordinatorService;
    
    /**
     * Manual trigger endpoint for testing
     * POST http://localhost:8080/api/transfer/start
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, String>> startTransfer() {
        // Run in separate thread to avoid blocking HTTP request
        new Thread(() -> coordinatorService.manualTrigger(), "Manual-Transfer-Trigger").start();
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "started");
        response.put("message", "Transfer initiated. Check logs or /api/monitor/progress for status");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Transfer Coordinator");
        return ResponseEntity.ok(response);
    }
}
