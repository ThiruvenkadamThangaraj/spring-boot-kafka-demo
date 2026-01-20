package com.example.evaluation.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    /**
     * WebClient for JSONPlaceholder API (public testing API)
     * Base URL: https://jsonplaceholder.typicode.com
     */
    @Bean
    public WebClient jsonPlaceholderWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(10))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(10, TimeUnit.SECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.SECONDS)));

        return WebClient.builder()
                .baseUrl("https://jsonplaceholder.typicode.com")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .filter(logRequest())
                .filter(logResponse())
                .build();
    }

    /**
     * WebClient for ReqRes API (another public testing API)
     * Base URL: https://reqres.in/api
     */
    @Bean
    public WebClient reqResWebClient() {
        return WebClient.builder()
                .baseUrl("https://reqres.in/api")
                .defaultHeader("Content-Type", "application/json")
                .filter(logRequest())
                .filter(logResponse())
                .build();
    }

    /**
     * Generic WebClient without base URL
     */
    @Bean
    public WebClient genericWebClient() {
        return WebClient.builder()
                .filter(logRequest())
                .filter(logResponse())
                .build();
    }

    /**
     * Log request details
     */
    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            System.out.println("Request: " + clientRequest.method() + " " + clientRequest.url());
            clientRequest.headers().forEach((name, values) ->
                    values.forEach(value -> System.out.println(name + ": " + value)));
            return Mono.just(clientRequest);
        });
    }

    /**
     * Log response details
     */
    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            System.out.println("Response Status Code: " + clientResponse.statusCode());
            return Mono.just(clientResponse);
        });
    }
}
