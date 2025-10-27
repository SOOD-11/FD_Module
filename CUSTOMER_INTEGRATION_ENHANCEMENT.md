# Customer Integration Enhancement - JWT Email to Customer Number Mapping

**Date:** October 27, 2025

## Summary
Updated the FD Module to integrate with Customer Service API. Instead of using customerId directly from JWT token, the system now:
1. Extracts **email** from JWT token's 'sub' claim
2. Calls Customer Service API to fetch customer profile
3. Uses **customerNumber** (business identifier) from customer profile as the customerId in FD accounts

## Changes Made

### 1. Created CustomerProfileResponse DTO (`CustomerProfileResponse.java`)
**Purpose:** Map the complete response from Customer Service API

**Key Fields:**
- `customerId` - UUID from customer service (not used in FD)
- `customerNumber` - **Business identifier** (e.g., CUST-20251024-000001) - **THIS IS WHAT WE USE**
- `email` - Customer email
- `firstName`, `lastName`, `middleName` - Name components
- `phoneNumber` - Contact information
- `addressLine1`, `city`, `state`, `country`, `postalCode` - Address details
- `maskedAadhar`, `maskedPan` - Masked identification
- `customerStatus`, `kycStatus` - Status fields

**JSON Mapping Example:**
```json
{
  "customerId": "9568cf52-16a1-430c-9eae-a27af667ad37",
  "customerNumber": "CUST-20251024-000001",  // <- WE USE THIS
  "email": "probott@nexabank.com",
  "firstName": "probott",
  "lastName": "op",
  "customerStatus": "ACTIVE",
  "kycStatus": "PENDING"
}
```

### 2. Created CustomerService (`CustomerService.java`)
**Purpose:** Service to interact with Customer Profile Management API

**Configuration:**
- Base URL: `http://localhost:1005`
- Endpoint: `/api/profiles/email/{email}`

**Methods:**

#### `getCustomerByEmail(String email)`
- Calls Customer Service API with email
- Returns complete CustomerProfileResponse
- Logs customer details for debugging
- Throws RuntimeException if customer not found

#### `getCustomerNumberByEmail(String email)`
- Convenience method that only returns customerNumber
- Used by FD Account Controller
- Throws exception if customerNumber is null

**Error Handling:**
- Catches all exceptions from REST call
- Logs error with email for debugging
- Throws RuntimeException with descriptive message

### 3. Updated FdAccountController (`FdAccountController.java`)

#### Constructor Changes
**Before:**
```java
private FDAccountService fdAccountService;

public FdAccountController(FDAccountService fdAccountService) {
    this.fdAccountService = fdAccountService;
}
```

**After:**
```java
private FDAccountService fdAccountService;
private CustomerService customerService;

public FdAccountController(FDAccountService fdAccountService, CustomerService customerService) {
    this.fdAccountService = fdAccountService;
    this.customerService = customerService;
}
```

#### createAccount Method Changes

**Before:**
```java
@AuthenticationPrincipal Jwt jwt) {
    // Extract customer ID from JWT token (sub claim)
    String customerId = jwt.getSubject();
    
    FDAccountView createdAccount = fdAccountService.createAccount(request, customerId);
    return new ResponseEntity<>(createdAccount, HttpStatus.CREATED);
}
```

**After:**
```java
@AuthenticationPrincipal Jwt jwt) {
    // Extract email from JWT token (sub claim)
    String email = jwt.getSubject();
    
    // Fetch customer profile from Customer Service using email
    // and get customerNumber (e.g., CUST-20251024-000001)
    String customerNumber = customerService.getCustomerNumberByEmail(email);
    
    FDAccountView createdAccount = fdAccountService.createAccount(request, customerNumber);
    return new ResponseEntity<>(createdAccount, HttpStatus.CREATED);
}
```

#### Documentation Updates

**Updated @Tag Description:**
- Changed: "contains customer ID in 'sub' claim" → "contains customer email in 'sub' claim"
- Added step: "System calls Customer Service to get customer profile and customerNumber"
- Updated authentication description to mention email extraction

**Updated @Operation Description:**
- Added Customer Service as first external service call
- Updated JWT requirements to specify email in 'sub' claim
- Updated "What Gets Created" to mention customerNumber instead of customerId from JWT

## Flow Diagram

### Old Flow
```
1. User Login → JWT Token (sub: customerId)
2. POST /api/v1/accounts
3. Extract customerId from JWT
4. Create FD Account with customerId
```

### New Flow
```
1. User Login → JWT Token (sub: email)
2. POST /api/v1/accounts
3. Extract email from JWT
4. Call Customer Service: GET /api/profiles/email/{email}
5. Get customerNumber from response (e.g., CUST-20251024-000001)
6. Create FD Account with customerNumber as customerId
```

## Example API Call Flow

### Step 1: User has JWT Token
```
Authorization: Bearer eyJhbGciOiJSUzI1NiJ9...
Token Claims: { "sub": "probott@nexabank.com", ... }
```

### Step 2: Create FD Account
```bash
POST http://localhost:8080/api/v1/accounts
Authorization: Bearer {token}
Content-Type: application/json

{
  "accountName": "My Retirement Fund",
  "calcId": 41
}
```

### Step 3: System Extracts Email
```java
String email = jwt.getSubject(); // "probott@nexabank.com"
```

