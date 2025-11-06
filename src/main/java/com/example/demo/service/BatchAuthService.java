package com.example.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Service to handle batch processing authentication
 * Gets JWT token for batch operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchAuthService {
    
    private final RestTemplate restTemplate;
    
    @Value("${auth.service.base-url}")
    private String authServiceBaseUrl;
    
    @Value("${batch.user.email}")
    private String batchUserEmail;
    
    @Value("${batch.user.password}")
    private String batchUserPassword;
    
    private String cachedAccessToken = null;
    
    /**
     * Get JWT access token for batch processing
     * Caches the token to avoid repeated login calls
     */
    public String getBatchAccessToken() {
        // Return cached token if available
        if (cachedAccessToken != null && !cachedAccessToken.isEmpty()) {
            log.debug("Using cached batch access token");
            return cachedAccessToken;
        }
        
        // Login to get new token
        String loginUrl = authServiceBaseUrl + "/api/auth/login";
        
        log.info("🔑 Authenticating batch user: {}", batchUserEmail);
        
        try {
            // Build login request
            Map<String, Object> loginRequest = new HashMap<>();
            loginRequest.put("email", batchUserEmail);
            loginRequest.put("password", batchUserPassword);
            loginRequest.put("rememberMe", true);
            
            // Make login request
            ResponseEntity<Map> response = restTemplate.postForEntity(loginUrl, loginRequest, Map.class);
            
            if (response.getBody() != null && response.getBody().containsKey("data")) {
                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
                cachedAccessToken = (String) data.get("accessToken");
                
                log.info("✅ Batch authentication successful");
                log.debug("Access token: {}...", cachedAccessToken.substring(0, Math.min(20, cachedAccessToken.length())));
                
                return cachedAccessToken;
            } else {
                throw new RuntimeException("Login response missing data field");
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to authenticate batch user: {}", e.getMessage(), e);
            throw new RuntimeException("Batch authentication failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Clear cached token (call this if token expires or becomes invalid)
     */
    public void clearCachedToken() {
        log.info("Clearing cached batch access token");
        cachedAccessToken = null;
    }
}
