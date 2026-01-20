# WebClient Demo API - Testing Guide

## 🚀 Overview
This Spring Boot controller demonstrates **both Synchronous and Asynchronous** API calls using **WebClient**.

**Public APIs Used:**
- JSONPlaceholder: https://jsonplaceholder.typicode.com (Posts, Users)
- ReqRes: https://reqres.in (Alternative)

---

## 📦 Dependencies Required

Add to your `pom.xml`:

```xml
<!-- Spring WebFlux (includes WebClient) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>

<!-- Reactor Netty (HTTP client) -->
<dependency>
    <groupId>io.projectreactor.netty</groupId>
    <artifactId>reactor-netty</artifactId>
</dependency>

<!-- Lombok (optional, for cleaner code) -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```

---

## 🏃 Quick Start

1. **Run the application:**
   ```bash
   mvn spring-boot:run
   ```

2. **Base URL:**
   ```
   http://localhost:8080/api/webclient-demo
   ```

3. **Health Check:**
   ```bash
   curl http://localhost:8080/api/webclient-demo/health
   ```

---

## 🔵 SYNCHRONOUS Examples (Blocking)

### 1. Get Single Post (Sync)
```bash
# Windows PowerShell
Invoke-WebRequest -Uri "http://localhost:8080/api/webclient-demo/sync/post/1" | Select-Object -Expand Content

# Linux/Mac
curl http://localhost:8080/api/webclient-demo/sync/post/1
```

**Response:**
```json
{
  "id": 1,
  "userId": 1,
  "title": "sunt aut facere repellat provident",
  "body": "quia et suscipit..."
}
```

---

### 2. Get All Posts (Sync)
```bash
curl http://localhost:8080/api/webclient-demo/sync/posts
```

**Response:** Array of 100 posts

---

### 3. Create Post (Sync)
```bash
# PowerShell
$body = @{
    userId = 1
    title = "My New Post"
    body = "This is the post content"
} | ConvertTo-Json

Invoke-WebRequest -Uri "http://localhost:8080/api/webclient-demo/sync/post" `
    -Method POST `
    -ContentType "application/json" `
    -Body $body

# Linux/Mac
curl -X POST http://localhost:8080/api/webclient-demo/sync/post \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"title":"My New Post","body":"Post content"}'
```

---

### 4. Update Post (Sync)
```bash
# PowerShell
$body = @{
    userId = 1
    title = "Updated Title"
    body = "Updated content"
} | ConvertTo-Json

Invoke-WebRequest -Uri "http://localhost:8080/api/webclient-demo/sync/post/1" `
    -Method PUT `
    -ContentType "application/json" `
    -Body $body

# Linux/Mac
curl -X PUT http://localhost:8080/api/webclient-demo/sync/post/1 \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"title":"Updated","body":"Updated content"}'
```

---

### 5. Delete Post (Sync)
```bash
curl -X DELETE http://localhost:8080/api/webclient-demo/sync/post/1
```

---

### 6. Get Post with Error Handling (Sync)
```bash
# Valid ID
curl http://localhost:8080/api/webclient-demo/sync/post-safe/1

# Invalid ID (returns error)
curl http://localhost:8080/api/webclient-demo/sync/post-safe/999999
```

---

## 🟢 ASYNCHRONOUS Examples (Non-Blocking)

### 7. Get Single Post (Async)
```bash
curl http://localhost:8080/api/webclient-demo/async/post/1
```

**Difference:** Returns immediately, streams response

---

### 8. Get All Posts (Async - Streaming)
```bash
curl http://localhost:8080/api/webclient-demo/async/posts
```

**Benefit:** Posts streamed as they arrive (not waiting for all)

---

### 9. Create Post (Async)
```bash
curl -X POST http://localhost:8080/api/webclient-demo/async/post \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"title":"Async Post","body":"Created asynchronously"}'
```

---

### 10. Get Post with Timeout (Async)
```bash
curl http://localhost:8080/api/webclient-demo/async/post-timeout/1
```

