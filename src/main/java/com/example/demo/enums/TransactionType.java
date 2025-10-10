package com.example.demo.enums;



public enum TransactionType {
    PRINCIPAL_DEPOSIT,
    INTEREST_ACCRUAL,       // Interest calculated but not paid out
    INTEREST_PAYOUT,        // Interest transferred to another account
    INTEREST_CAPITALIZATION,// Interest added to principal
    PREMATURE_WITHDRAWAL,
    PENALTY_DEBIT,
    MATURITY_PAYOUT,
    RENEWAL_DEPOSIT        // Transaction for renewing the FD
}

