package com.example.demo.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * Response from the Product and Pricing Service
 */
@Data
public class ProductDetailsResponse {
    
    @JsonProperty("productId")
    private String productId;
    
    @JsonProperty("productCode")
    private String productCode;
    
    @JsonProperty("productName")
    private String productName;
    
    @JsonProperty("productType")
    private String productType;
    
    @JsonProperty("currency")
    private String currency;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("interestType")
    private String interestType;
    
    @JsonProperty("compoundingFrequency")
    private String compoundingFrequency;
    
    @JsonProperty("productRules")
    private List<ProductRule> productRules;
    
    @JsonProperty("productCharges")
    private List<ProductCharge> productCharges;
    
    @JsonProperty("productRoles")
    private List<ProductRole> productRoles;
    
    @JsonProperty("productTransactions")
    private List<ProductTransaction> productTransactions;
    
    @JsonProperty("productBalances")
    private List<ProductBalance> productBalances;
    
    @JsonProperty("productCommunications")
    private List<ProductCommunication> productCommunications;
    
    @JsonProperty("productInterests")
    private List<ProductInterest> productInterests;
    
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;
    
    @JsonProperty("efctv_date")
    private LocalDateTime effectiveDate;
    
    @Data
    public static class ProductRule {
        @JsonProperty("ruleId")
        private String ruleId;
        
        @JsonProperty("ruleCode")
        private String ruleCode;
        
        @JsonProperty("ruleName")
        private String ruleName;
        
        @JsonProperty("ruleType")
        private String ruleType;
        
        @JsonProperty("dataType")
        private String dataType;
        
        @JsonProperty("ruleValue")
        private String ruleValue;
        
        @JsonProperty("validationType")
        private String validationType;
    }
    
    @Data
    public static class ProductCharge {
        @JsonProperty("chargeId")
        private String chargeId;
        
        @JsonProperty("chargeCode")
        private String chargeCode;
        
        @JsonProperty("chargeType")
        private String chargeType;
        
        @JsonProperty("calculationType")
        private String calculationType;
        
        @JsonProperty("frequency")
        private String frequency;
        
        @JsonProperty("amount")
        private Double amount;
    }
    
    @Data
    public static class ProductRole {
        @JsonProperty("roleId")
        private String roleId;
        
        @JsonProperty("roleCode")
        private String roleCode;
        
        @JsonProperty("roleType")
        private String roleType;
        
        @JsonProperty("roleName")
        private String roleName;
    }
    
    @Data
    public static class ProductTransaction {
        @JsonProperty("transactionId")
        private String transactionId;
        
        @JsonProperty("transactionCode")
        private String transactionCode;
        
        @JsonProperty("transactionType")
        private String transactionType;
    }
    
    @Data
    public static class ProductBalance {
        @JsonProperty("balanceId")
        private String balanceId;
        
        @JsonProperty("balanceType")
        private String balanceType;
        
        @JsonProperty("isActive")
        private Boolean isActive;
        
        @JsonProperty("createdAt")
        private LocalDateTime createdAt;
    }
    
    @Data
    public static class ProductCommunication {
        @JsonProperty("commId")
        private String commId;
        
        @JsonProperty("commCode")
        private String commCode;
        
        @JsonProperty("communicationType")
        private String communicationType;
        
        @JsonProperty("channel")
        private String channel;
        
        @JsonProperty("event")
        private String event;
        
        @JsonProperty("template")
        private String template;
        
        @JsonProperty("frequencyLimit")
        private String frequencyLimit;
    }
    
    @Data
    public static class ProductInterest {
        @JsonProperty("rateId")
        private String rateId;
        
        @JsonProperty("rateCode")
        private String rateCode;
        
        @JsonProperty("termInMonths")
        private Integer termInMonths;
        
        @JsonProperty("rateCumulative")
        private Double rateCumulative;
        
        @JsonProperty("rateNonCumulativeMonthly")
        private Double rateNonCumulativeMonthly;
        
        @JsonProperty("rateNonCumulativeQuarterly")
        private Double rateNonCumulativeQuarterly;
        
        @JsonProperty("rateNonCumulativeYearly")
        private Double rateNonCumulativeYearly;
    }
}