**Benefit:** Times out after 5 seconds (doesn't hang forever)

---

### 11. Get Post with Retry (Async)
```bash
curl http://localhost:8080/api/webclient-demo/async/post-retry/1
```

**Benefit:** Retries up to 3 times on failure

---

### 12. Get Post with Error Handling (Async)
```bash
# Valid
curl http://localhost:8080/api/webclient-demo/async/post-safe/1

# Invalid (error handled gracefully)
curl http://localhost:8080/api/webclient-demo/async/post-safe/999999
```

---

## ⚡ PARALLEL API Calls (WebClient's Superpower!)

### 13. Sequential Calls (SLOW - ~1000ms)
```bash
curl http://localhost:8080/api/webclient-demo/sequential/user-posts/1
```

**What happens:**
1. Call User API → Wait 500ms
2. Call Posts API → Wait 500ms
3. **Total: ~1000ms**

**Response:**
```json
{
  "user": { "id": 1, "name": "Leanne Graham", ... },
  "posts": [...],
  "executionTime": "1023ms",
  "mode": "SEQUENTIAL"
}
```

---

### 14. Parallel Calls (FAST - ~500ms)
```bash
curl http://localhost:8080/api/webclient-demo/parallel/user-posts/1
```

**What happens:**
1. Call User API + Posts API **simultaneously**
2. Both complete in ~500ms
3. **Total: ~500ms** (50% faster!)

**Response:**
```json
{
  "user": { "id": 1, "name": "Leanne Graham", ... },
  "posts": [...],
  "executionTime": "537ms",
  "mode": "PARALLEL"
}
```

**🎯 This is why WebClient is powerful!**

---

### 15. Multiple Parallel Calls (3 APIs at once)
```bash
curl http://localhost:8080/api/webclient-demo/parallel/multiple/1
```

**What happens:**
- Calls 3 APIs **simultaneously**
- If sequential: 500ms × 3 = **1500ms**
- If parallel: **~500ms** (3x faster!)

---

## 🎨 ADVANCED Examples

### 16. Async with Transformation
```bash
curl http://localhost:8080/api/webclient-demo/async/post-transform/1
```

**Response:**
```json
{
  "id": 1,
  "title": "SUNT AUT FACERE REPELLAT PROVIDENT",
  "titleLength": 36,
  "bodyPreview": "quia et suscipit suscipit recusandae consequuntur..."
}
```

---

### 17. Async Chain (Get user, then their posts)
```bash
curl http://localhost:8080/api/webclient-demo/async/chain/1
```

**What happens:**
1. Get user with ID 1
2. Use that user's ID to get their posts
3. Combine both results

---

## 📊 Performance Comparison

### Test: Get User + Posts

| Method | Time | Endpoint |
|--------|------|----------|
| **Sequential (Sync)** | ~1000ms | `/sequential/user-posts/1` |
| **Parallel (Async)** | ~500ms | `/parallel/user-posts/1` |
| **Improvement** | **50% faster** | ⚡ |

### Test with Postman/cURL:

**Sequential:**
```bash
time curl http://localhost:8080/api/webclient-demo/sequential/user-posts/1
# Output: ~1.0 seconds
```

**Parallel:**
```bash
time curl http://localhost:8080/api/webclient-demo/parallel/user-posts/1
# Output: ~0.5 seconds
```

---

## 🧪 Testing with Postman

### Import Collection:

1. Open Postman
2. Import → Raw Text
3. Paste this collection:

```json
{
  "info": {
    "name": "WebClient Demo API",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "Sync - Get Post",
      "request": {
        "method": "GET",
        "url": "http://localhost:8080/api/webclient-demo/sync/post/1"
      }
    },
    {
      "name": "Async - Get Post",
      "request": {
        "method": "GET",
        "url": "http://localhost:8080/api/webclient-demo/async/post/1"
      }
    },
    {
      "name": "Parallel - User + Posts",
      "request": {
        "method": "GET",
        "url": "http://localhost:8080/api/webclient-demo/parallel/user-posts/1"
      }
    }
  ]
}
```

---

## 🔍 Understanding the Differences

### Synchronous (.block())
```java
Post post = webClient.get()
    .uri("/posts/1")
    .retrieve()
    .bodyToMono(Post.class)
    .block(); // ⛔ BLOCKS thread until response
```

**Use when:**
- Legacy code compatibility
- Simple CRUD operations
- Need immediate response in same method

---

### Asynchronous (Mono/Flux)
```java
Mono<Post> postMono = webClient.get()
    .uri("/posts/1")
    .retrieve()
    .bodyToMono(Post.class); // ✅ Returns immediately
```

**Use when:**
- High traffic (1000+ req/sec)
- Parallel API calls
- Reactive Spring applications
- Need to call multiple APIs

---

### Parallel Calls
```java
Mono<User> userMono = webClient.get().uri("/users/1")...
Mono<List<Post>> postsMono = webClient.get().uri("/posts")...

Mono.zip(userMono, postsMono) // ✅ Both execute in parallel!
```

**Use when:**
- Need data from multiple APIs
- Want to reduce total response time
- APIs are independent

---

## 🎯 Key Takeaways

### Synchronous (Blocking)
- ✅ Simple, straightforward
- ✅ Familiar programming model
- ❌ One thread per request (not scalable)
- ❌ Thread blocked during API call
- **Use for:** Simple apps, low traffic

### Asynchronous (Non-Blocking)
- ✅ Few threads handle many requests
- ✅ Non-blocking I/O
- ✅ Can call multiple APIs in parallel
- ✅ 10x more scalable
- ❌ More complex code
- **Use for:** High-traffic apps, microservices

### Performance Numbers:
```
Synchronous:
- 1000 requests = 1000 threads
- Memory: ~1GB (1MB per thread)

Asynchronous:
- 1000 requests = ~10-20 threads
- Memory: ~20MB
- 50x more efficient! 🚀
```

---

## 🚨 Common Errors & Fixes

### Error 1: "WebClient bean could not be found"
**Fix:** Add `spring-boot-starter-webflux` dependency

### Error 2: "Connection timeout"
**Fix:** Already configured in `WebClientConfig.java` (5s connect, 10s read)

### Error 3: "404 Not Found"
**Fix:** Check endpoint URL in controller

---

## 📚 Additional Resources

- Spring WebFlux Docs: https://docs.spring.io/spring-framework/reference/web/webflux.html
- Reactor Docs: https://projectreactor.io/docs/core/release/reference/
- JSONPlaceholder API: https://jsonplaceholder.typicode.com/

---

## 🎓 Interview Questions You Can Answer Now:

1. **"What's the difference between RestTemplate and WebClient?"**
   - Show synchronous vs asynchronous examples

2. **"How do you make parallel API calls?"**
   - Point to `/parallel/user-posts` endpoint

3. **"Why is WebClient better for microservices?"**
   - Compare `/sequential` vs `/parallel` performance

4. **"How do you handle errors in WebClient?"**
   - Show `/sync/post-safe` and `/async/post-safe` examples

5. **"Can WebClient be used synchronously?"**
   - Yes! Show all `/sync/*` endpoints

---

**Ready to test?** Start your app and hit: `http://localhost:8080/api/webclient-demo/health` 🚀
