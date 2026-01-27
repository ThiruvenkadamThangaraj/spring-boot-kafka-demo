package com.example.userservice.repository;

import com.example.userservice.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepository extends MongoRepository<User, String> {
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    User findByUsername(String username);

    // Keyset pagination: fetch users after a given ID (as String), ordered by ID ascending, with limit
    java.util.List<User> findByIdGreaterThanOrderByIdAsc(String id, org.springframework.data.domain.Pageable pageable);
}
