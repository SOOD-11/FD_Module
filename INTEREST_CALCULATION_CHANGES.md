# Interest Calculation and Penalty Cap Changes

## Overview
This document describes the major changes made to the FD Module to implement period-based compound interest calculation and penalty capping logic.

## Changes Made

### 1. Interest Calculation - Compound Interest Based on Compounding Frequency

**File**: `src/main/java/com/example/demo/config/InterestCalculationJobConfig.java`

#### Previous Behavior
- Interest was calculated **daily** using simple interest formula
- Formula: `principal * (interestRate / 100 / 365)`
- Ran every day at 00:00 UTC
- Created daily INTEREST_ACCRUAL transactions

#### New Behavior
- Interest is calculated only at **period end** based on `compounding_frequency` field
- Supports three frequencies: **QUARTERLY**, **MONTHLY**, **YEARLY**
- Uses **compound interest formula** (adjusted for frequency):
  - **MONTHLY**: `(1 + effective_rate/(12*100)) * (interest_accrued + principal)`
  - **QUARTERLY**: `(1 + effective_rate/(4*100)) * (interest_accrued + principal)`
  - **YEARLY**: `(1 + effective_rate/100) * (interest_accrued + principal)`
- Tracks accrued interest in `fd_account_balances` table with `balance_type='FD_INTEREST'`

#### Key Implementation Details

**Period End Detection**:
- `MONTHLY`: **1st of every month** at 12:00 AM (e.g., Feb 1, Mar 1, Apr 1, etc.)
- `QUARTERLY`: **1st of every quarter** - January 1, April 1, July 1, October 1
- `YEARLY`: **1st of every year** - January 1st only

**Important Notes**:
- Uses **fixed calendar dates**, not relative to account effective_date
- Account must be active (today > effective_date) for compounding to occur
- Standardized approach simplifies operations and customer communication
- All compounding happens at 12:00 AM UTC when the batch job runs

**Compound Interest Calculation**:
```java
// Get current interest accrued from fd_account_balances
BigDecimal currentInterestAccrued = balanceRepository
    .findByFdAccount_AccountNumberAndBalanceType(accountNumber, "FD_INTEREST")
    .getBalanceAmount();

// Determine period divisor based on compounding frequency
BigDecimal periodDivisor;
switch (compoundingFrequency) {
    case "MONTHLY": periodDivisor = 12; break;
    case "QUARTERLY": periodDivisor = 4; break;
    case "YEARLY": periodDivisor = 1; break;
}

// Apply compound formula: 1 + (effective_rate / (period_divisor * 100))
BigDecimal rateComponent = effective_rate / (periodDivisor * 100);
BigDecimal compoundFactor = 1 + rateComponent;
BigDecimal newTotalAmount = compoundFactor * (interest_accrued + principal);

// Interest earned this period
BigDecimal interestEarned = newTotalAmount - (currentInterestAccrued + principal);

// Update FD_INTEREST balance
interestBalance.setBalanceAmount(currentInterestAccrued + interestEarned);
```

**Transaction Creation**:
- Transactions are created only at period end (not daily)
- Transaction type: `INTEREST_ACCRUAL`
- Description includes compounding frequency: "QUARTERLY compound interest accrual"
- Alert is sent to customer via Kafka (including email and phone)

**Database Updates**:
- `fd_account_balances` table: FD_INTEREST balance is created/updated
- `fd_transactions` table: Transaction record created at each compounding period

### 2. Penalty Capping in Premature Withdrawal

**File**: `src/main/java/com/example/demo/service/impl/FDAccountServiceImpl.java`

#### Previous Behavior
- Penalty was calculated based on product configuration or 1% default
- No cap on penalty amount
- Could potentially exceed actual interest earned

#### New Behavior
- Penalty is calculated as before
- **Penalty is capped at actual interest accrued** from `fd_account_balances`
- Formula: `penalty = min(calculated_penalty, interest_accrued)`

#### Key Implementation Details

**Penalty Cap Logic**:
```java
// Calculate penalty (existing logic)
BigDecimal penaltyAmount = /* calculation based on product config */;

// Get actual interest accrued from fd_account_balances
FdAccountBalance interestBalance = balanceRepository
    .findByFdAccount_AccountNumberAndBalanceType(accountNumber, "FD_INTEREST");

BigDecimal actualInterestAccrued = (interestBalance != null) 
    ? interestBalance.getBalanceAmount() 
    : BigDecimal.ZERO;

// Cap penalty at interest accrued
if (penaltyAmount.compareTo(actualInterestAccrued) > 0) {
    log.info("Penalty {} exceeds interest accrued {}. Capping penalty at interest accrued.", 
            penaltyAmount, actualInterestAccrued);
    penaltyAmount = actualInterestAccrued;
}
```

**Logging**:
- Log message when penalty is capped for audit trail
- Helps track cases where calculated penalty would have exceeded interest

## Impact on Existing Data

### Migration Considerations

1. **Existing FD Accounts**: 
   - Will start using compound interest from next period end
   - Historical daily interest transactions remain unchanged
   - Need to initialize `fd_account_balances` with current interest if not already tracked

2. **FD Account Balances Table**:
   - Ensure all active accounts have FD_INTEREST balance record
   - System will create new records if missing during first compound period

3. **Premature Withdrawal**:
   - All future withdrawals will use capped penalty
   - Past withdrawals remain unchanged

## Testing Recommendations

### Test Scenarios for Interest Calculation

1. **Monthly Compounding**:
   - Create account with any effective_date (e.g., 2024-01-15)
   - Set compounding_frequency = MONTHLY
   - Verify interest calculated only on 1st of each month (Feb 1, Mar 1, Apr 1, etc.)
   - Account created on Jan 15, first compounding on Feb 1
   - Verify FD_INTEREST balance updated correctly

