# Kafka Alert Topic Implementation

## Overview
Implemented Kafka producer calls to the `alert` topic that trigger whenever:
1. A new FD account is created
2. An existing account's details are modified (account holder added, status changed)
3. Accounts are renewed during maturity processing

## Implementation Details

### 1. New Event Class: AccountAlertEvent

**Location:** `src/main/java/com/example/demo/events/AccountAlertEvent.java`

**Structure:**
```java
public record AccountAlertEvent(
    String accountNumber,
    AlertType alertType,
    String alertMessage,
    String customerId,
    LocalDateTime timestamp,
    String eventId,
    String details
)
```

**Alert Types:**
- `ACCOUNT_CREATED` - New account created
- `ACCOUNT_MODIFIED` - Account details modified
- `ACCOUNT_HOLDER_ADDED` - New account holder/nominee added
- `ACCOUNT_STATUS_CHANGED` - Account status changed (matured, closed, etc.)

### 2. Updated KafkaProducerService

**Location:** `src/main/java/com/example/demo/service/KafkaProducerService.java`

**Changes:**
- Added constant: `ALERT_TOPIC = "alert"`
- Added method: `sendAlertEvent(AccountAlertEvent event)`

**Method Signature:**
```java
public void sendAlertEvent(AccountAlertEvent event) {
    log.info("Publishing alert event to topic {}: type={}, account={}", 
             ALERT_TOPIC, event.alertType(), event.accountNumber());
    try {
        this.kafkaTemplate.send(ALERT_TOPIC, event.accountNumber(), event);
    } catch (Exception e) {
        log.error("Failed to send alert event to Kafka", e);
    }
}
```

### 3. Alert Triggers in FDAccountServiceImpl

**Location:** `src/main/java/com/example/demo/service/impl/FDAccountServiceImpl.java`

#### Trigger 1: Account Creation
**Method:** `createAccount()`
**Alert Type:** `ACCOUNT_CREATED`
**Timing:** After account is saved to database
**Details Included:**
- Account number
- Customer ID
- Principal amount
- Product code
- Maturity date
- Interest rate

**Code:**
```java
AccountAlertEvent alertEvent = new AccountAlertEvent(
    savedAccount.getAccountNumber(),
    AccountAlertEvent.AlertType.ACCOUNT_CREATED,
    "New FD account created: ...",
    customerId,
    clockService.getLogicalDateTime(),
    UUID.randomUUID().toString(),
    "Product: ..., Maturity Date: ..., Interest Rate: ..."
);
kafkaProducerService.sendAlertEvent(alertEvent);
```

#### Trigger 2: Account Holder Added
**Method:** `addRoleToAccount()`
**Alert Type:** `ACCOUNT_HOLDER_ADDED`
**Timing:** After new account holder is saved
**Details Included:**
- Account number
- Customer ID of new holder
- Role type (OWNER, NOMINEE, etc.)
- Ownership percentage

**Code:**
```java
AccountAlertEvent alertEvent = new AccountAlertEvent(
    updatedAccount.getAccountNumber(),
    AccountAlertEvent.AlertType.ACCOUNT_HOLDER_ADDED,
    "Account holder added to account ...",
    request.customerId(),
    clockService.getLogicalDateTime(),
    UUID.randomUUID().toString(),
    "Role: ..., Ownership: ...%"
);
kafkaProducerService.sendAlertEvent(alertEvent);
```

#### Trigger 3: Premature Withdrawal (Status Change)
**Method:** `performEarlyWithdrawal()`
**Alert Type:** `ACCOUNT_STATUS_CHANGED`
**Timing:** After account status changed to PREMATURELY_CLOSED
**Details Included:**
- Account number
- New status (PREMATURELY_CLOSED)
- Penalty amount
- Final payout amount
- Closure reason

**Code:**
```java
AccountAlertEvent alertEvent = new AccountAlertEvent(
    closedAccount.getAccountNumber(),
    AccountAlertEvent.AlertType.ACCOUNT_STATUS_CHANGED,
    "Account ... status changed to PREMATURELY_CLOSED",
    customerIdsToNotify.get(0),
    clockService.getLogicalDateTime(),
    UUID.randomUUID().toString(),
    "Penalty: ..., Final Payout: ..., Closure Reason: Premature Withdrawal"
);
kafkaProducerService.sendAlertEvent(alertEvent);
```

### 4. Alerts in Batch Processing

**Location:** `src/main/java/com/example/demo/config/MaturityProcessingJobConfiguration.java`

#### Trigger 4: Account Matured
**Alert Type:** `ACCOUNT_STATUS_CHANGED`
**Timing:** When account reaches maturity date
**Details Included:**
- Account number
- Maturity amount
- Maturity date
- Maturity instruction (PAYOUT, RENEW, CLOSE)

#### Trigger 5: Account Renewed
**Alert Type:** `ACCOUNT_CREATED`
**Timing:** When matured account is automatically renewed
**Details Included:**
- New account number
- Original account number
- New principal amount (maturity value of original)
- New maturity date

