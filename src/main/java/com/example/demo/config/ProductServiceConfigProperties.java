package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Configuration properties for Product and Pricing Service
 */
@Component
@ConfigurationProperties(prefix = "product.service")
@Data
public class ProductServiceConfigProperties {
    
    /**
     * Base URL of the product service (e.g., http://localhost:8080)
     */
    private String baseUrl;
    
    /**
     * Path template for product endpoint (e.g., /api/products)
     */
    private String path;
    
    /**
     * Get the complete URL for fetching product details
     * @param productCode Product code to fetch
     * @return Complete URL with product code
     */
    public String getProductUrl(String productCode) {
        return baseUrl + path + "/" + productCode;
    }
}
