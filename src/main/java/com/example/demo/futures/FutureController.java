package com.example.demo.futures;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/futures")
public class FutureController {

    private final FutureService service;

    public FutureController(FutureService service) {
        this.service = service;
    }

    @GetMapping
    public String run() {
        service.runFutures();
        return "CompletableFuture tasks submitted";
    }
}
