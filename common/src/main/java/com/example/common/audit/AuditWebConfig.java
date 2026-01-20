package com.example.common.audit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web Configuration to register Audit Interceptor
 * 
 * To enable audit logging in any microservice, add this to application.properties:
 * audit.enabled=true
 * 
 * This will automatically capture all HTTP requests and responses
 */
@Configuration
@ConditionalOnProperty(name = "audit.enabled", havingValue = "true", matchIfMissing = false)
public class AuditWebConfig implements WebMvcConfigurer {

    @Autowired
    private AuditInterceptor auditInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditInterceptor)
                .addPathPatterns("/api/**")  // Only audit API endpoints
                .excludePathPatterns(
                        "/api/audit/**",           // Don't audit the audit service itself
                        "/actuator/**",            // Don't audit actuator endpoints
                        "/h2-console/**",          // Don't audit H2 console
                        "/swagger-ui/**",          // Don't audit Swagger UI
                        "/v3/api-docs/**"          // Don't audit API docs
                );
    }
}
