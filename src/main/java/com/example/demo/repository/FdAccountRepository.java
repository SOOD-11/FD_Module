package com.example.demo.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.demo.entities.FdAccount;
import com.example.demo.enums.AccountStatus;

@Repository
public interface FdAccountRepository extends JpaRepository<FdAccount, Long> {

    /**
     * Finds an account by its unique, user-facing account number.
     * WHY: Essential for API lookups and ensuring uniqueness during account creation.
     */
    Optional<FdAccount> findByAccountNumber(String accountNumber);

    /**
     * Finds all accounts with a specific status.
     * WHY: Used by the Interest Calculation batch job to fetch all ACTIVE accounts.
     */
    List<FdAccount> findByStatus(AccountStatus status);

    /**
     * Finds accounts by their name, ignoring case and matching partial names.
     * WHY: For user-friendly searching.
     */
    List<FdAccount> findByAccountNameContainingIgnoreCase(String accountName);

    /**
     * Finds active accounts that are maturing on a specific date.
     * WHY: The primary query for the daily Maturity Processing batch job.
     */
    List<FdAccount> findByMaturityDateAndStatus(LocalDate maturityDate, AccountStatus status);

    /**
     * Finds accounts that will mature within a given date range.
     * WHY: Used for the Reporting feature ("accounts maturing in the next 7 days").
     */
    List<FdAccount> findByMaturityDateBetween(LocalDate startDate, LocalDate endDate);

    /**
     * Finds accounts that were closed within a given date range.
     * WHY: Used for the Reporting feature ("accounts closed this month").
     * NOTE: This relies on the `closedAt` field we just added.
     */
    List<FdAccount> findByStatusInAndClosedAtBetween(List<AccountStatus> statuses, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Finds all accounts where a given customer is an owner or co-owner.
     * This uses a JPQL (Java Persistence Query Language) query to join across tables.
     * WHY: Fulfills the requirement to search for accounts by Customer Number.
     */
    @Query("SELECT a FROM FdAccount a JOIN a.accountHolders h WHERE h.customerId = :customerId")
    List<FdAccount> findAccountsByCustomerId(@Param("customerId") String customerId);

    /**
     * Finds accounts that were created within a given date range.
     * WHY: For the "Accounts Created" report.
     */
    List<FdAccount> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);
}
    
    
    
  
    


