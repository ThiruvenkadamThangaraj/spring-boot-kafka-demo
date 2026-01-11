package com.example.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.beans.factory.annotation.Value;

@Configuration
@PropertySource("classpath:version.properties")
public class ApiVersionConfig {

    @Value("${api.version}")
    private String version;

    @Value("${api.build.date:}")
    private String buildDate;

    public String getVersion() {
        return version;
    }

    public String getBuildDate() {
        return buildDate;
    }
}
