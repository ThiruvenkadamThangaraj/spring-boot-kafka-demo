package com.example.remediation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = {"com.example.common.entity", "com.example.remediation.entity"})
@EnableJpaRepositories(basePackages = "com.example.remediation.repository")
public class RemediationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RemediationServiceApplication.class, args);
    }
}
