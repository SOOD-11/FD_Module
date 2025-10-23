package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for HTTP client beans
 */
@Configuration
public class RestTemplateConfig {
    
    /**
     * Creates a RestTemplate bean for making HTTP calls to external services
     * Used by:
     * - ProductService (Product and Pricing API)
     * - FDAccountService (FD Calculation Service)
     * - JwtPublicKeyProvider (Auth Service)
     * 
     * @return configured RestTemplate instance
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
