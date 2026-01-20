package com.example.common.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

/**
 * Content Caching Filter - Required for audit interceptor to read request/response bodies
 * 
 * Spring's HttpServletRequest/Response input streams can only be read once.
 * This filter wraps them in caching wrappers so the audit interceptor can read the content.
 */
@Component
public class ContentCachingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;

            // Wrap request and response to cache content
            ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(httpRequest);
            ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(httpResponse);

            // Continue with wrapped request/response
            chain.doFilter(wrappedRequest, wrappedResponse);

            // Copy response content to actual response (important!)
            wrappedResponse.copyBodyToResponse();
        } else {
            chain.doFilter(request, response);
        }
    }
}
