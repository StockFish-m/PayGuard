package com.payguard.engine.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@EnableScheduling
@Configuration
public class PayOsConfig {

    @Bean
    public RestClient payOsRestClient(PayOsProperties properties) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(Duration.ofSeconds(10));

        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.jdk().build(settings);

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("x-client-id", properties.getClientId())
                .defaultHeader("x-api-key", properties.getApiKey())
                .requestFactory(requestFactory)
                .build();
    }
}