package com.example.demo.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.entities.FdTransaction;
import com.example.demo.enums.TransactionType;


@Repository
public interface FdTransactionRepository extends JpaRepository<FdTransaction, Long> {

    /**
     * Finds all transactions for a specific account, ordered by most recent first.
     * WHY: To get the complete transaction history for an account.
     */
    List<FdTransaction> findByFdAccount_AccountNumberOrderByTransactionDateDesc(String accountNumber);

    /**
     * Finds transactions for a specific account within a given date range.
     * WHY: The core query for generating monthly or quarterly account statements.
     */
    List<FdTransaction> findByFdAccount_AccountNumberAndTransactionDateBetweenOrderByTransactionDateDesc(
            String accountNumber,
            LocalDateTime startDate,
            LocalDateTime endDate
    );
    
    /**
     * An even more specific query for finding transactions of a certain type within a date range.
     * WHY: Useful for specific inquiries, like "show all interest payments for this account last year".
     */
    List<FdTransaction> findByFdAccount_AccountNumberAndTransactionTypeAndTransactionDateBetweenOrderByTransactionDateDesc(
            String accountNumber,
            TransactionType transactionType,
            LocalDateTime startDate,
            LocalDateTime endDate
    );
}