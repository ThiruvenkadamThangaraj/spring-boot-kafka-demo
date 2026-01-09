package com.example.demo.webclient;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class WebClientService {

    private final WebClient client = WebClient.create();

    public Mono<String> callApi(int id) {
        return client.get()
                .uri("https://httpbin.org/delay/3")
                .retrieve()
                .bodyToMono(String.class)
                .map(res -> "Response " + id);
    }
}
