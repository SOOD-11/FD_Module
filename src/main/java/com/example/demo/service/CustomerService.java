package com.example.demo.service;

import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
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
    private static final String GET_PROFILE_BY_CUSTOMER_NUMBER_PATH = "/api/profiles/customer-number/{customerNumber}";
    private static final String GET_EMAIL_BY_CUSTOMER_NUMBER_PATH = "/api/profiles/public/customer/{customerNumber}/email";
    private static final String GET_PHONE_BY_CUSTOMER_NUMBER_PATH = "/api/profiles/public/customer/{customerNumber}/phone";
    
    /**
     * Fetch customer profile by email address
     * 
     * @param email Customer's email address from JWT token
     * @param jwtToken JWT token to include in Authorization header
     * @return Customer profile with customerNumber
     */
    public CustomerProfileResponse getCustomerByEmail(String email, String jwtToken) {
        String url = CUSTOMER_SERVICE_BASE_URL + GET_PROFILE_BY_EMAIL_PATH;
        
        log.info("🔍 Fetching customer profile for email: {} (URL path parameter only)", email);
        
        try {
            // Create headers with Authorization Bearer token
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + jwtToken);
            
            // Empty body - email goes ONLY in URL path parameter
            HttpEntity<String> requestEntity = new HttpEntity<>(null, headers);
            
            // Make GET request with JWT header and email in URL path
            ResponseEntity<CustomerProfileResponse> responseEntity = restTemplate.exchange(
                url, 
                HttpMethod.GET, 
                requestEntity,
                CustomerProfileResponse.class, 
                email  // This goes into {email} path variable
            );
            
            CustomerProfileResponse response = responseEntity.getBody();
            
            if (response != null) {
                log.info("✅ Found customer: customerNumber={}, customerId={}, name={} {}", 
                         response.getCustomerNumber(), 
                         response.getCustomerId(),
                         response.getFirstName(), 
                         response.getLastName());
            }
            
            return response;
        } catch (Exception e) {
            log.error("❌ Error fetching customer profile for email: {}", email, e);
            throw new RuntimeException("Failed to fetch customer profile: " + e.getMessage(), e);
        }
    }
    
    /**
     * Fetch customer profile by customer number (for batch processing without JWT)
     * 
     * @param customerNumber Customer's business identifier (stored as customerId in account_holders)
     * @return Customer profile
     */
    public CustomerProfileResponse getCustomerByCustomerNumber(String customerNumber) {
        String url = CUSTOMER_SERVICE_BASE_URL + GET_PROFILE_BY_CUSTOMER_NUMBER_PATH;
        
        log.info("Fetching customer profile for customer number: {}", customerNumber);
        
        try {
            // For batch processing, make request without JWT token
            ResponseEntity<CustomerProfileResponse> responseEntity = restTemplate.getForEntity(
                url, 
                CustomerProfileResponse.class, 
                customerNumber
            );
            
            CustomerProfileResponse response = responseEntity.getBody();
            
            if (response != null) {
                log.info("Found customer: customerNumber={}, customerId={}, email={}, name={} {}", 
                         response.getCustomerNumber(), 
                         response.getCustomerId(),
                         response.getEmail(),
                         response.getFirstName(), 
                         response.getLastName());
            }
            
            return response;
        } catch (Exception e) {
            log.error("Error fetching customer profile for customer number: {}", customerNumber, e);
            throw new RuntimeException("Failed to fetch customer profile: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get customer email by customer number (public endpoint, no auth needed)
     * 
     * @param customerNumber Customer's business identifier
     * @return Customer email address
     */
    public String getEmailByCustomerNumber(String customerNumber) {
        String url = CUSTOMER_SERVICE_BASE_URL + GET_EMAIL_BY_CUSTOMER_NUMBER_PATH;
        
        log.info("Fetching customer email for customer number: {}", customerNumber);
        
        try {
            // Public endpoint - no JWT needed, returns JSON: {"customerNumber":"...", "email":"..."}
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> responseEntity = restTemplate.getForEntity(
                url, 
                Map.class, 
                customerNumber
            );
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = responseEntity.getBody();
            
            if (response != null && response.containsKey("email")) {
                String email = (String) response.get("email");
                log.info("✅ Found customer email from response: {}", email);
                return email;
            } else {
                throw new RuntimeException("Email not found in response for customer number: " + customerNumber);
            }
            
        } catch (Exception e) {
            log.error("❌ Error fetching customer email for customer number: {}", customerNumber, e);
            throw new RuntimeException("Failed to fetch customer email: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get customer phone number by customer number (public endpoint, no auth needed)
     * 
     * @param customerNumber Customer's business identifier
     * @return Customer phone number
     */
    public String getPhoneByCustomerNumber(String customerNumber) {
        String url = CUSTOMER_SERVICE_BASE_URL + GET_PHONE_BY_CUSTOMER_NUMBER_PATH;
        
        log.info("Fetching customer phone for customer number: {}", customerNumber);
        
        try {
            // Public endpoint - no JWT needed, returns JSON: {"customerNumber":"...", "phoneNumber":"...", "alternatePhone":"..."}
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> responseEntity = restTemplate.getForEntity(
                url, 
                Map.class, 
                customerNumber
            );
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = responseEntity.getBody();
            
            if (response != null && response.containsKey("phoneNumber")) {
                String phone = (String) response.get("phoneNumber");
                log.info("✅ Found customer phone from response: {}", phone);
                return phone;
            } else {
                throw new RuntimeException("phoneNumber not found in response for customer number: " + customerNumber);
            }
            
        } catch (Exception e) {
            log.error("❌ Error fetching customer phone for customer number: {}", customerNumber, e);
            throw new RuntimeException("Failed to fetch customer phone: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get customer number (business identifier) from email
     * This is what we'll use as customerId in FD Account
     * 
     * @param email Customer's email from JWT
     * @param jwtToken JWT token to include in Authorization header
     * @return Customer number (e.g., CUST-20251024-000001)
     */
    public String getCustomerNumberByEmail(String email, String jwtToken) {
        CustomerProfileResponse profile = getCustomerByEmail(email, jwtToken);
        
        if (profile == null || profile.getCustomerNumber() == null) {
            throw new RuntimeException("Customer number not found for email: " + email);
        }
        
        return profile.getCustomerNumber();
    }
}
