package com.example.sampling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.example.sampling", "com.example.common"})
@EntityScan(basePackages = {"com.example.common.entity", "com.example.sampling.entity"})
public class SamplingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SamplingServiceApplication.class, args);
    }
}
