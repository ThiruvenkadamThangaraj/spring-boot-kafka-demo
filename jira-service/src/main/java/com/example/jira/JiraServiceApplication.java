package com.example.jira;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.example.jira", "com.example.common"})
public class JiraServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(JiraServiceApplication.class, args);
    }
}
