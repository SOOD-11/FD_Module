package com.example.demo.entities;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.example.demo.enums.AccountStatus;
import com.example.demo.enums.MaturityInstruction;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.AllArgsConstructor;
import lombok.Data;

import lombok.NoArgsConstructor;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data


public class FdAccount {

	

	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_identifier")
    private Long id; // System-generated internal identifier

    @Column(name = "account_number", unique = true, nullable = false, length = 10)
    private String accountNumber; // User-facing account number

    @Column(name = "account_name", nullable = false)
    private String accountName;

    @Column(name = "product_code", nullable = false)
    private String productCode; // From Product and Pricing Module

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccountStatus status;

    @Column(name = "term_in_months", nullable = false)
    private Integer termInMonths;

    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate; // e.g., 7.50

    @Column(name = "principal_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal principalAmount;

    @Column(name = "maturity_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal maturityAmount;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "maturity_instruction", nullable = false)
    private MaturityInstruction maturityInstruction;
    
    @Column(name = "payout_account_number")
    private String payoutAccountNumber; // Linked account for payout on maturity

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    // --- Relationships ---

    @OneToMany(mappedBy = "fdAccount", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Accountholder> accountHolders;

    @OneToMany(mappedBy = "fdAccount", cascade = CascadeType.ALL)
    private List<FdTransaction> transactions;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    
    
    
    public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getAccountNumber() {
		return accountNumber;
	}

	public void setAccountNumber(String accountNumber) {
		this.accountNumber = accountNumber;
	}

	public String getAccountName() {
		return accountName;
	}

	public void setAccountName(String accountName) {
		this.accountName = accountName;
	}

	public String getProductCode() {
		return productCode;
	}

	public void setProductCode(String productCode) {
		this.productCode = productCode;
	}

	public AccountStatus getStatus() {
		return status;
	}

	public void setStatus(AccountStatus status) {
		this.status = status;
	}

	public Integer getTermInMonths() {
		return termInMonths;
	}

	public void setTermInMonths(Integer termInMonths) {
		this.termInMonths = termInMonths;
	}

	public BigDecimal getInterestRate() {
		return interestRate;
	}

	public void setInterestRate(BigDecimal interestRate) {
		this.interestRate = interestRate;
	}

	public BigDecimal getPrincipalAmount() {
		return principalAmount;
	}

	public void setPrincipalAmount(BigDecimal principalAmount) {
		this.principalAmount = principalAmount;
	}

	public BigDecimal getMaturityAmount() {
		return maturityAmount;
	}

	public void setMaturityAmount(BigDecimal maturityAmount) {
		this.maturityAmount = maturityAmount;
	}

	public LocalDate getEffectiveDate() {
		return effectiveDate;
	}

	public void setEffectiveDate(LocalDate effectiveDate) {
		this.effectiveDate = effectiveDate;
	}

	public LocalDate getMaturityDate() {
		return maturityDate;
	}

	public void setMaturityDate(LocalDate maturityDate) {
		this.maturityDate = maturityDate;
	}

	public MaturityInstruction getMaturityInstruction() {
		return maturityInstruction;
	}

	public void setMaturityInstruction(MaturityInstruction maturityInstruction) {
		this.maturityInstruction = maturityInstruction;
	}

	public String getPayoutAccountNumber() {
		return payoutAccountNumber;
	}

	public void setPayoutAccountNumber(String payoutAccountNumber) {
		this.payoutAccountNumber = payoutAccountNumber;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

	public LocalDateTime getClosedAt() {
		return closedAt;
	}

	public void setClosedAt(LocalDateTime closedAt) {
		this.closedAt = closedAt;
	}

	public List<Accountholder> getAccountHolders() {
		return accountHolders;
	}

	public void setAccountHolders(List<Accountholder> accountHolders) {
		this.accountHolders = accountHolders;
	}

	public List<FdTransaction> getTransactions() {
		return transactions;
	}

	public void setTransactions(List<FdTransaction> transactions) {
		this.transactions = transactions;
	}
}



