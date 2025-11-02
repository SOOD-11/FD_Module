# FD Module Batch Jobs - Logical Time Integration

## Overview

All batch jobs in the FD Module now use the **LogicalClockService** for time-based operations. This ensures consistent time behavior during testing and allows for time manipulation without affecting system time.

## Batch Jobs

### 1. Interest Calculation Job

**Purpose**: Calculate and accrue daily interest for all active FD accounts.

**Schedule**: Daily at midnight (00:00 UTC)

**Configuration File**: `InterestCalculationJobConfig.java`

**Scheduler**: `InterestCalculationScheduler.java`

**Key Features**:
- Reads active FD accounts in pages of 100
- Calculates daily interest based on annual rate divided by 365
- Creates `INTEREST_ACCRUAL` transactions with **logical time** timestamps
- Uses `clockService.getLogicalDateTime()` for transaction dates

**Logical Time Usage**:
```java
// Transaction timestamp uses logical time
transaction.setTransactionDate(clockService.getLogicalDateTime());
```

**Testing Example**:
```java
// Set logical time to specific date
clockService.setLogicalDate(LocalDate.of(2026, 1, 15));

// Run interest calculation job
jobLauncher.run(interestCalculationJob, jobParameters);

// All interest transactions will have timestamp of 2026-01-15
```

---

### 2. Maturity Processing Job

**Purpose**: Process FD accounts that have reached or passed their maturity date.

**Schedule**: Daily at 1:00 AM (01:00 UTC) - Runs after interest calculation

**Configuration File**: `MaturityProcessingJobConfiguration.java`

**Scheduler**: `MaturityProcessingScheduler.java`

**Key Features**:
- Reads active accounts where `maturityDate <= logical current date`
- Processes based on maturity instruction:
  - **RENEW_PRINCIPAL_AND_INTEREST**: Creates new renewed account, marks original as matured
  - **PAYOUT_TO_LINKED_ACCOUNT**: Marks account as matured
  - **CLOSE**: Marks account as matured
- Sends Kafka events: `AccountMaturedEvent` and `AccountAlertEvent`
- Uses **logical time** for maturity date comparison and new account creation

**Logical Time Usage**:
```java
// Query uses logical date for maturity comparison
.queryString("SELECT a FROM FdAccount a WHERE a.status = :status AND a.maturityDate <= :today")
.parameterValues(Map.of(
    "status", AccountStatus.ACTIVE,
    "today", clockService.getLogicalDate()  // Logical date!
))

// New renewed account uses logical date
newAccount.setEffectiveDate(clockService.getLogicalDate());
newAccount.setMaturityDate(clockService.getLogicalDate().plusMonths(termInMonths));

// Kafka events use logical time
AccountAlertEvent alertEvent = new AccountAlertEvent(
    accountNumber,
    AlertType.ACCOUNT_STATUS_CHANGED,
    message,
    customerId,
    clockService.getLogicalDateTime(),  // Logical timestamp!
    eventId,
    details
);
```

**Testing Example**:
```java
// Create account maturing on 2026-06-30
FdAccount account = createAccount(
    maturityDate = LocalDate.of(2026, 6, 30)
);

// Fast-forward to maturity date
clockService.setLogicalDate(LocalDate.of(2026, 6, 30));

// Run maturity processing job
jobLauncher.run(maturityProcessingJob, jobParameters);

// Account will be processed as matured
assertEquals(AccountStatus.MATURED, account.getStatus());
```

---

### 3. Monthly Statement Job

**Purpose**: Generate account statements for all FD accounts for the previous month.

**Schedule**: Monthly on 1st day at 11:00 PM (23:00 UTC)

**Configuration File**: `MonthlyStatementJobConfiguration.java`

**Scheduler**: `MonthlyStatementScheduler.java`

**Key Features**:
- Uses **logical date** to calculate previous month's date range
- Generates statements for all accounts covering the previous month
- Sends statement notifications via Kafka

**Logical Time Usage**:
```java
// Calculate previous month using logical date
LocalDate today = clockService.getLogicalDate();
LocalDate firstDayOfLastMonth = today.minusMonths(1).withDayOfMonth(1);
LocalDate lastDayOfLastMonth = today.withDayOfMonth(1).minusDays(1);

// Generate statements for calculated period
statementService.generateStatementsForAllAccounts(firstDayOfLastMonth, lastDayOfLastMonth);
```

