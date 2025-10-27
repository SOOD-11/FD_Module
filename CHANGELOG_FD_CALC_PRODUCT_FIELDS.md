# Changelog: Enhanced FD Account with Calculation and Product Fields

**Date:** October 27, 2025

## Summary
Updated the FD Module to capture and store additional fields from both the FD Calculation Service and Product & Pricing Service. This enhancement provides a more complete audit trail and eliminates the need for reverse calculations.

## Changes Made

### 1. FDCalculationResponse DTO (`FDCalculationResponse.java`)
**Added new fields to capture from the calculation service:**
- `principalAmount` - The actual principal amount (no longer needs to be reverse-calculated)
- `tenureValue` - The tenure value (e.g., 5)
- `tenureUnit` - The tenure unit (YEARS, MONTHS, DAYS)

**JSON Mapping:**
```java
@JsonProperty("principal_amount")
private BigDecimal principalAmount;

@JsonProperty("tenure_value")
private Integer tenureValue;

@JsonProperty("tenure_unit")
private String tenureUnit;
```

### 2. FdAccount Entity (`FdAccount.java`)
**Added new columns to the database table:**

From FD Calculation Service:
- `tenure_value` (Integer) - Stores the tenure value
- `tenure_unit` (String, max 20 chars) - Stores YEARS/MONTHS/DAYS

From Product Service:
- `currency` (String, max 3 chars) - Currency code (e.g., INR, USD)
- `interest_type` (String, max 20 chars) - Interest type (SIMPLE, COMPOUND)
- `compounding_frequency` (String, max 20 chars) - Compounding frequency (MONTHLY, QUARTERLY, YEARLY)

**Database Impact:**
These new columns will be automatically created by Hibernate/JPA when the application starts.

### 3. FDAccountServiceImpl (`FDAccountServiceImpl.java`)
**Updated the `createAccount` method:**

#### Before:
- Calculation service was called to get maturity details
- Principal amount was **reverse-calculated** from maturity value (unreliable)
- Product service was called later, only for balances and communications

#### After:
1. **Step 1:** Fetch calculation details (now includes principal_amount, tenure_value, tenure_unit)
2. **Step 2:** Fetch product details immediately (to get currency, interestType, compoundingFrequency)
3. **Step 3-4:** Generate account number and calculate term in months
4. **Step 5:** Use principal amount **directly from calculation response** (no reverse calculation)
5. **Step 6:** Set all fields on FdAccount entity:
   - From Calculation: calcId, resultId, apy, effectiveRate, payoutFreq, payoutAmount, category1Id, category2Id, **tenureValue, tenureUnit, principalAmount**
   - From Product: **currency, interestType, compoundingFrequency**
6. **Step 7-13:** Continue with account holder, transactions, save, balances, events, communications

**Removed:**
- `calculatePrincipalFromMaturity()` method - No longer needed since principal comes from calculation service

### 4. Enhanced Logging
Added comprehensive logging for new fields:
```java
log.info("Fetched calculation details: maturityValue={}, maturityDate={}, principalAmount={}", 
         calculation.getMaturityValue(), calculation.getMaturityDate(), calculation.getPrincipalAmount());
log.info("Product code: {}, Tenure: {} {}", 
         calculation.getProductCode(), calculation.getTenureValue(), calculation.getTenureUnit());
log.info("Fetched product details: currency={}, interestType={}, compoundingFrequency={}", 
         product.getCurrency(), product.getInterestType(), product.getCompoundingFrequency());
```

## API Contract Changes

### FD Calculation Service Response
The `/api/fd/calculations/{calcId}` endpoint now returns:
```json
{
  "maturity_value": 157969.75,
  "maturity_date": "2030-10-26",
  "apy": 9.5758,
  "effective_rate": 9.25,
  "payout_freq": null,
  "payout_amount": null,
  "calc_id": 41,
  "result_id": 41,
  "category1_id": "JR",
  "category2_id": "DY",
  "product_code": "FD001",
  "principal_amount": 100000,      // NEW
  "tenure_value": 5,               // NEW
  "tenure_unit": "YEARS"           // NEW
}
```

### Product Service Response
The `/api/products/{productCode}` endpoint returns (relevant fields):
```json
{
  "productCode": "FD001",
  "productName": "Fixed Deposit under 500000",
  "currency": "INR",               // NOW STORED
  "interestType": "COMPOUND",      // NOW STORED
  "compoundingFrequency": "QUARTERLY",  // NOW STORED
  ...
}
```

## Database Schema Changes

### New Columns in `fd_account` table:
```sql
ALTER TABLE fd_account ADD COLUMN tenure_value INTEGER;
ALTER TABLE fd_account ADD COLUMN tenure_unit VARCHAR(20);
ALTER TABLE fd_account ADD COLUMN currency VARCHAR(3);
ALTER TABLE fd_account ADD COLUMN interest_type VARCHAR(20);
ALTER TABLE fd_account ADD COLUMN compounding_frequency VARCHAR(20);
```

**Note:** These will be auto-created by JPA if using `spring.jpa.hibernate.ddl-auto=update`

## Benefits

1. **Accuracy:** Principal amount comes directly from calculation service (no reverse calculation errors)
2. **Completeness:** All product configuration is stored with the account
3. **Audit Trail:** Complete record of what was calculated and configured at account creation time
4. **Performance:** Single product service call instead of multiple calls
5. **Data Integrity:** Tenure information stored in original format (5 YEARS) instead of just months
6. **Reporting:** Currency, interest type, and compounding frequency available for reports without joining to product service

## Testing Recommendations

1. **Unit Tests:** Verify all new fields are properly mapped from DTOs to entity
2. **Integration Tests:** Test account creation with new calculation response format
3. **Database Tests:** Verify new columns are created and populated correctly
4. **API Tests:** Call POST /api/v1/accounts and verify response includes all new fields
5. **Regression Tests:** Ensure existing functionality still works with enhanced data model

## Migration Notes

For existing accounts in the database:
- New columns will have NULL values
- Consider running a data migration script to populate historical data if needed
- Or simply note that these fields are only available for new accounts created after this release

## Version Compatibility

- **Requires:** FD Calculation Service returning principal_amount, tenure_value, tenure_unit
- **Requires:** Product Service returning currency, interestType, compoundingFrequency
- **Backward Compatible:** If calculation service doesn't return new fields, principal will be null (requires null handling)
- **Recommended:** Update calculation service first, then deploy this change

## Related Files Modified

1. `src/main/java/com/example/demo/dto/FDCalculationResponse.java`
2. `src/main/java/com/example/demo/entities/FdAccount.java`
3. `src/main/java/com/example/demo/service/impl/FDAccountServiceImpl.java`

## Rollback Plan

If needed to rollback:
1. Revert code changes to previous version
2. New database columns can remain (will just be unused)
3. No data loss - existing functionality preserved
