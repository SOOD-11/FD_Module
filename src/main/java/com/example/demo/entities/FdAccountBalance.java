package com.example.demo.entities;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing different balance types for an FD account
 * Examples: FD_PRINCIPAL, FD_INTEREST, PENALTY
 */
@Entity
@Table(name = "fd_account_balances")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FdAccountBalance {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "account_number", referencedColumnName = "account_number")
    private FdAccount fdAccount;
    
    @Column(name = "balance_type", nullable = false, length = 50)
    private String balanceType; // FD_PRINCIPAL, FD_INTEREST, PENALTY
    
    @Column(name = "balance_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal balanceAmount = BigDecimal.ZERO;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    // Note: Timestamps should be set explicitly in service layer using IClockService
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
