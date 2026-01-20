package com.example.evaluation.controller;

import com.example.evaluation.dto.CombinedApiResponse;
import com.example.evaluation.dto.ExternalUser;
import com.example.evaluation.dto.Post;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * WebClient Demo Controller
 * Demonstrates both Synchronous and Asynchronous API calls using WebClient
 */
@RestController
@RequestMapping("/api/webclient-demo")
public class WebClientDemoController {

    private static final Logger log = LoggerFactory.getLogger(WebClientDemoController.class);

    @Autowired
    private WebClient jsonPlaceholderWebClient;

    // ========================================
    // SYNCHRONOUS EXAMPLES (Blocking)
    // ========================================

    /**
     * Example 1: Simple Synchronous GET Request
     * GET /api/webclient-demo/sync/post/1
     */
    @GetMapping("/sync/post/{id}")
    public ResponseEntity<Post> getSyncPost(@PathVariable Long id) {
        log.info("Fetching post {} synchronously", id);
        
        Instant start = Instant.now();
        
        Post post = jsonPlaceholderWebClient.get()
                .uri("/posts/{id}", id)
                .retrieve()
                .bodyToMono(Post.class)
                .block(); // BLOCKING call - waits for response
        
        Duration duration = Duration.between(start, Instant.now());
        log.info("Fetched post {} in {}ms", id, duration.toMillis());
        
        return ResponseEntity.ok(post);
    }

    /**
     * Example 2: Synchronous GET Request - List of Items
     * GET /api/webclient-demo/sync/posts
     */
    @GetMapping("/sync/posts")
    public ResponseEntity<List<Post>> getSyncAllPosts() {
        log.info("Fetching all posts synchronously");
        
        List<Post> posts = jsonPlaceholderWebClient.get()
                .uri("/posts")
                .retrieve()
                .bodyToFlux(Post.class)
                .collectList()
                .block(); // BLOCKING - waits for all items
        
        log.info("Fetched {} posts", posts != null ? posts.size() : 0);
        
        return ResponseEntity.ok(posts);
    }

    /**
     * Example 3: Synchronous POST Request
     * POST /api/webclient-demo/sync/post
     */
    @PostMapping("/sync/post")
    public ResponseEntity<Post> createSyncPost(@RequestBody Post post) {
        log.info("Creating post synchronously: {}", post.getTitle());
        
        Post createdPost = jsonPlaceholderWebClient.post()
                .uri("/posts")
                .bodyValue(post)
                .retrieve()
                .bodyToMono(Post.class)
                .block();
        
        log.info("Created post with ID: {}", createdPost != null ? createdPost.getId() : null);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(createdPost);
    }