**Testing Example**:
```java
// Set logical time to first day of February 2026
clockService.setLogicalDate(LocalDate.of(2026, 2, 1));

// Run monthly statement job
jobLauncher.run(monthlyStatementJob, jobParameters);

// Statements generated for January 2026 (2026-01-01 to 2026-01-31)
```

---

## Scheduler Details

### Interest Calculation Scheduler
```java
@Scheduled(cron = "0 0 0 * * ?", zone = "UTC")  // Daily at midnight
public void calculateDailyInterest() {
    JobParameters jobParameters = new JobParametersBuilder()
        .addLong("logicalTimestamp", clockService.getLogicalInstant().toEpochMilli())
        .addString("logicalExecutionTime", clockService.getLogicalDateTime().toString())
        .addString("logicalDate", clockService.getLogicalDate().toString())
        .toJobParameters();
    
    jobLauncher.run(interestCalculationJob, jobParameters);
}
```

### Maturity Processing Scheduler
```java
@Scheduled(cron = "0 0 1 * * ?", zone = "UTC")  // Daily at 1 AM
public void processMaturedAccounts() {
    JobParameters jobParameters = new JobParametersBuilder()
        .addLong("logicalTimestamp", clockService.getLogicalInstant().toEpochMilli())
        .addString("logicalExecutionTime", clockService.getLogicalDateTime().toString())
        .addString("logicalDate", clockService.getLogicalDate().toString())
        .toJobParameters();
    
    jobLauncher.run(maturityProcessingJob, jobParameters);
}
```

### Monthly Statement Scheduler
```java
@Scheduled(cron = "0 0 23 1 * ?", zone = "UTC")  // 1st of month at 11 PM
public void generateMonthlyStatements() {
    JobParameters jobParameters = new JobParametersBuilder()
        .addLong("logicalTimestamp", clockService.getLogicalInstant().toEpochMilli())
        .addString("logicalExecutionTime", clockService.getLogicalDateTime().toString())
        .addString("logicalDate", clockService.getLogicalDate().toString())
        .toJobParameters();
    
    jobLauncher.run(monthlyStatementJob, jobParameters);
}
```

---

## Job Execution Order

The jobs are scheduled to run in a specific order to maintain data consistency:

1. **00:00 UTC** - Interest Calculation
   - Accrues daily interest for all active accounts
   
2. **01:00 UTC** - Maturity Processing
   - Processes accounts that matured (including today)
   - Runs after interest calculation to ensure final interest is accrued
   
3. **23:00 UTC (1st of month)** - Monthly Statement
   - Generates statements for previous month
   - Runs late in the day to capture all activity

---

## Testing Batch Jobs with Logical Time

### Test Interest Accrual Over Multiple Days

```java
@Test
void testInterestAccrualOver30Days() {
    // Create account
    FdAccount account = createAccount(
        principal = 100000,
        interestRate = 7.0,
        termInMonths = 12
    );
    
    // Set starting date
    clockService.setLogicalDate(LocalDate.of(2026, 1, 1));
    
    // Run interest calculation for 30 days
    for (int day = 1; day <= 30; day++) {
        clockService.advanceDays(1);
        
        // Run interest job
        jobLauncher.run(interestCalculationJob, buildJobParams());
    }
    
    // Verify 30 interest transactions were created
    List<FdTransaction> transactions = transactionRepo.findByAccountAndType(
        account.getAccountNumber(), 
        TransactionType.INTEREST_ACCRUAL
    );
    assertEquals(30, transactions.size());
    
    // Verify dates are sequential
    assertEquals(LocalDate.of(2026, 1, 2), transactions.get(0).getTransactionDate().toLocalDate());
    assertEquals(LocalDate.of(2026, 1, 31), transactions.get(29).getTransactionDate().toLocalDate());
}
```

### Test Maturity Processing