### Step 4: System Calls Customer Service
```bash
GET http://localhost:1005/api/profiles/email/probott@nexabank.com
```

### Step 5: Customer Service Response
```json
{
  "customerId": "9568cf52-16a1-430c-9eae-a27af667ad37",
  "customerNumber": "CUST-20251024-000001",
  "email": "probott@nexabank.com",
  "firstName": "probott",
  "lastName": "op",
  "customerStatus": "ACTIVE"
}
```

### Step 6: System Extracts customerNumber
```java
String customerNumber = "CUST-20251024-000001";
```

### Step 7: Create FD Account
```java
fdAccountService.createAccount(request, customerNumber);
// customerNumber is stored as customerId in FdAccount entity
```

## Database Impact

### FdAccount Table
The `customer_id` column (which should probably be renamed to `customer_number` for clarity) now stores:
- **Before:** UUID or user ID from JWT (e.g., "usr_123")
- **After:** Business identifier from Customer Service (e.g., "CUST-20251024-000001")

### Accountholder Table
The `customer_id` column now stores:
- **Before:** UUID from JWT
- **After:** Customer number (e.g., "CUST-20251024-000001")

**Note:** This is a breaking change if there's existing data with old format customer IDs.

## Benefits

1. **Business-Friendly IDs:** Customer numbers like "CUST-20251024-000001" are more readable than UUIDs
2. **Consistent Identification:** Same customer number used across all modules
3. **Audit Trail:** Customer number includes date created, helpful for support
4. **Centralized Customer Data:** Single source of truth in Customer Service
5. **Decoupling:** FD Module doesn't need to know customer details, just the number
6. **Versioning Support:** Customer Service maintains audit trail with INSERT-ONLY paradigm

## Configuration Requirements

### Customer Service Must Be Running
- **URL:** http://localhost:1005
- **Endpoint:** `/api/profiles/email/{email}` must be available
- **Authentication:** Must accept JWT tokens from same Auth Service

### Environment Variables (Future Enhancement)
Currently hardcoded, should be externalized:
```properties
customer.service.base-url=http://localhost:1005
customer.service.profile-by-email-path=/api/profiles/email/{email}
```

## Error Scenarios

### 1. Customer Service is Down
```
Error: Failed to fetch customer profile: Connection refused
Response: 500 Internal Server Error
```

### 2. Email Not Found in Customer Service
```
Error: Customer number not found for email: unknown@example.com
Response: 500 Internal Server Error
```

### 3. Invalid Email in JWT Token
```
Error: Failed to fetch customer profile: 404 Not Found
Response: 500 Internal Server Error
```

### 4. Customer Number is Null
```
Error: Customer number not found for email: test@example.com
Response: 500 Internal Server Error
```

## Testing Recommendations

### Unit Tests
1. Test `CustomerService.getCustomerByEmail()` with mock RestTemplate
2. Test `CustomerService.getCustomerNumberByEmail()` extracts correct field
3. Test controller extracts email from JWT correctly

### Integration Tests
1. Test full flow with real Customer Service running
2. Test error handling when Customer Service is unavailable
3. Test with various email formats
4. Verify customerNumber is stored correctly in database

### Manual Testing
```bash
# 1. Start Customer Service on port 1005
# 2. Start Auth Service on port 3020
# 3. Start FD Module on port 8080

# 4. Login and get JWT token
curl -X POST http://localhost:3020/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"probott@nexabank.com","password":"password"}'

# 5. Create FD calculation
curl -X POST http://localhost:4030/api/fd/calculations \
  -H "Authorization: Bearer {token}" \
  -d '{...}'

# 6. Create FD account
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "accountName": "Test FD",
    "calcId": 41
  }'

# 7. Verify response contains customerNumber in customer_id field
```

## Migration Strategy (For Existing Data)

If there's existing data with old customer ID format:

### Option 1: Data Migration Script
```sql
-- Create mapping table
CREATE TABLE customer_id_mapping (
  old_customer_id VARCHAR(255),
  new_customer_number VARCHAR(50),
  migrated_at TIMESTAMP
);

-- Update FD accounts
UPDATE fd_account fa
SET customer_id = (
  SELECT customer_number 
  FROM customer_profiles cp 
  WHERE cp.user_id = fa.customer_id
);

-- Update account holders
UPDATE accountholder ah
SET customer_id = (
  SELECT customer_number 
  FROM customer_profiles cp 
  WHERE cp.user_id = ah.customer_id
);
```

### Option 2: Dual Support (Temporary)
Add logic to detect old vs new format:
```java
if (customerId.startsWith("CUST-")) {
    // New format - use as is
} else {
    // Old format - lookup from mapping table
}
```

## Related Files Modified

1. `src/main/java/com/example/demo/dto/CustomerProfileResponse.java` - NEW
2. `src/main/java/com/example/demo/service/CustomerService.java` - NEW
3. `src/main/java/com/example/demo/controller/FdAccountController.java` - MODIFIED

## Dependencies

- **Customer Service:** Must be running on port 1005
- **RestTemplate:** Already configured in RestTemplateConfig
- **JWT Token:** Must contain email in 'sub' claim
- **Customer Profile:** Must have customerNumber field populated

## Rollback Plan

To rollback this change:
1. Revert FdAccountController to extract customerId from JWT directly
2. Remove CustomerService and CustomerProfileResponse
3. Update documentation back to original
4. Database data remains compatible (just different format in customer_id field)
