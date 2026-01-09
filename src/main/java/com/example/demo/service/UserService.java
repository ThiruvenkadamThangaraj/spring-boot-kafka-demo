package com.example.demo.service;

import com.example.demo.model.User;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.reactor.retry.RetryOperator;

import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadRegistry;

import com.example.demo.repository.UserRepository;

import java.util.List;

@Service
public class UserService {

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final ThreadPoolBulkhead bulkhead;
    private final UserRepository userRepository;

    public UserService(WebClient.Builder builder,
                       CircuitBreakerRegistry cbRegistry,
                       RetryRegistry retryRegistry,
                       ThreadPoolBulkheadRegistry bulkheadRegistry,
                       UserRepository userRepository) {

        this.webClient = builder.baseUrl("http://localhost:8080").build();
        this.circuitBreaker = cbRegistry.circuitBreaker("userServiceCB");
        this.retry = retryRegistry.retry("userServiceRetry");
        this.bulkhead = bulkheadRegistry.bulkhead("userServiceBulkhead");
        this.userRepository = userRepository;
    }

    public Mono<User> getUserFromDB(Long id) {
        return Mono.fromCallable(() -> {
            com.example.demo.entity.User e = userRepository.findById(id).orElseThrow();
            return new User(e.getId(), e.getName(), true);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<User> getUserFromRemote(Long id) {
        return webClient.get()
                .uri("/external/user/{id}", id)
                .retrieve()
                .bodyToMono(User.class)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry));
    }

    private final List<User> users = List.of(
            new User(1L, "John Doe", true),
            new User(2L, "Jane Smith", false),
            new User(3L, "Michael Johnson", true)
    );

    public List<User> getUsers() {
        return users;
    }

    public User getUser(Long id) {
        return users.stream()
                .filter(u -> u.id().equals(id))
                .findFirst()
                .orElse(null);
    }
}
