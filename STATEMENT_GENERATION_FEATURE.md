# FD Statement Generation Feature

## Overview
This document describes the new statement generation feature for Fixed Deposit (FD) accounts, including both on-demand API and automated monthly batch processing.

## Features Implemented

### 1. On-Demand Statement Generation API

**Endpoint:** `POST /api/v1/accounts/{accountNumber}/statement`

**Description:** Generates and sends an FD account statement via Kafka to the notification service.

**Request Parameters:**
- `accountNumber` (path parameter): FD account number
- `startDate` (request body): Statement period start date
- `endDate` (request body): Statement period end date
- JWT Token (header): Contains user's email in 'sub' claim

**Request Example:**
```json
POST /api/v1/accounts/1017575972/statement
Authorization: Bearer <JWT_TOKEN>

{
  "startDate": "2025-10-01",
  "endDate": "2025-10-27"
}
```

**Response Example:**
```json
{
  "message": "Statement generation request accepted for account 1017575972"
}
```

### 2. Kafka Statement Notification

**Topic:** `statement`

**Message Structure:** The complete notification request sent to Kafka includes:

#### 1. Notification Routing & Content
- `toEmail`: Customer's email address
- `toSms`: Customer's phone number
- `subject`: Email subject line (e.g., "Your Fixed Deposit Statement - A/c No. ...75972")
- `body`: Email body with templated message from Product Service

#### 2. Metadata
- `statementType`: "FD_STATEMENT"
- `pdfFileName`: Generated filename (e.g., "FD_Statement_1017575972_2025-10.pdf")
- `statementPeriod`: Object with startDate and endDate
- `generatedOnDate`: ISO 8601 timestamp

#### 3. Customer Details
- Fetched from Customer Service API (`/api/profiles/email/{email}`)
- Includes: customerId, customerNumber, name, DOB, contact info, address, masked PAN

#### 4. Account Details
- From FD account entity
- Includes: accountNumber, productName, status, currency, amounts, dates, tenure, interest details

#### 5. Current Balances
- `asOfDate`: Current timestamp
- `principal`: Current principal balance
- `interest`: Accrued interest balance
- `penalty`: Any penalty amounts

#### 6. Transaction History
- List of all transactions in the specified period
- Each transaction includes: date, description, debit, credit, running balance, reference ID

### 3. Automated Monthly Statement Generation

**Schedule:** 1st of every month at 11:00 PM UTC

**Implementation:**
- Spring Batch job configuration
- Scheduled using Spring's `@Scheduled` annotation
- Cron expression: `"0 0 23 1 * ?"` (11 PM on the 1st of every month)

**Process Flow:**
1. Scheduler triggers on the 1st of every month at 11:00 PM
2. Batch job calculates previous month's date range (1st to last day)
3. Fetches all FD accounts from the database
4. For each account:
   - Retrieves customer details from Customer Service
   - Gets communication template from Product Service
   - Fetches transactions for the period
   - Calculates balances
   - Builds complete statement notification
   - Sends to Kafka 'statement' topic
5. Logs success/failure count

## Technical Components

### DTOs Created

1. **StatementRequest.java**
   - Input for statement generation API
   - Fields: startDate, endDate

2. **StatementNotificationRequest.java**
   - Complete Kafka message structure
   - Includes all 6 sections mentioned above

3. **CustomerDetailsDto.java**
   - Customer information from Customer Service
   - Includes nested AddressDto

4. **AccountDetailsDto.java**
   - FD account information
   - All account configuration details

5. **CurrentBalancesDto.java**
   - Current balance snapshot
   - Principal, interest, penalty amounts

6. **StatementTransactionDto.java**
   - Individual transaction details
   - Includes running balance calculation

7. **StatementPeriodDto.java**
   - Period information for the statement

8. **ProductCommunicationResponse.java**
   - Response from Product Service communications API

### Services

#### StatementService.java
Main service handling statement generation logic.

**Key Methods:**
- `generateStatement(accountNumber, startDate, endDate, userEmail)`: Generate for single account
- `generateStatementsForAllAccounts(startDate, endDate)`: Generate for all accounts (batch)
- `buildStatementRequest()`: Constructs complete Kafka message
- `buildCustomerDetails()`: Maps customer service response
- `buildAccountDetails()`: Maps account entity
- `buildCurrentBalances()`: Calculates current balances
- `buildTransactions()`: Processes transaction history with running balance
- `replacePlaceholders()`: Replaces template variables with actual values

**Template Variables Supported:**
- `${CUSTOMER_NAME}`: Customer's first name
- `${PRODUCT_NAME}`: FD account/product name
- `${LAST_4_DIGITS}`: Last 4 digits of account number
- `${OPENING_BALANCE}`: Balance at start of period
- `${CLOSING_BALANCE}`: Balance at end of period

#### ProductService.java (Enhanced)
Added methods to fetch communication templates.

**New Methods:**
- `getProductCommunications(productCode)`: Fetches all communication templates
- `getCommunicationTemplate(productCode, eventType)`: Gets specific template by event type

#### KafkaProducerService.java (Enhanced)
Added method to send statement notifications.

**New Method:**
- `sendStatementNotification(StatementNotificationRequest)`: Sends to 'statement' topic

### Configuration

