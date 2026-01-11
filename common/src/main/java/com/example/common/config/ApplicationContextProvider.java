package com.example.common.config;

import com.example.common.dto.ApiVersionHolder;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ApplicationContextProvider {

    @Autowired
    private ApiVersionConfig apiVersionConfig;

    @PostConstruct
    public void init() {
        ApiVersionHolder.setVersion(apiVersionConfig.getVersion());
    }
}
