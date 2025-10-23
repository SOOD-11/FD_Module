package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * Response from the FD Calculation Service
 */
@Data
public class FDCalculationResponse {
    
    @JsonProperty("maturity_value")
    private BigDecimal maturityValue;
    
    @JsonProperty("maturity_date")
    private LocalDate maturityDate;
    
    @JsonProperty("apy")
    private BigDecimal apy;
    
    @JsonProperty("effective_rate")
    private BigDecimal effectiveRate;
    
    @JsonProperty("payout_freq")
    private String payoutFreq;
    
    @JsonProperty("payout_amount")
    private BigDecimal payoutAmount;
    
    @JsonProperty("calc_id")
    private Long calcId;
    
    @JsonProperty("result_id")
    private Long resultId;
    
    @JsonProperty("category1_id")
    private String category1Id;
    
    @JsonProperty("category2_id")
    private String category2Id;
    
    @JsonProperty("product_code")
    private String productCode;
}
