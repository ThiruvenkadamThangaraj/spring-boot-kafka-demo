package com.example.demo.webclient;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/webclient")
public class WebClientController {

    private final WebClientService service;

    public WebClientController(WebClientService service) {
        this.service = service;
    }

    @GetMapping
    public Flux<String> run() {
        return Flux.range(1, 10)
                .flatMap(service::callApi);
    }
}
