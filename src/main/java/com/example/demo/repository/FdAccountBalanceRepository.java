package com.example.demo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.entities.FdAccountBalance;

@Repository
public interface FdAccountBalanceRepository extends JpaRepository<FdAccountBalance, Long> {
    
    /**
     * Find all balances for a specific FD account
     */
    List<FdAccountBalance> findByFdAccount_AccountNumber(String accountNumber);
    
    /**
     * Find specific balance type for an account
     */
    FdAccountBalance findByFdAccount_AccountNumberAndBalanceType(String accountNumber, String balanceType);
}
