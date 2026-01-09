package com.example.demo.starvation;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class StarvationService {

    @Async("smallPool")
    public void outerTask(int id) {
        System.out.println("Outer " + id + " on " + Thread.currentThread().getName());

        try { Thread.sleep(3000); } catch (Exception ignored) {}

        System.out.println("Outer " + id + " submitting inner task");

        innerTask(id);
    }

    @Async("smallPool")
    public void innerTask(int id) {
        System.out.println("Inner " + id + " running on " + Thread.currentThread().getName());
    }
}