## Alert Topic Configuration

### Kafka Topic
- **Topic Name:** `alert`
- **Key:** Account number (for partitioning)
- **Value:** AccountAlertEvent JSON

### Sample Alert Message

**Account Creation:**
```json
{
  "account_number": "1234567890",
  "alert_type": "ACCOUNT_CREATED",
  "alert_message": "New FD account created: 1234567890 with principal amount: 100000.00",
  "customer_id": "CUST123",
  "timestamp": "2025-01-31T14:30:00",
  "event_id": "uuid-here",
  "details": "Product: FD_REGULAR, Maturity Date: 2026-01-31, Interest Rate: 7.50%"
}
```

**Account Holder Added:**
```json
{
  "account_number": "1234567890",
  "alert_type": "ACCOUNT_HOLDER_ADDED",
  "alert_message": "Account holder added to account 1234567890: Customer CUST456 with role NOMINEE",
  "customer_id": "CUST456",
  "timestamp": "2025-02-15T10:00:00",
  "event_id": "uuid-here",
  "details": "Role: NOMINEE, Ownership: 50.00%"
}
```

**Account Status Changed:**
```json
{
  "account_number": "1234567890",
  "alert_type": "ACCOUNT_STATUS_CHANGED",
  "alert_message": "Account 1234567890 has matured",
  "customer_id": "CUST123",
  "timestamp": "2026-01-31T00:00:00",
  "event_id": "uuid-here",
  "details": "Maturity Amount: 107500.00, Maturity Date: 2026-01-31, Instruction: PAYOUT_TO_LINKED_ACCOUNT"
}
```

## Consumer Implementation (Example)

Consumers can subscribe to the `alert` topic to:
1. Send notifications (email, SMS, push) to customers
2. Log alerts in monitoring systems
3. Trigger downstream workflows
4. Update external systems
5. Generate audit trails

**Example Consumer:**
```java
@KafkaListener(topics = "alert", groupId = "alert-consumer-group")
public void handleAlert(AccountAlertEvent alert) {
    log.info("Received alert: {} for account {}", 
             alert.alertType(), alert.accountNumber());
    
    switch (alert.alertType()) {
        case ACCOUNT_CREATED:
            // Send welcome notification
            notificationService.sendWelcome(alert);
            break;
        case ACCOUNT_HOLDER_ADDED:
            // Notify existing holders
            notificationService.notifyAccountHolders(alert);
            break;
        case ACCOUNT_STATUS_CHANGED:
            // Send status update notification
            notificationService.sendStatusUpdate(alert);
            break;
    }
}
```

## Testing

### Test Account Creation Alert
```bash
# Create a new FD account via API
curl -X POST http://localhost:8082/api/v1/accounts \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "calcId": 123,
    "accountName": "My FD Account"
  }'

# Check Kafka alert topic for ACCOUNT_CREATED event
```

### Test Account Holder Addition Alert
```bash
# Add account holder to existing account
curl -X POST http://localhost:8082/api/v1/accounts/{accountNumber}/roles \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "customerId": "CUST456",
    "roleType": "NOMINEE",
    "ownershipPercentage": 50.00
  }'

# Check Kafka alert topic for ACCOUNT_HOLDER_ADDED event
```

### Test Status Change Alert
```bash
# Perform premature withdrawal
curl -X POST http://localhost:8082/api/v1/accounts/{accountNumber}/withdraw \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{}'

# Check Kafka alert topic for ACCOUNT_STATUS_CHANGED event
```

## Monitoring

### Alert Metrics to Track
1. **Alert Volume:** Count of alerts by type
2. **Alert Latency:** Time from event to Kafka publish
3. **Failed Alerts:** Count of failed Kafka sends
4. **Consumer Lag:** Lag in alert topic consumers

### Logging

All alert sends are logged:
```
INFO  - Alert sent for new account creation: 1234567890
INFO  - Publishing alert event to topic alert: type=ACCOUNT_CREATED, account=1234567890
```

## Benefits

1. **Real-time Notifications:** Immediate alerts for account changes
2. **Audit Trail:** Complete history of account modifications
3. **Decoupled Architecture:** Alert consumers independent of core service
4. **Scalability:** Multiple consumers can process alerts in parallel
5. **Reliability:** Kafka ensures message delivery and ordering

## Future Enhancements

1. **Alert Filtering:** Allow consumers to filter by alert type
2. **Alert Priorities:** High/Medium/Low priority levels
3. **Alert Batching:** Group multiple alerts for same account
4. **Alert Suppression:** Prevent duplicate alerts within time window
5. **Alert Acknowledgment:** Track which alerts have been acted upon
6. **Alert Enrichment:** Add more contextual data to alerts

## Summary

✅ Alert topic successfully integrated
✅ 5 alert triggers implemented:
   - Account creation
   - Account holder addition
   - Account status changes
   - Account maturity
   - Account renewal
✅ Uses logical clock for consistent timestamps
✅ Proper error handling and logging
✅ Ready for consumer implementation
