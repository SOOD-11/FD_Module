package com.example.demo.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.demo.config.ProductServiceConfigProperties;
import com.example.demo.dto.ProductDetailsResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service to interact with Product and Pricing Service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
    
    private final RestTemplate restTemplate;
    private final ProductServiceConfigProperties productConfig;
    
    /**
     * Fetch complete product details from Product and Pricing Service
     * 
     * @param productCode Product code from calculation response
     * @return Complete product configuration including balances, roles, transactions, communications
     */
    public ProductDetailsResponse getProductDetails(String productCode) {
        String url = productConfig.getProductUrl(productCode);
        
        log.info("Fetching product details from: {}", url);
        
        try {
            ProductDetailsResponse response = restTemplate.getForObject(url, ProductDetailsResponse.class);
            
            if (response == null) {
                throw new RuntimeException("Product not found: " + productCode);
            }
            
            log.info("Product details fetched successfully for code: {}", productCode);
            log.info("Product has {} balance types, {} roles, {} transaction types, {} communications", 
                     response.getProductBalances() != null ? response.getProductBalances().size() : 0,
                     response.getProductRoles() != null ? response.getProductRoles().size() : 0,
                     response.getProductTransactions() != null ? response.getProductTransactions().size() : 0,
                     response.getProductCommunications() != null ? response.getProductCommunications().size() : 0);
            
            return response;
        } catch (Exception e) {
            log.error("Failed to fetch product details for code: {}", productCode, e);
            throw new RuntimeException("Failed to fetch product details: " + e.getMessage(), e);
        }
    }
    
    /**
     * Validate if a role is allowed for the product
     * 
     * @param productCode Product code
     * @param roleType Role type to validate
     * @return true if role is allowed, false otherwise
     */
    public boolean isRoleAllowed(String productCode, String roleType) {
        ProductDetailsResponse product = getProductDetails(productCode);
        
        if (product.getProductRoles() == null || product.getProductRoles().isEmpty()) {
            log.warn("No roles configured for product: {}", productCode);
            return false;
        }
        
        return product.getProductRoles().stream()
                .anyMatch(role -> role.getRoleType().equals(roleType));
    }
    
    /**
     * Validate if a transaction type is allowed for the product
     * 
     * @param productCode Product code
     * @param transactionType Transaction type to validate
     * @return true if transaction is allowed, false otherwise
     */
    public boolean isTransactionAllowed(String productCode, String transactionType) {
        ProductDetailsResponse product = getProductDetails(productCode);
        
        if (product.getProductTransactions() == null || product.getProductTransactions().isEmpty()) {
            log.warn("No transactions configured for product: {}", productCode);
            return false;
        }
        
        return product.getProductTransactions().stream()
                .anyMatch(txn -> txn.getTransactionType().equals(transactionType));
    }
    
    /**
     * Get penalty charge for early withdrawal based on completion percentage
     * 
     * @param productCode Product code
     * @param completionPercentage Percentage of FD term completed (0-100)
     * @return Penalty charge details
     */
    public ProductDetailsResponse.ProductCharge getPenaltyCharge(String productCode, double completionPercentage) {
        ProductDetailsResponse product = getProductDetails(productCode);
        
        if (product.getProductCharges() == null || product.getProductCharges().isEmpty()) {
            return null;
        }
        
        // Extract suffix from product code (e.g., "FD001" -> "001")
        String suffix = productCode.length() > 2 ? productCode.substring(2) : productCode;
        
        // Determine penalty code based on completion percentage
        String penaltyCode;
        if (completionPercentage < 50.0) {
            penaltyCode = "PEN-H-" + suffix; // High penalty for < 50% completion
        } else {
            penaltyCode = "PEN-L-" + suffix; // Low penalty for >= 50% completion
        }
        
        log.info("Looking for penalty charge: {} (completion: {}%)", penaltyCode, completionPercentage);
        
        return product.getProductCharges().stream()
                .filter(charge -> charge.getChargeCode().equals(penaltyCode))
                .findFirst()
                .orElse(null);
    }
}
