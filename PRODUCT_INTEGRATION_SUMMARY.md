# Product Integration Implementation Summary

## Overview
Successfully integrated Product and Pricing Service with FD Module to enable product-driven configuration for account creation, balance types, communications, role validation, and transaction validation.

## Architecture Changes

### 1. External Service Integration

#### Product and Pricing Service
- **Base URL**: `http://localhost:8080` (configurable)
- **Endpoint**: `/api/products/{productCode}`
- **Purpose**: Fetch complete product configuration including:
  - Product balances (FD_PRINCIPAL, FD_INTEREST, PENALTY)
  - Product roles (OWNER, CO_OWNER, etc.)
  - Product transactions (DEPOSIT, WITHDRAWAL, INTEREST_ACCRUED)
  - Product communications (COMM_OPENING, COMM_MONTHLY_STATEMENT)
  - Product charges (PEN-H-{suffix}, PEN-L-{suffix})
  - Product rules (MIN/MAX amounts, interest bonuses)

### 2. New Components Created

#### DTOs
1. **ProductDetailsResponse.java**
   - Complete product response mapping
   - Nested classes for all product sub-entities:
     - ProductRule
     - ProductCharge
     - ProductRole
     - ProductTransaction
     - ProductBalance
     - ProductCommunication
     - ProductInterest

2. **CommunicationEvent.java**
   - Generic communication event for Kafka
   - Supports EMAIL, SMS, PUSH_NOTIFICATION, IN_APP channels
   - Template variable substitution support

#### Entities
1. **FdAccountBalance.java**
   - Tracks multiple balance types per account
   - Fields: balanceType, balanceAmount, isActive
   - Linked to FdAccount via @ManyToOne

#### Repositories
1. **FdAccountBalanceRepository.java**
   - Find balances by account number
   - Find specific balance type for account

#### Services
1. **ProductService.java**
   - `getProductDetails(productCode)` - Fetch product configuration
   - `isRoleAllowed(productCode, roleType)` - Validate role permissions
   - `isTransactionAllowed(productCode, transactionType)` - Validate transactions
   - `getPenaltyCharge(productCode, completionPercentage)` - Get penalty rates

#### Configuration
1. **ProductServiceConfigProperties.java**
   - Configuration properties for product service URL
   - Helper method to build product URLs

### 3. Updated Components

#### FDAccountServiceImpl.java

**Enhanced createAccount Method:**
```java
@Override
public FDAccountView createAccount(CreateFDAccountRequest request, String customerId) {
    // Step 1: Fetch calculation details
    FDCalculationResponse calculation = fdCalculationService.getCalculation(request.calcId());
    
    // Step 2: Fetch product details
    ProductDetailsResponse product = productService.getProductDetails(calculation.getProductCode());
    
    // Step 3: Create FD account entity
    // ... account creation logic ...
    
    // Step 4: Save account
    FdAccount savedAccount = fdAccountRepository.save(fdAccount);
    
    // Step 5: Create balance types based on product configuration
    createAccountBalances(savedAccount, product, principalAmount);
    
    // Step 6: Publish account created event
    kafkaProducerService.sendAccountCreatedEvent(event);
    
    // Step 7: Send communication events based on product templates
    sendCommunicationEvents(savedAccount, product, customerId, "COMM_OPENING");
    
    return mapToView(savedAccount);
}
```

**New Helper Methods:**

1. **createAccountBalances()**
   - Creates balance entries for each product balance type
   - Sets initial amounts:
     - FD_PRINCIPAL = principal amount
     - FD_INTEREST = 0
     - PENALTY = 0

2. **sendCommunicationEvents()**
   - Sends Kafka events for configured communications
   - Filters by event type (COMM_OPENING, COMM_MONTHLY_STATEMENT, etc.)
   - Substitutes template variables:
     - ${CUSTOMER_NAME}
     - ${PRODUCT_NAME}
     - ${ACCOUNT_NUMBER}
     - ${DATE}
     - ${PRINCIPAL_AMOUNT}
     - ${MATURITY_DATE}

**Enhanced addRoleToAccount Method:**
```java
@Override
public FDAccountView addRoleToAccount(String accountNumber, AddAccountHolderRequest request) {
    // Validate role against product configuration
    boolean roleAllowed = productService.isRoleAllowed(account.getProductCode(), roleType);
    
    if (!roleAllowed) {
        throw new IllegalArgumentException("Role not configured for product");
    }
    
    // Add role to account
    // ...
}
```

