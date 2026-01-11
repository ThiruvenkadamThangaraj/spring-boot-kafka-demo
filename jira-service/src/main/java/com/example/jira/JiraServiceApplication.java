package com.example.jira;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"com.example.jira", "com.example.common"})
@EntityScan(basePackages = {"com.example.common.entity", "com.example.jira.entity"})
@EnableJpaRepositories(basePackages = "com.example.jira.repository")
public class JiraServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(JiraServiceApplication.class, args);
    }
}
