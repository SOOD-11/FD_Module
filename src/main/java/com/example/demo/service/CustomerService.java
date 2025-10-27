package com.example.demo.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.demo.dto.CustomerProfileResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service to interact with Customer Profile Management API
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {
    
    private final RestTemplate restTemplate;
    
    // Customer Service base URL (should be externalized to config)
    private static final String CUSTOMER_SERVICE_BASE_URL = "http://localhost:1005";
    private static final String GET_PROFILE_BY_EMAIL_PATH = "/api/profiles/email/{email}";
    
    /**
     * Fetch customer profile by email address
     * 
     * @param email Customer's email address from JWT token
     * @return Customer profile with customerNumber
     */
    public CustomerProfileResponse getCustomerByEmail(String email) {
        String url = CUSTOMER_SERVICE_BASE_URL + GET_PROFILE_BY_EMAIL_PATH;
        
        log.info("Fetching customer profile for email: {}", email);
        
        try {
            CustomerProfileResponse response = restTemplate.getForObject(
                url, 
                CustomerProfileResponse.class, 
                email
            );
            
            if (response != null) {
                log.info("Found customer: customerNumber={}, customerId={}, name={} {}", 
                         response.getCustomerNumber(), 
                         response.getCustomerId(),
                         response.getFirstName(), 
                         response.getLastName());
            }
            
            return response;
        } catch (Exception e) {
            log.error("Error fetching customer profile for email: {}", email, e);
            throw new RuntimeException("Failed to fetch customer profile: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get customer number (business identifier) from email
     * This is what we'll use as customerId in FD Account
     * 
     * @param email Customer's email from JWT
     * @return Customer number (e.g., CUST-20251024-000001)
     */
    public String getCustomerNumberByEmail(String email) {
        CustomerProfileResponse profile = getCustomerByEmail(email);
        
        if (profile == null || profile.getCustomerNumber() == null) {
            throw new RuntimeException("Customer number not found for email: " + email);
        }
        
        return profile.getCustomerNumber();
    }
}