**Enhanced performEarlyWithdrawal Method:**
```java
@Transactional
public FDAccountView performEarlyWithdrawal(String accountNumber, EarlyWithdrawlRequest request) {
    // Validate WITHDRAWAL transaction is allowed
    boolean withdrawalAllowed = productService.isTransactionAllowed(
        account.getProductCode(), 
        TransactionType.PREMATURE_WITHDRAWAL.name()
    );
    
    if (!withdrawalAllowed) {
        throw new IllegalArgumentException("Withdrawal not allowed for product");
    }
    
    // Perform withdrawal
    // ...
}
```

**Enhanced getPrematureWithdrawalInquiry Method:**
```java
@Override
public PrematureWithdrawalInquiryResponse getPrematureWithdrawalInquiry(String accountNumber) {
    // Calculate completion percentage
    double completionPercentage = (daysActive * 100.0) / totalTermDays;
    
    // Get penalty charge from product
    // PEN-H-{suffix} if < 50% completion
    // PEN-L-{suffix} if >= 50% completion
    ProductCharge penaltyCharge = productService.getPenaltyCharge(
        account.getProductCode(), 
        completionPercentage
    );
    
    // Calculate penalty using product-configured rate
    BigDecimal penaltyRate = new BigDecimal(penaltyCharge.getAmount().toString());
    
    // Apply penalty (PERCENTAGE or FLAT based on calculationType)
    // ...
}
```

#### KafkaProducerService.java
- Added `sendCommunicationEvent()` method
- New topic: `fd.communication`

#### application.properties
```properties
# Product and Pricing Service Configuration
product.service.base-url=http://localhost:8080
product.service.path=/api/products
```

## Key Features Implemented

### 1. Dynamic Balance Type Creation
- ✅ Reads product balance configuration
- ✅ Creates balance entries for: FD_PRINCIPAL, FD_INTEREST, PENALTY
- ✅ Initializes amounts appropriately
- ✅ Stores in `fd_account_balances` table

### 2. Product-Driven Communications
- ✅ Reads product communication templates
- ✅ Sends Kafka events for configured communications
- ✅ Supports multiple channels: EMAIL, SMS, PUSH_NOTIFICATION, IN_APP
- ✅ Template variable substitution
- ✅ Event-driven (COMM_OPENING, COMM_MONTHLY_STATEMENT, etc.)

### 3. Role Validation
- ✅ Validates roles against product configuration
- ✅ Prevents adding unauthorized roles
- ✅ Checks `productRoles` array from product service

### 4. Transaction Validation
- ✅ Validates transaction types against product
- ✅ Prevents unauthorized transactions
- ✅ Checks `productTransactions` array

### 5. Product-Based Penalty Calculation
- ✅ Uses product charges (PEN-H-{suffix}, PEN-L-{suffix})
- ✅ Dynamic penalty based on completion percentage:
  - < 50% completion → PEN-H (high penalty)
  - ≥ 50% completion → PEN-L (low penalty)
- ✅ Supports PERCENTAGE and FLAT calculation types

## Data Flow

### Account Creation Flow
```
1. User Request → Controller (JWT + calcId)
   ↓
2. Extract customerId from JWT token
   ↓
3. Call FD Calculation Service (get maturity details)
   ↓
4. Call Product Service (get product configuration)
   ↓
5. Create FD Account Entity
   ↓
6. Save to Database
   ↓
7. Create Balance Types (from product.productBalances)
   ↓
8. Publish Account Created Event (Kafka)
   ↓
9. Send Communication Events (from product.productCommunications)
   ↓
10. Return Response
```

### Role Assignment Flow
```
1. User Request → addRole(accountNumber, roleRequest)
   ↓
2. Fetch Account
   ↓
3. Call Product Service → isRoleAllowed(productCode, roleType)
   ↓
4. If ALLOWED → Add role
   ↓
5. If NOT ALLOWED → Throw exception
```

### Withdrawal Flow
```
1. User Request → performEarlyWithdrawal(accountNumber)
   ↓
2. Fetch Account
   ↓
3. Call Product Service → isTransactionAllowed(productCode, "WITHDRAWAL")
   ↓
4. If ALLOWED → Continue
   ↓
5. Calculate Completion % = (daysActive / totalDays) * 100
   ↓
6. Call Product Service → getPenaltyCharge(productCode, completionPercentage)
   ↓
7. Apply Penalty (PEN-H-{suffix} or PEN-L-{suffix})
   ↓
8. Process Withdrawal
```

