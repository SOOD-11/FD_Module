package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "auth.service")
public class JwtConfigProperties {
    
    /**
     * Base URL of the authentication service (e.g., http://localhost:3020)
     */
    private String baseUrl;
    
    /**
     * Path to the public key endpoint (e.g., /api/auth/public-key)
     */
    private String publicKeyPath;
    
    /**
     * Get the full URL for fetching the public key
     */
    public String getPublicKeyUrl() {
        return baseUrl + publicKeyPath;
    }
}