#### MonthlyStatementJobConfiguration.java
Spring Batch job configuration for monthly statement generation.

**Beans:**
- `monthlyStatementJob`: Main job definition
- `monthlyStatementStep`: Single step tasklet
- `monthlyStatementTasklet`: Business logic for statement generation

#### MonthlyStatementScheduler.java
Spring scheduler for triggering monthly batch job.

**Configuration:**
- Cron: `0 0 23 1 * ?` (11 PM on 1st of every month, UTC)
- Uses Spring Batch JobLauncher to execute job
- Adds timestamp and execution time as job parameters

### Controller Enhancement

**FdAccountController.java**
Added new endpoint for statement generation.

**New Endpoint:**
```java
POST /api/v1/accounts/{accountNumber}/statement
```

**Features:**
- Swagger/OpenAPI documentation
- JWT authentication required
- Validates date range
- Returns immediate acknowledgment

## Data Flow

### On-Demand Statement Generation

```
User Request (JWT + Account + Dates)
        ↓
FdAccountController
        ↓
Extract email from JWT
        ↓
StatementService.generateStatement()
        ↓
┌───────────────────────────────────┐
│ 1. Fetch FD Account from DB       │
│ 2. Get Customer from Customer API │
│ 3. Get Template from Product API  │
│ 4. Fetch Transactions from DB     │
│ 5. Calculate Balances              │
│ 6. Build Complete Notification    │
└───────────────────────────────────┘
        ↓
KafkaProducerService
        ↓
Kafka Topic: "statement"
        ↓
Notification Service (Consumer)
```

### Monthly Batch Processing

```
Scheduler (Cron: 1st @ 11 PM)
        ↓
MonthlyStatementScheduler
        ↓
Launch Spring Batch Job
        ↓
MonthlyStatementJobConfiguration
        ↓
Calculate Previous Month Range
        ↓
Fetch All FD Accounts
        ↓
For Each Account:
    StatementService.generateStatement()
        ↓
    Send to Kafka
        ↓
Log Results (Success/Failure Count)
```

## External Service Integrations

### 1. Customer Service
**Endpoint:** `GET /api/profiles/email/{email}`
**Purpose:** Fetch complete customer profile including contact details and address
**Used For:** Customer details section in statement

### 2. Product & Pricing Service
**Endpoint:** `GET /api/products/{productCode}/communications`
**Purpose:** Fetch communication templates including COMM_MONTHLY_STATEMENT
**Used For:** Email body content with placeholders

### 3. Kafka
**Topic:** `statement`
**Purpose:** Async communication with Notification Service
**Message:** Complete StatementNotificationRequest JSON

## Configuration Properties

Ensure these properties are set in `application.properties`:

```properties
# Customer Service
customer.service.base-url=http://localhost:1005

# Product Service
product.service.base-url=http://localhost:8080
product.service.path=/api/products

# Kafka
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer

# Spring Batch
spring.batch.jdbc.initialize-schema=always
spring.batch.job.enabled=false  # Disable auto-run on startup

# Scheduling
spring.task.scheduling.pool.size=2
```

## Testing

### Test On-Demand API

```bash
# Get JWT token first
TOKEN="<your-jwt-token>"

# Generate statement
curl -X POST http://localhost:8080/api/v1/accounts/1017575972/statement \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "startDate": "2025-10-01",
    "endDate": "2025-10-27"
  }'
```

### Test Batch Job Manually

You can trigger the batch job manually using Spring Batch Admin or programmatically:

```java
@Autowired
private JobLauncher jobLauncher;

@Autowired
private Job monthlyStatementJob;

public void testBatchJob() throws Exception {
    JobParameters params = new JobParametersBuilder()
        .addLong("timestamp", System.currentTimeMillis())
        .toJobParameters();
    jobLauncher.run(monthlyStatementJob, params);
}
```

## Monitoring

### Logs to Monitor

1. **Statement Generation:**
   ```
   Generating statement for account: {accountNumber} from {startDate} to {endDate}
   Statement notification sent for account: {accountNumber}
   ```

2. **Batch Job:**
   ```
   Starting scheduled monthly statement generation job
   Generating statements for period: {startDate} to {endDate}
   Statement generation completed. Success: X, Failures: Y
   ```

3. **Kafka:**
   ```
   Publishing statement notification to topic statement: account={accountNumber}, type=FD_STATEMENT
   ```

## Error Handling

The service includes comprehensive error handling:

1. **Account Not Found:** Throws `ResourceNotFoundException`
2. **Customer Service Failure:** Logs error and throws runtime exception
3. **Product Service Failure:** Uses default template if COMM_MONTHLY_STATEMENT not found
4. **Kafka Failure:** Logs error but doesn't fail the entire operation
5. **Batch Processing:** Individual account failures don't stop the batch; failures are logged and counted

## Future Enhancements

1. Add retry mechanism for failed statement generations
2. Store statement generation history in database
3. Add API to retrieve statement generation status
4. Support different statement types (quarterly, annual)
5. Add statement generation metrics and monitoring dashboard
6. Implement statement download functionality
7. Add email notification on successful statement generation

## Related Documentation

- Customer Service API: `customer.yaml`
- Product & Pricing Service API: `product.yaml`
- FD Module API Documentation: Swagger UI at `/swagger-ui.html`
