package com.example.demo.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.FDAccountView;
import com.example.demo.service.FDReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "FD Reports", description = "APIs for generating Fixed Deposit reports and analytics")
public class FdReportController {
	
	
	
	private final FDReportService reportService;

    public FdReportController(FDReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "Get maturing accounts", description = "Retrieve all active FD accounts that are maturing within a specified number of days from now")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Maturing accounts retrieved successfully"),
        @ApiResponse(responseCode = "204", description = "No maturing accounts found")
    })
    @GetMapping("/accounts/maturing")
    public ResponseEntity<List<FDAccountView>> getMaturingAccounts(
            @Parameter(description = "Number of days to look ahead for maturing accounts", required = true, example = "7")
            @RequestParam("days") int days) {
        List<FDAccountView> accounts = reportService.getAccountsMaturingWithin(days);
        if (accounts.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(accounts);
    }
    
    
    
    
   
    @Operation(summary = "Get accounts created between dates", description = "Retrieve all FD accounts created within a specified date range")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Accounts retrieved successfully"),
        @ApiResponse(responseCode = "204", description = "No accounts found in the date range")
    })
    @GetMapping("/accounts/created")
    public ResponseEntity<List<FDAccountView>> getCreatedAccounts(
            @Parameter(description = "Start date (ISO format: yyyy-MM-dd)", required = true, example = "2025-01-01")
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date (ISO format: yyyy-MM-dd)", required = true, example = "2025-12-31")
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<FDAccountView> accounts = reportService.getAccountsCreatedBetween(startDate, endDate);
        if (accounts.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(accounts);
    }
    
    
    @Operation(summary = "Get closed accounts", description = "Retrieve all FD accounts closed within a specified date range, optionally filtered by status")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Closed accounts retrieved successfully"),
        @ApiResponse(responseCode = "204", description = "No closed accounts found")
    })
    @GetMapping("/accounts/closed")
    public ResponseEntity<List<FDAccountView>> getClosedAccounts(
            @Parameter(description = "Start date (ISO format: yyyy-MM-dd)", required = true, example = "2025-01-01")
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date (ISO format: yyyy-MM-dd)", required = true, example = "2025-12-31")
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "Optional status filter (e.g., CLOSED, MATURED)", required = false)
            @RequestParam(name = "status", required = false) Optional<String> status) {
        List<FDAccountView> accounts = reportService.getAccountsClosedBetween(startDate, endDate, status);
        if (accounts.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(accounts);
    }

}