    /**
     * Example 4: Synchronous with Error Handling
     * GET /api/webclient-demo/sync/post-safe/999999
     */
    @GetMapping("/sync/post-safe/{id}")
    public ResponseEntity<?> getSyncPostWithErrorHandling(@PathVariable Long id) {
        log.info("Fetching post {} with error handling", id);
        
        try {
            Post post = jsonPlaceholderWebClient.get()
                    .uri("/posts/{id}", id)
                    .retrieve()
                    .bodyToMono(Post.class)
                    .block();
            
            return ResponseEntity.ok(post);
            
        } catch (WebClientResponseException e) {
            log.error("Error fetching post {}: {}", id, e.getMessage());
            return ResponseEntity
                    .status(e.getStatusCode())
                    .body(Map.of(
                            "error", e.getStatusText(),
                            "message", "Post not found with ID: " + id
                    ));
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    // ========================================
    // ASYNCHRONOUS EXAMPLES (Non-Blocking)
    // ========================================

    /**
     * Example 5: Asynchronous GET Request (returns Mono)
     * GET /api/webclient-demo/async/post/1
     */
    @GetMapping("/async/post/{id}")
    public Mono<Post> getAsyncPost(@PathVariable Long id) {
        log.info("Fetching post {} asynchronously", id);
        
        return jsonPlaceholderWebClient.get()
                .uri("/posts/{id}", id)
                .retrieve()
                .bodyToMono(Post.class)
                .doOnSuccess(post -> log.info("Fetched post: {}", post.getId()))
                .doOnError(error -> log.error("Error fetching post: {}", error.getMessage()));
    }

    /**
     * Example 6: Asynchronous GET Request - List (returns Flux)
     * GET /api/webclient-demo/async/posts
     */
    @GetMapping("/async/posts")
    public Flux<Post> getAsyncAllPosts() {
        log.info("Fetching all posts asynchronously");
        
        return jsonPlaceholderWebClient.get()
                .uri("/posts")
                .retrieve()
                .bodyToFlux(Post.class)
                .doOnComplete(() -> log.info("Finished streaming all posts"))
                .doOnError(error -> log.error("Error streaming posts: {}", error.getMessage()));
    }

    /**
     * Example 7: Asynchronous POST Request
     * POST /api/webclient-demo/async/post
     */
    @PostMapping("/async/post")
    public Mono<ResponseEntity<Post>> createAsyncPost(@RequestBody Post post) {
        log.info("Creating post asynchronously: {}", post.getTitle());
        
        return jsonPlaceholderWebClient.post()
                .uri("/posts")
                .bodyValue(post)
                .retrieve()
                .bodyToMono(Post.class)
                .map(createdPost -> {
                    log.info("Created post with ID: {}", createdPost.getId());
                    return ResponseEntity.status(HttpStatus.CREATED).body(createdPost);
                })
                .doOnError(error -> log.error("Error creating post: {}", error.getMessage()));
    }

    /**
     * Example 8: Asynchronous with Timeout
     * GET /api/webclient-demo/async/post-timeout/1
     */
    @GetMapping("/async/post-timeout/{id}")
    public Mono<Post> getAsyncPostWithTimeout(@PathVariable Long id) {
        log.info("Fetching post {} with 5 second timeout", id);
        
        return jsonPlaceholderWebClient.get()
                .uri("/posts/{id}", id)
                .retrieve()
                .bodyToMono(Post.class)
                .timeout(Duration.ofSeconds(5))
                .doOnError(error -> log.error("Timeout or error: {}", error.getMessage()));
    }

    // ========================================
    // PARALLEL API CALLS (Async Advantage)
    // ========================================

    /**
     * Example 9: Sequential Synchronous Calls (SLOW)
     * GET /api/webclient-demo/sequential/user-posts/1
     */
    @GetMapping("/sequential/user-posts/{userId}")
    public ResponseEntity<CombinedApiResponse> getSequentialUserPosts(@PathVariable Long userId) {
        log.info("Fetching user and posts SEQUENTIALLY for user {}", userId);
        
        Instant start = Instant.now();
        
        // Call 1: Get user (waits ~500ms)
        ExternalUser user = jsonPlaceholderWebClient.get()
                .uri("/users/{id}", userId)
                .retrieve()
                .bodyToMono(ExternalUser.class)
                .block();
        
        // Call 2: Get posts (waits ~500ms)
        List<Post> posts = jsonPlaceholderWebClient.get()
                .uri("/posts?userId={userId}", userId)
                .retrieve()
                .bodyToFlux(Post.class)
                .collectList()
                .block();
        
        Duration duration = Duration.between(start, Instant.now());
        
        log.info("SEQUENTIAL execution took: {}ms", duration.toMillis());
        
        CombinedApiResponse response = new CombinedApiResponse(
                user,
                posts,
                duration.toMillis() + "ms",
                "SEQUENTIAL"
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Example 10: Parallel Asynchronous Calls (FAST)
     * GET /api/webclient-demo/parallel/user-posts/1
     */
    @GetMapping("/parallel/user-posts/{userId}")
    public Mono<CombinedApiResponse> getParallelUserPosts(@PathVariable Long userId) {
        log.info("Fetching user and posts IN PARALLEL for user {}", userId);
        
        Instant start = Instant.now();
        
        // Call 1: Get user (non-blocking)
        Mono<ExternalUser> userMono = jsonPlaceholderWebClient.get()
                .uri("/users/{id}", userId)
                .retrieve()
                .bodyToMono(ExternalUser.class);
        
        // Call 2: Get posts (non-blocking)
        Mono<List<Post>> postsMono = jsonPlaceholderWebClient.get()
                .uri("/posts?userId={userId}", userId)
                .retrieve()
                .bodyToFlux(Post.class)
                .collectList();
        
        // Combine both calls - executes in parallel!
        return Mono.zip(userMono, postsMono)
                .map(tuple -> {
                    Duration duration = Duration.between(start, Instant.now());
                    log.info("PARALLEL execution took: {}ms", duration.toMillis());
                    
                    return new CombinedApiResponse(
                            tuple.getT1(),  // User
                            tuple.getT2(),  // Posts
                            duration.toMillis() + "ms",
                            "PARALLEL"
                    );
                });
    }

    /**
     * Example 11: Health Check endpoint
     * GET /api/webclient-demo/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "message", "WebClient Demo API is running",
                "endpoints", "Check /api/webclient-demo/sync/* or /async/* or /parallel/*"
        ));
    }
}
