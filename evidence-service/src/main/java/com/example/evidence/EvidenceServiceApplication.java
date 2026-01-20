package com.example.evidence;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.example.evidence", "com.example.common"})
@EntityScan(basePackages = {"com.example.common.entity", "com.example.evidence.entity"})
public class EvidenceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EvidenceServiceApplication.class, args);
    }
}
