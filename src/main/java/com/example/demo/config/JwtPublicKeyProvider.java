package com.example.demo.config;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtPublicKeyProvider {

    private final JwtConfigProperties jwtConfigProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Fetches the RSA public key from the authentication service
     * 
     * @return RSAPublicKey for JWT validation
     */
    public RSAPublicKey fetchPublicKey() {
        try {
            String publicKeyUrl = jwtConfigProperties.getPublicKeyUrl();
            log.info("Fetching public key from: {}", publicKeyUrl);

            // Call the authentication service to get the public key
            String response = restTemplate.getForObject(publicKeyUrl, String.class);
            
            // Parse the JSON response
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
            
            @SuppressWarnings("unchecked")
            List<Map<String, String>> keys = (List<Map<String, String>>) responseMap.get("keys");
            
            if (keys == null || keys.isEmpty()) {
                throw new RuntimeException("No keys found in the response");
            }

            // Get the first key (you can add logic to select based on 'kid' if needed)
            Map<String, String> key = keys.get(0);
            String modulus = key.get("n");
            String exponent = key.get("e");

            // Decode the Base64 URL-encoded values
            byte[] modulusBytes = Base64.getUrlDecoder().decode(modulus);
            byte[] exponentBytes = Base64.getUrlDecoder().decode(exponent);

            // Create BigInteger values
            BigInteger modulusBigInt = new BigInteger(1, modulusBytes);
            BigInteger exponentBigInt = new BigInteger(1, exponentBytes);

            // Create RSA public key
            RSAPublicKeySpec keySpec = new RSAPublicKeySpec(modulusBigInt, exponentBigInt);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(keySpec);

            log.info("Successfully fetched and constructed RSA public key");
            return publicKey;

        } catch (Exception e) {
            log.error("Error fetching public key from authentication service", e);
            throw new RuntimeException("Failed to fetch public key: " + e.getMessage(), e);
        }
    }
}
