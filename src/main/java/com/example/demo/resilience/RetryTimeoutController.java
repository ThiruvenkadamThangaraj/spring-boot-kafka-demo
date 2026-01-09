package com.example.demo.resilience;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/resilience")
public class RetryTimeoutController {

    private final RetryTimeoutService service;

    public RetryTimeoutController(RetryTimeoutService service) {
        this.service = service;
    }

    @GetMapping("/timeout")
    public CompletableFuture<String> timeout() {
        return service.callWithTimeout();
    }

    @GetMapping("/retry")
    public CompletableFuture<String> retry() {
        return service.retry(() -> service.callWithTimeout(), 3);
    }
}
