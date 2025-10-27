package com.example.demo.service.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.demo.dto.AccountHolderView;
import com.example.demo.dto.FDAccountView;
import com.example.demo.entities.FdAccount;
import com.example.demo.enums.AccountStatus;
import com.example.demo.repository.FdAccountRepository;
import com.example.demo.service.FDReportService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
@Slf4j
@Service
@Transactional// Reporting services are typically read-only
@RequiredArgsConstructor

public class FDReportServiceImpl implements FDReportService{
	
	
	
	
	private final FdAccountRepository fdAccountRepository;
	
	
public List<FDAccountView> getAccountsMaturingWithin(int days) {
        log.info("Generating report for accounts maturing in the next {} days.", days);

        LocalDate today = LocalDate.now();
        LocalDate futureDate = today.plusDays(days);

        // Use our existing repository method
        List<FdAccount> accounts = fdAccountRepository.findByMaturityDateBetween(today, futureDate);

        // Map the entities to DTOs for the response
        return accounts.stream()
                .map(this::mapToView)
                .collect(Collectors.toList());
    }

    // This is a private helper method to map the entity to a DTO.
    // We can extract this into a shared mapper class later if we want.
    private FDAccountView mapToView(FdAccount account) {
        List<AccountHolderView> holderViews = account.getAccountHolders().stream()
                .map(holder -> new AccountHolderView(
                        holder.getCustomerId(), holder.getRoleType(), holder.getOwnershipPercentage()))
                .collect(Collectors.toList());

        return new FDAccountView(
                account.getAccountNumber(),
                account.getAccountName(),
                account.getProductCode(),
                account.getStatus(),
                account.getPrincipalAmount(),
                account.getMaturityAmount(),
                account.getEffectiveDate(),
                account.getMaturityDate(),
                account.getInterestRate(),
                account.getApy(),
                account.getCategory1Id(),
                account.getCategory2Id(),
                account.getInterestType(),
                account.getCompoundingFrequency(),
                account.getTenureValue(),
                account.getTenureUnit(),
                account.getCurrency(),
                holderViews);
    }
    
    
    
    public List<FDAccountView> getAccountsCreatedBetween(LocalDate startDate, LocalDate endDate) {
        log.info("Generating report for accounts created between {} and {}.", startDate, endDate);
        // We use atStartOfDay() and plusDays(1) to make the end date inclusive for the full day.
        List<FdAccount> accounts = fdAccountRepository.findByCreatedAtBetween(startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());
        return accounts.stream().map(this::mapToView).collect(Collectors.toList());
    }

    
    
    
    public List<FDAccountView> getAccountsClosedBetween(LocalDate startDate, LocalDate endDate, Optional<String> status) {
        log.info("Generating report for accounts closed between {} and {} with optional status filter: {}", startDate, endDate, status);
        
        List<AccountStatus> targetStatuses;
        
        // If a specific status is provided (and is valid), search only for that status.
        // Otherwise, search for all possible closed statuses.
        if (status.isPresent() && !status.get().isBlank()) {
            try {
                AccountStatus requestedStatus = AccountStatus.valueOf(status.get().toUpperCase());
                // Ensure the requested status is a valid "closed" type
                if (requestedStatus == AccountStatus.MATURED || requestedStatus == AccountStatus.PREMATURELY_CLOSED || requestedStatus == AccountStatus.CLOSED) {
                    targetStatuses = List.of(requestedStatus);
                } else {
                    // If the status is not a valid "closed" status, return an empty list to prevent errors.
                    log.warn("Invalid status '{}' provided for closed accounts report. Returning empty.", status.get());
                    return List.of();
                }
            } catch (IllegalArgumentException e) {
                // If the provided status string is not a valid enum constant, return an empty list.
                log.warn("Unknown status '{}' provided for closed accounts report. Returning empty.", status.get());
                return List.of();
            }
        } else {
            // Default to all "closed" statuses if no specific one is requested.
            targetStatuses = List.of(AccountStatus.MATURED, AccountStatus.PREMATURELY_CLOSED, AccountStatus.CLOSED);
        }

        List<FdAccount> accounts = fdAccountRepository.findByStatusInAndClosedAtBetween(
                targetStatuses,
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay()
        );

        return accounts.stream().map(this::mapToView).collect(Collectors.toList());
    }
   

}
