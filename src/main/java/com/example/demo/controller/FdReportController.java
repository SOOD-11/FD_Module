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

@RestController
@RequestMapping("/api/v1/reports")
public class FdReportController {
	
	
	
	private final FDReportService reportService;

    public FdReportController(FDReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * Report to find all active accounts that are maturing within a specified number of days from now.
     * @param days The number of days to look ahead for maturing accounts. Defaults to 7.
     * @return A list of account views that are maturing soon.
     */
    
    
    @GetMapping("/accounts/maturing")
    public ResponseEntity<List<FDAccountView>> getMaturingAccounts(
            @RequestParam("days") int days) {
        List<FDAccountView> accounts = reportService.getAccountsMaturingWithin(days);
        if (accounts.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(accounts);
    }
    
    
    
    
   
    @GetMapping("/accounts/created")
    public ResponseEntity<List<FDAccountView>> getCreatedAccounts(
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<FDAccountView> accounts = reportService.getAccountsCreatedBetween(startDate, endDate);
        if (accounts.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(accounts);
    }
    
    
    @GetMapping("/accounts/closed")
    public ResponseEntity<List<FDAccountView>> getClosedAccounts(
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "status", required = false) Optional<String> status) { // Use Optional for the optional parameter
        List<FDAccountView> accounts = reportService.getAccountsClosedBetween(startDate, endDate, status);
        if (accounts.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(accounts);
    }

}