## Example Product Configuration

```json
{
  "productCode": "FD001",
  "productName": "Fixed Deposit under 500000",
  "productType": "FIXED_DEPOSIT",
  "productBalances": [
    {"balanceType": "FD_PRINCIPAL", "isActive": true},
    {"balanceType": "FD_INTEREST", "isActive": true},
    {"balanceType": "PENALTY", "isActive": true}
  ],
  "productRoles": [
    {"roleType": "OWNER", "roleName": "OWNER"}
  ],
  "productTransactions": [
    {"transactionType": "DEPOSIT"},
    {"transactionType": "WITHDRAWAL"},
    {"transactionType": "INTEREST_ACCRUED"}
  ],
  "productCharges": [
    {"chargeCode": "PEN-H-001", "chargeType": "PENALTY", "calculationType": "PERCENTAGE", "amount": 1.0},
    {"chargeCode": "PEN-L-001", "chargeType": "PENALTY", "calculationType": "PERCENTAGE", "amount": 0.5}
  ],
  "productCommunications": [
    {
      "event": "COMM_OPENING",
      "communicationType": "ALERT",
      "channel": "SMS",
      "template": "Dear ${CUSTOMER_NAME}, your ${PRODUCT_NAME} account ${ACCOUNT_NUMBER} was opened on ${DATE}."
    },
    {
      "event": "COMM_MONTHLY_STATEMENT",
      "communicationType": "STATEMENT",
      "channel": "EMAIL",
      "template": "Your monthly statement for ${ACCOUNT_NUMBER} is ready."
    }
  ]
}
```

## Testing Checklist

### Account Creation
- [ ] Account created with correct product code
- [ ] Balance types created (FD_PRINCIPAL, FD_INTEREST, PENALTY)
- [ ] Principal balance initialized correctly
- [ ] Account created event published
- [ ] Communication event(s) sent for COMM_OPENING

### Role Assignment
- [ ] Valid role (OWNER) can be added
- [ ] Invalid role throws exception with message
- [ ] Error message references product code

### Early Withdrawal
- [ ] Transaction validation performed
- [ ] Withdrawal allowed if product permits
- [ ] Withdrawal blocked if product doesn't permit
- [ ] Penalty calculated using PEN-H-{suffix} for < 50% completion
- [ ] Penalty calculated using PEN-L-{suffix} for >= 50% completion
- [ ] Percentage penalty applied correctly
- [ ] Flat penalty applied correctly (if configured)

## Configuration Required

### application.properties
```properties
# Product and Pricing Service
product.service.base-url=http://localhost:8080
product.service.path=/api/products
```

### Required Services Running
1. **Auth Service** - `http://localhost:3020` (JWT public key)
2. **FD Calculation Service** - `http://localhost:4030` (Calculation details)
3. **Product and Pricing Service** - `http://localhost:8080` (Product configuration)

### Kafka Topics
- `fd.account.created` - Account creation events
- `fd.account.matured` - Account maturity events
- `fd.account.closed` - Account closure events
- `fd.communication` - **NEW** Communication events

## Database Changes

### New Table: fd_account_balances
```sql
CREATE TABLE fd_account_balances (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_number VARCHAR(255),
    balance_type VARCHAR(50) NOT NULL,
    balance_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    FOREIGN KEY (account_number) REFERENCES fd_account(accountNumber)
);
```

## Benefits

1. **Product-Driven Configuration**
   - No code changes needed for new balance types
   - No code changes for new roles
   - No code changes for new transaction types

2. **Flexible Penalty Structure**
   - Different penalties for different completion stages
   - Configurable per product
   - Supports both percentage and flat penalties

3. **Enhanced Communication**
   - Template-based communications
   - Multiple channel support
   - Event-driven architecture

4. **Better Validation**
   - Product configuration enforced at runtime
   - Clear error messages for invalid operations
   - Reduced risk of misconfiguration

5. **Audit Trail**
   - All communications logged via Kafka
   - Balance changes tracked
   - Product configuration versioning (via product service)

## Future Enhancements

1. Cache product configuration to reduce API calls
2. Add product configuration change notifications
3. Support for custom balance calculation rules
4. Enhanced template engine with conditional logic
5. Multi-language communication templates
6. Product-driven interest calculation rules
