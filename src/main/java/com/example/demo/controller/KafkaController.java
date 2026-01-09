package com.example.demo.controller;

import com.example.demo.service.ProducerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class KafkaController {

    private final ProducerService producerService;

    @Value("${kafka.topic}")
    private String topic;

    public KafkaController(ProducerService producerService) {
        this.producerService = producerService;
    }

    // POST /api/kafka/publish?key=someKey
    @PostMapping("/api/kafka/publish")
    public ResponseEntity<?> publish(@RequestParam(required = false) String key,
                                     @RequestBody(required = false) Map<String, Object> body) {
        String message = (body == null) ? "" : body.toString();
        producerService.sendMessage(topic, key, message);
        return ResponseEntity.accepted().body(Map.of("status", "sent", "topic", topic));
    }
}
