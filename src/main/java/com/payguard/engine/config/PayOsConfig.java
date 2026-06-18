package com.payguard.engine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Configuration
public class PayOsConfig {

    /**
     * Đúc ra con WebClient đặc chủng dành riêng cho payOS
     * Spring Boot sẽ tự động tìm và nạp 'PayOsProperties' vào tham số dưới đây
     */
    @Bean
    public WebClient payOsWebClient(PayOsProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.getBaseUrl()) // Nạp Base URL từ file .yml
                .filter(autoAddPayOsHeaders(properties)) // Cắm bộ lọc tự động chèn Header vào đây
                .build();
    }

    /**
     * Bộ lọc (Filter) tự động giật lấy Request chiều đi và đập thêm 2 cái Header bí
     * mật vào
     */
    private ExchangeFilterFunction autoAddPayOsHeaders(PayOsProperties properties) {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            return Mono.just(
                    org.springframework.web.reactive.function.client.ClientRequest.from(clientRequest)
                            .header("x-client-id", properties.getClientId()) // Tự động điền Client ID
                            .header("x-api-key", properties.getApiKey()) // Tự động điền API Key
                            .build());
        });
    }
}