2. **Quarterly Compounding**:
   - Create account with any effective_date (e.g., 2024-02-20)
   - Set compounding_frequency = QUARTERLY
   - Verify interest calculated only on Jan 1, Apr 1, Jul 1, Oct 1
   - Account created on Feb 20, first compounding on Apr 1
   - Second compounding on Jul 1, third on Oct 1

3. **Yearly Compounding**:
   - Create account with any effective_date (e.g., 2024-06-10)
   - Set compounding_frequency = YEARLY
   - Verify interest calculated only on Jan 1 of each year
   - Account created on Jun 10, 2024, first compounding on Jan 1, 2025
   - Second compounding on Jan 1, 2026

4. **Compound Interest Accumulation**:
   - Verify that interest compounds on previous interest
   - Example: Principal 100,000, Effective Rate 12% (annual), Quarterly compounding
     - Quarterly rate = 12 / (4 * 100) = 0.03 (3% per quarter)
     - Apr 1: (1.03) × (0 + 100,000) = 103,000, Interest = 3,000
     - Jul 1: (1.03) × (3,000 + 100,000) = 106,090, Interest = 3,090
     - Oct 1: (1.03) × (6,090 + 100,000) = 109,272.70, Interest = 3,182.70
     - Jan 1: (1.03) × (9,272.70 + 100,000) = 112,550.88, Interest = 3,278.18

5. **Account Created on Compounding Date**:
   - Create account on Jan 1 (compounding date)
   - Verify no compounding on creation date (must be after effective_date)
   - First compounding should occur on next compounding date

### Test Scenarios for Penalty Capping

1. **Penalty Less Than Interest**:
   - Account with interest_accrued = 10,000
   - Calculated penalty = 5,000
   - Expected: Penalty charged = 5,000 (no capping)

2. **Penalty Exceeds Interest**:
   - Account with interest_accrued = 5,000
   - Calculated penalty = 8,000
   - Expected: Penalty charged = 5,000 (capped)
   - Verify log message appears

3. **Zero Interest Accrued**:
   - Account with no interest (withdrawn immediately)
   - Calculated penalty = 2,000
   - Expected: Penalty charged = 0 (capped at zero)

4. **No Interest Balance Record**:
   - Account without FD_INTEREST record
   - Expected: actualInterestAccrued = 0, penalty capped at 0

## Database Schema Dependencies

### Tables Used

1. **fd_account**:
   - `compounding_frequency` - QUARTERLY, MONTHLY, YEARLY
   - `effective_rate` - Rate used for compounding (decimal, e.g., 10.5)
   - `effective_date` - Start date for period calculations
   - `principal_amount` - Principal for compound calculation

2. **fd_account_balances**:
   - `balance_type` - 'FD_INTEREST' for accrued interest tracking
   - `balance_amount` - Current accrued interest amount
   - `is_active` - Should be true for active balances
   - `created_at`, `updated_at` - Timestamps using logical time

3. **fd_transactions**:
   - Transaction records created at each compounding period
   - `transaction_type` = INTEREST_ACCRUAL
   - `amount` - Interest earned in the period
   - `description` - Includes compounding frequency

## Configuration

### Logical Time Scheduler
- Interest calculation job still runs **daily at 00:00 UTC**
- However, transactions are created only on period end dates
- Non-period-end dates: Job reads accounts but processor returns null (skips)

### Kafka Events
- Alert events sent for each interest accrual transaction
- Includes customer email and phone number
- Topic: `alert`
- Alert type: Transaction alert

## Business Logic Summary

### Interest Calculation Formula

**Previous (Daily Simple Interest)**:
```
Daily Interest = Principal × (Interest Rate / 100 / 365)
```

**New (Period-based Compound Interest)**:
```
MONTHLY:
  Compound Factor = 1 + (Effective Rate / (12 * 100))
  New Total = Compound Factor × (Previous Interest + Principal)
  Period Interest = New Total - (Previous Interest + Principal)

QUARTERLY:
  Compound Factor = 1 + (Effective Rate / (4 * 100))
  New Total = Compound Factor × (Previous Interest + Principal)
  Period Interest = New Total - (Previous Interest + Principal)

YEARLY:
  Compound Factor = 1 + (Effective Rate / 100)
  New Total = Compound Factor × (Previous Interest + Principal)
  Period Interest = New Total - (Previous Interest + Principal)
```

### Penalty Calculation

**Formula**:
```
Calculated Penalty = Based on product configuration (percentage or flat)
Final Penalty = MIN(Calculated Penalty, Actual Interest Accrued)
```

## Notes

1. **Payout Frequency**: The current implementation focuses on compounding frequency. The `payout_freq` field can be implemented in future iterations to handle interest payouts separately from compounding.

2. **Effective Rate**: The system now uses `effective_rate` field instead of `interest_rate` for compounding calculations. Ensure product configurations populate this field correctly.

3. **Backward Compatibility**: Accounts without `compounding_frequency` set will be skipped by the processor (returns null).

4. **Transaction Alerts**: Every interest accrual transaction triggers a customer alert with email and phone, maintaining consistency with other transaction types.

## Verification Steps

After deployment:

1. Check logs for "Processed account" messages - should only appear on period end dates
2. Query `fd_account_balances` to verify FD_INTEREST balances are being created/updated
3. Verify `fd_transactions` table shows interest transactions only on period end dates
4. Test premature withdrawal API and verify penalty capping in logs
5. Monitor Kafka topics for alert events on interest accrual dates

---

**Date**: January 2025  
**Version**: 1.0  
**Author**: FD Module Development Team
