package com.example.demo.resilience;

import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
public class RetryTimeoutService {

    public CompletableFuture<String> callWithTimeout() {
        return CompletableFuture.supplyAsync(() -> {
            try { Thread.sleep(5000); } catch (Exception ignored) {}
            return "OK";
        }).completeOnTimeout("timeout", 2, TimeUnit.SECONDS);
    }

    public CompletableFuture<String> retry(Supplier<CompletableFuture<String>> supplier, int attempts) {
        return supplier.get().handle((res, ex) -> {
            if (ex == null) {
                return CompletableFuture.completedFuture(res);
            }
            if (attempts > 1) {
                return retry(supplier, attempts - 1);
            }
            return CompletableFuture.<String>failedFuture(ex);
        }).thenCompose(f -> f);
    }
}
