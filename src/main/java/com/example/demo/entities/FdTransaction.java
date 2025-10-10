package com.example.demo.entities;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.demo.enums.TransactionType;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@Table(name = "fd_transactions")
public class FdTransaction {
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fd_account_id", nullable = false)
    private FdAccount fdAccount;

    @Column(name = "transaction_reference", unique = true, nullable = false)
    private String transactionReference; // A unique ID for the transaction

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @Column(name = "description", length = 255)
    private String description;

	public void setId(Long id) {
		this.id = id;
	}

	public void setFdAccount(FdAccount fdAccount) {
		this.fdAccount = fdAccount;
	}

	public void setTransactionReference(String transactionReference) {
		this.transactionReference = transactionReference;
	}

	public void setTransactionType(TransactionType transactionType) {
		this.transactionType = transactionType;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public void setTransactionDate(LocalDateTime transactionDate) {
		this.transactionDate = transactionDate;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	

    // Getters and Setters
}

// --- Enum for FDTransaction ---

	
	


