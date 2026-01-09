package com.example.demo.futures;

import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class FutureService {

    ExecutorService ioPool = Executors.newFixedThreadPool(3);
    ExecutorService cpuPool = Executors.newFixedThreadPool(3);

    public void runFutures() {
        for (int i = 1; i <= 10; i++) {
            int id = i;

            CompletableFuture.runAsync(() -> {
                System.out.println("Outer " + id);

                try { Thread.sleep(3000); } catch (Exception ignored) {}

                CompletableFuture.runAsync(() ->
                        System.out.println("Inner " + id), cpuPool
                );

            }, ioPool);
        }
    }
}

