package com.example.demo.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * Response from Customer Profile Management API
 * Used to fetch customer details by email
 */
@Data
public class CustomerProfileResponse {
    
    @JsonProperty("customerId")
    private String customerId;
    
    @JsonProperty("customerNumber")
    private String customerNumber; // This is what we'll use as customerId in FD Account
    
    @JsonProperty("crudOperation")
    private String crudOperation;
    
    @JsonProperty("versionTimestamp")
    private LocalDateTime versionTimestamp;
    
    @JsonProperty("userId")
    private String userId;
    
    @JsonProperty("email")
    private String email;
    
    @JsonProperty("firstName")
    private String firstName;
    
    @JsonProperty("lastName")
    private String lastName;
    
    @JsonProperty("middleName")
    private String middleName;
    
    @JsonProperty("dateOfBirth")
    private LocalDate dateOfBirth;
    
    @JsonProperty("gender")
    private String gender;
    
    @JsonProperty("nationality")
    private String nationality;
    
    @JsonProperty("phoneNumber")
    private String phoneNumber;
    
    @JsonProperty("alternatePhone")
    private String alternatePhone;
    
    @JsonProperty("addressLine1")
    private String addressLine1;
    
    @JsonProperty("addressLine2")
    private String addressLine2;
    
    @JsonProperty("city")
    private String city;
    
    @JsonProperty("state")
    private String state;
    
    @JsonProperty("country")
    private String country;
    
    @JsonProperty("postalCode")
    private String postalCode;
    
    @JsonProperty("maskedAadhar")
    private String maskedAadhar;
    
    @JsonProperty("maskedPan")
    private String maskedPan;
    
    @JsonProperty("aadharNumber")
    private String aadharNumber;
    
    @JsonProperty("panNumber")
    private String panNumber;
    
    @JsonProperty("passportNumber")
    private String passportNumber;
    
    @JsonProperty("drivingLicense")
    private String drivingLicense;
    
    @JsonProperty("customerType")
    private String customerType;
    
    @JsonProperty("customerStatus")
    private String customerStatus;
    
    @JsonProperty("kycStatus")
    private String kycStatus;
    
    @JsonProperty("kycCompletionDate")
    private LocalDateTime kycCompletionDate;
}
