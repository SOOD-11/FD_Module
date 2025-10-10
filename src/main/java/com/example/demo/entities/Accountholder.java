package com.example.demo.entities;

import java.math.BigDecimal;

import com.example.demo.enums.RoleType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;

import lombok.NoArgsConstructor;


@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data


@Table(name = "account_holders", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"fd_account_id", "customer_id", "role_type"})
})
public class Accountholder {
	
	
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fd_account_id", nullable = false)
    private FdAccount fdAccount;

    @Column(name = "customer_id", nullable = false)
    private String customerId; // From Customer Module

    @Enumerated(EnumType.STRING)
    @Column(name = "role_type", nullable = false)
    private RoleType roleType;

    @Column(name = "ownership_percentage", precision = 5, scale = 2)
    private BigDecimal ownershipPercentage; // e.g., 50.00 for a 50% owner

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public FdAccount getFdAccount() {
		return fdAccount;
	}

	public void setFdAccount(FdAccount fdAccount) {
		this.fdAccount = fdAccount;
	}

	public String getCustomerId() {
		return customerId;
	}

	public void setCustomerId(String customerId) {
		this.customerId = customerId;
	}

	public RoleType getRoleType() {
		return roleType;
	}

	public void setRoleType(RoleType roleType) {
		this.roleType = roleType;
	}

	public BigDecimal getOwnershipPercentage() {
		return ownershipPercentage;
	}

	public void setOwnershipPercentage(BigDecimal ownershipPercentage) {
		this.ownershipPercentage = ownershipPercentage;
	}


}



