package com.example.demo.starvation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/starvation")
public class StarvationController {

    private final StarvationService service;

    public StarvationController(StarvationService service) {
        this.service = service;
    }

    @GetMapping
    public String run() {
        for (int i = 1; i <= 10; i++) {
            service.outerTask(i);
        }
        return "Submitted starvation tasks";
    }
}
