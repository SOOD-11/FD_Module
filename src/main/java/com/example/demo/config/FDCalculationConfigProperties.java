package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "fd.calculation.service")
public class FDCalculationConfigProperties {
    
    /**
     * Base URL of the FD calculation service (e.g., http://localhost:4030)
     */
    private String baseUrl;
    
    /**
     * Path to the calculation endpoint (e.g., /api/fd/calculations)
     */
    private String path;
    
    /**
     * Get the full URL for fetching calculation details
     * 
     * @param calcId The calculation ID
     * @return Full URL to fetch the calculation
     */
    public String getCalculationUrl(Long calcId) {
        return baseUrl + path + "/" + calcId;
    }
}
