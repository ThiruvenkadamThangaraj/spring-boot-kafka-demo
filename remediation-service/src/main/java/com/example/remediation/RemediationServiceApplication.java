package com.example.remediation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"com.example.remediation", "com.example.common"})
@EntityScan(basePackages = {"com.example.common.entity", "com.example.remediation.entity"})
@EnableJpaRepositories(basePackages = "com.example.remediation.repository")
public class RemediationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RemediationServiceApplication.class, args);
    }
}
