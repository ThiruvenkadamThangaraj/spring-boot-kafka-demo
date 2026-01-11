package com.example.sampling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"com.example.sampling", "com.example.common"})
@EntityScan(basePackages = {"com.example.common.entity", "com.example.sampling.entity"})
@EnableJpaRepositories(basePackages = "com.example.sampling.repository")
public class SamplingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SamplingServiceApplication.class, args);
    }
}
