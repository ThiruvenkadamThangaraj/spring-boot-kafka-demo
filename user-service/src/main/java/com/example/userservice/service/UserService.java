package com.example.userservice.service;

import com.example.userservice.dto.UserInfoDTO;

import java.util.concurrent.CompletableFuture;
import java.util.List;

public interface UserService {
    CompletableFuture<UserInfoDTO> getUserInfo(String username);

    // Keyset pagination: fetch users after a given ID, limit results
    CompletableFuture<List<UserInfoDTO>> getUsersAfterId(String lastSeenId, int limit);
}