```java
@Test
void testMaturityProcessing() {
    // Create account maturing in 6 months
    LocalDate startDate = LocalDate.of(2026, 1, 1);
    clockService.setLogicalDate(startDate);
    
    FdAccount account = createAccount(
        effectiveDate = startDate,
        termInMonths = 6,
        maturityInstruction = MaturityInstruction.RENEW_PRINCIPAL_AND_INTEREST
    );
    // Maturity date will be 2026-07-01
    
    // Fast-forward to day before maturity
    clockService.setLogicalDate(LocalDate.of(2026, 6, 30));
    jobLauncher.run(maturityProcessingJob, buildJobParams());
    
    // Should not process yet
    assertEquals(AccountStatus.ACTIVE, accountRepo.findById(account.getId()).get().getStatus());
    
    // Advance to maturity date
    clockService.advanceDays(1);  // Now 2026-07-01
    jobLauncher.run(maturityProcessingJob, buildJobParams());
    
    // Should be matured and renewed
    FdAccount original = accountRepo.findById(account.getId()).get();
    assertEquals(AccountStatus.MATURED, original.getStatus());
    
    // New renewed account should exist
    FdAccount renewed = accountRepo.findByAccountNumber(account.getAccountNumber() + "-R").get();
    assertEquals(AccountStatus.ACTIVE, renewed.getStatus());
    assertEquals(LocalDate.of(2026, 7, 1), renewed.getEffectiveDate());
    assertEquals(LocalDate.of(2027, 1, 1), renewed.getMaturityDate());
}
```

### Test Monthly Statement Generation

```java
@Test
void testMonthlyStatementGeneration() {
    // Create account on Jan 1
    clockService.setLogicalDate(LocalDate.of(2026, 1, 1));
    FdAccount account = createAccount();
    
    // Create some transactions during January
    for (int day = 1; day <= 31; day++) {
        clockService.setLogicalDate(LocalDate.of(2026, 1, day));
        // Create transactions
        createTransaction(account, TransactionType.INTEREST_ACCRUAL);
    }
    
    // Fast-forward to Feb 1 and run statement job
    clockService.setLogicalDate(LocalDate.of(2026, 2, 1));
    jobLauncher.run(monthlyStatementJob, buildJobParams());
    
    // Verify statement was created for January
    Statement statement = statementRepo.findByAccountAndPeriod(
        account.getAccountNumber(),
        LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 1, 31)
    ).get();
    
    assertNotNull(statement);
    assertEquals(31, statement.getTransactionCount());
}
```

### Test Complete Lifecycle

```java
@Test
void testCompleteFDLifecycle() {
    // 1. Create account on Jan 1, 2026 with 6-month term
    clockService.setLogicalDate(LocalDate.of(2026, 1, 1));
    FdAccount account = createAccount(
        principal = 100000,
        interestRate = 7.0,
        termInMonths = 6
    );
    
    // 2. Simulate 6 months of daily interest calculation
    for (int month = 1; month <= 6; month++) {
        LocalDate monthStart = LocalDate.of(2026, month, 1);
        int daysInMonth = monthStart.lengthOfMonth();
        
        for (int day = 1; day <= daysInMonth; day++) {
            clockService.setLogicalDate(LocalDate.of(2026, month, day));
            jobLauncher.run(interestCalculationJob, buildJobParams());
        }
        
        // Generate monthly statement on 1st of next month
        if (month < 6) {
            clockService.setLogicalDate(LocalDate.of(2026, month + 1, 1));
            jobLauncher.run(monthlyStatementJob, buildJobParams());
        }
    }
    
    // 3. Process maturity on July 1, 2026
    clockService.setLogicalDate(LocalDate.of(2026, 7, 1));
    jobLauncher.run(maturityProcessingJob, buildJobParams());
    
    // 4. Verify final state
    FdAccount maturedAccount = accountRepo.findById(account.getId()).get();
    assertEquals(AccountStatus.MATURED, maturedAccount.getStatus());
    
    // Verify interest transactions (approximately 181 days)
    List<FdTransaction> interestTxns = transactionRepo.findByAccountAndType(
        account.getAccountNumber(),
        TransactionType.INTEREST_ACCRUAL
    );
    assertTrue(interestTxns.size() >= 180 && interestTxns.size() <= 182);
    
    // Verify monthly statements (6 months)
    List<Statement> statements = statementRepo.findByAccount(account.getAccountNumber());
    assertEquals(6, statements.size());
}
```

---

## Manual Job Execution

