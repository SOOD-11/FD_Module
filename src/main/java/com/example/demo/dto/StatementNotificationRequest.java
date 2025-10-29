package com.example.demo.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StatementNotificationRequest(
    
    // --- 1. Notification Routing & Content ---
    String toEmail,
    String toSms,
    String subject,
    String body,
    
    // --- 2. Metadata ---
    String statementType,
    String pdfFileName,
    StatementPeriodDto statementPeriod,
    String generatedOnDate,
    
    // --- 3. Customer Details ---
    CustomerDetailsDto customerDetails,
    
    // --- 4. Account Details ---
    AccountDetailsDto accountDetails,
    
    // --- 5. Current Balances ---
    CurrentBalancesDto currentBalances,
    
    // --- 6. Transaction History ---
    List<StatementTransactionDto> transactions
) {
}
