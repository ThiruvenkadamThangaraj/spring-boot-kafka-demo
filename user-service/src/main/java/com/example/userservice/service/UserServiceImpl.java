package com.example.userservice.service;

import com.example.userservice.dto.UserInfoDTO;
import com.example.userservice.model.User;
import com.example.userservice.repository.UserRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.List;
import java.util.stream.Collectors;

import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;

@Service
public class UserServiceImpl implements UserService {


    @Autowired
    private UserRepository userRepository;

    @Autowired
    @Qualifier("userServiceExecutor")
    private Executor userServiceExecutor;

    @Override
    @TimeLimiter(name = "userInfoService", fallbackMethod = "fallbackUserInfo")
    @Retry(name = "userInfoService")
    @CircuitBreaker(name = "userInfoService", fallbackMethod = "fallbackUserInfo")
    public CompletableFuture<UserInfoDTO> getUserInfo(String username) {
        return CompletableFuture.supplyAsync(() -> {
            User user = userRepository.findByUsername(username);
            if (user == null) {
                throw new RuntimeException("User not found");
            }
            return new UserInfoDTO(user.getId(), user.getUsername(), user.getEmail());
        }, userServiceExecutor);
    }

    // Keyset pagination: fetch users after a given ID, limit results
    @TimeLimiter(name = "userInfoService", fallbackMethod = "fallbackUserList")
    @Retry(name = "userInfoService")
    @CircuitBreaker(name = "userInfoService", fallbackMethod = "fallbackUserList")
    public CompletableFuture<List<UserInfoDTO>> getUsersAfterId(String lastSeenId, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<User> users;
            if (lastSeenId == null || lastSeenId.isEmpty()) {
                users = userRepository.findAll(org.springframework.data.domain.PageRequest.of(0, limit)).getContent();
            } else {
                users = userRepository.findByIdGreaterThanOrderByIdAsc(lastSeenId, org.springframework.data.domain.PageRequest.of(0, limit));
            }
            return users.stream()
                .map(u -> new UserInfoDTO(u.getId(), u.getUsername(), u.getEmail()))
                .collect(Collectors.toList());
        }, userServiceExecutor);
    }

    public CompletableFuture<List<UserInfoDTO>> fallbackUserList(String lastSeenId, int limit, Throwable t) {
        return CompletableFuture.completedFuture(java.util.Collections.emptyList());
    }

    public CompletableFuture<UserInfoDTO> fallbackUserInfo(String username, Throwable t) {
        // Fallback logic: return a default or null object, or log the error
        return CompletableFuture.completedFuture(null);
    }
}