You can manually trigger jobs for testing using the REST API:

### Trigger Interest Calculation
```bash
curl -X POST http://localhost:8080/api/jobs/trigger \
  -H "Content-Type: application/json" \
  -d '{
    "jobName": "interestCalculationJob",
    "logicalDate": "2026-01-15"
  }'
```

### Trigger Maturity Processing
```bash
curl -X POST http://localhost:8080/api/jobs/trigger \
  -H "Content-Type: application/json" \
  -d '{
    "jobName": "maturityProcessingJob",
    "logicalDate": "2026-06-30"
  }'
```

### Trigger Monthly Statement
```bash
curl -X POST http://localhost:8080/api/jobs/trigger \
  -H "Content-Type: application/json" \
  -d '{
    "jobName": "monthlyStatementJob",
    "logicalDate": "2026-02-01"
  }'
```

---

## Benefits of Logical Time in Batch Jobs

### 1. Time Travel Testing
```java
// Test account behavior over years without waiting
clockService.setLogicalDate(LocalDate.of(2026, 1, 1));
// Run jobs for 5 years
for (int days = 0; days < 365 * 5; days++) {
    clockService.advanceDays(1);
    runDailyJobs();
}
// Completed 5 years of testing in minutes!
```

### 2. Reproducible Tests
```java
// Same logical time = same results
clockService.setLogicalDate(LocalDate.of(2026, 1, 1));
runInterestCalculation();
// Always produces same interest amounts for same date
```

### 3. Edge Case Testing
```java
// Test month boundaries
clockService.setLogicalDate(LocalDate.of(2026, 1, 31));
runInterestCalculation();
clockService.advanceDays(1);  // Feb 1
runMonthlyStatement();

// Test leap years
clockService.setLogicalDate(LocalDate.of(2024, 2, 29));
runInterestCalculation();

// Test year boundaries
clockService.setLogicalDate(LocalDate.of(2025, 12, 31));
runAllJobs();
clockService.advanceDays(1);  // Jan 1, 2026
runAllJobs();
```

### 4. Parallel Testing
```java
// Multiple test instances with different logical times
@Test
void test1() {
    clockService.setLogicalDate(LocalDate.of(2026, 1, 1));
    // Test scenario 1
}

@Test
void test2() {
    clockService.setLogicalDate(LocalDate.of(2027, 6, 15));
    // Test scenario 2 - different time, no conflict!
}
```

---

## Monitoring and Debugging

### Check Logical Time in Logs

All batch jobs now log the logical time:

```
2025-11-02 10:15:30 INFO  InterestCalculationScheduler - Starting scheduled daily interest calculation job at logical time: 2026-01-15T00:00:00
2025-11-02 10:15:35 INFO  InterestCalculationScheduler - Daily interest calculation job completed successfully at logical time: 2026-01-15T00:00:05.123
```

### Verify Job Parameters

Job parameters include logical time information:

```java
JobParameters params = jobExecution.getJobParameters();
String logicalDate = params.getString("logicalDate");  // "2026-01-15"
String logicalExecutionTime = params.getString("logicalExecutionTime");  // "2026-01-15T00:00:00"
Long logicalTimestamp = params.getLong("logicalTimestamp");  // Epoch millis
```

### Check Time Offset

```java
// Get current logical time offset
Duration offset = clockService.getTimeOffset();
log.info("Current time offset: {} days", offset.toDays());

// Get when offset was set
Instant offsetSetAt = clockService.getOffsetSetAt();
log.info("Offset set at system time: {}", offsetSetAt);
```

---

## Summary

All three batch jobs now use **LogicalClockService** for time-based operations:

| Job | Logical Time Usage | Schedule |
|-----|-------------------|----------|
| **Interest Calculation** | Transaction timestamps | Daily at 00:00 |
| **Maturity Processing** | Maturity date comparison, new account dates, Kafka events | Daily at 01:00 |
| **Monthly Statement** | Date range calculation | Monthly on 1st at 23:00 |

This ensures:
- ✅ Consistent time behavior across all operations
- ✅ Easy testing with time manipulation
- ✅ Reproducible results for same logical time
- ✅ Ability to fast-forward time for long-term testing
- ✅ Natural time progression with running clock behavior
