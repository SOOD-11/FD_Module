package com.example.demo.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.demo.config.FDCalculationConfigProperties;
import com.example.demo.dto.FDCalculationResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service to interact with the FD Calculation Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FDCalculationService {

    private final FDCalculationConfigProperties calculationConfig;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Fetch calculation details from the FD Calculation Service
     * 
     * @param calcId The calculation ID
     * @return FD calculation response with maturity details
     */
    public FDCalculationResponse getCalculation(Long calcId) {
        try {
            String url = calculationConfig.getCalculationUrl(calcId);
            log.info("Fetching FD calculation from: {}", url);

            FDCalculationResponse response = restTemplate.getForObject(url, FDCalculationResponse.class);
            
            if (response == null) {
                throw new RuntimeException("No calculation data received for calcId: " + calcId);
            }

            log.info("Successfully fetched calculation for calcId: {}", calcId);
            return response;

        } catch (Exception e) {
            log.error("Error fetching calculation for calcId: {}", calcId, e);
            throw new RuntimeException("Failed to fetch FD calculation: " + e.getMessage(), e);
        }
    }
}
