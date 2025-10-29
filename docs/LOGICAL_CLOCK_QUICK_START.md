# Logical Clock - Quick Start Guide

## For Developers

### Using IClockService in Your Code

#### 1. Inject the Service
```java
@Service
@RequiredArgsConstructor
public class YourService {
    private final IClockService clockService;
    
    // Your methods here
}
```

#### 2. Replace Time Calls

**BEFORE (Don't do this):**
```java
LocalDate today = LocalDate.now();
LocalDateTime timestamp = LocalDateTime.now();
Instant transactionTime = Instant.now();
```

**AFTER (Do this):**
```java
LocalDate today = clockService.getLogicalDate();
LocalDateTime timestamp = clockService.getLogicalDateTime();
Instant transactionTime = clockService.getLogicalInstant();
```

#### 3. Set Entity Timestamps Explicitly

**BEFORE (Don't do this):**
```java
@PrePersist
protected void onCreate() {
    createdAt = LocalDateTime.now();
}
```

**AFTER (Do this in service):**
```java
public Entity createEntity() {
    Entity entity = new Entity();
    entity.setCreatedAt(clockService.getLogicalDateTime());
    entity.setUpdatedAt(clockService.getLogicalDateTime());
    // ... set other fields
    return repository.save(entity);
}
```

## For Testers

### Time Control API Endpoints

Base URL: `http://localhost:8082/api/admin/time`

#### 1. Check Current Logical Time
```bash
curl http://localhost:8082/api/admin/time/current
```

Response:
```json
{
  "currentLogicalInstant": "2025-01-31T12:00:00Z",
  "currentLogicalDate": "2025-01-31",
  "currentLogicalDateTime": "2025-01-31T12:00:00",
  "systemTime": "2025-01-31T14:30:45.123Z",
  "isLogicalClockActive": true
}
```

#### 2. Set to Specific Date
```bash
curl -X POST http://localhost:8082/api/admin/time/set-date \
  -H "Content-Type: application/json" \
  -d '{"newLogicalDate": "2025-12-31"}'
```

#### 3. Set to Specific Instant
```bash
curl -X POST http://localhost:8082/api/admin/time/set-instant \
  -H "Content-Type: application/json" \
  -d '{"newLogicalInstant": "2025-12-31T23:59:59Z"}'
```

#### 4. Advance Time Forward
```bash
# Advance 7 days
curl -X POST http://localhost:8082/api/admin/time/advance \
  -H "Content-Type: application/json" \
  -d '{"days": 7, "hours": 0}'

# Advance 2 hours
curl -X POST http://localhost:8082/api/admin/time/advance \
  -H "Content-Type: application/json" \
  -d '{"days": 0, "hours": 2}'

# Advance 30 days and 6 hours
curl -X POST http://localhost:8082/api/admin/time/advance \
  -H "Content-Type: application/json" \
  -d '{"days": 30, "hours": 6}'
```

#### 5. Reset to System Time
```bash
curl -X POST http://localhost:8082/api/admin/time/reset
```

### Common Test Scenarios

#### Test FD Maturity
```bash
# 1. Create FD with maturity date 2026-01-31
# 2. Fast-forward to maturity
curl -X POST http://localhost:8082/api/admin/time/set-date \
  -H "Content-Type: application/json" \
  -d '{"newLogicalDate": "2026-01-31"}'

# 3. Run maturity batch job or check account status
# 4. Reset time
curl -X POST http://localhost:8082/api/admin/time/reset
```

#### Test Interest Accrual
```bash
# Loop through 30 days
for i in {1..30}; do
  echo "Day $i"
  curl -X POST http://localhost:8082/api/admin/time/advance \
    -H "Content-Type: application/json" \
    -d '{"days": 1, "hours": 0}'
  
  # Run interest calculation job or check balance
  sleep 1
done

# Reset
curl -X POST http://localhost:8082/api/admin/time/reset
```

#### Test Monthly Statement
```bash
# Set to end of month
curl -X POST http://localhost:8082/api/admin/time/set-date \
  -H "Content-Type: application/json" \
  -d '{"newLogicalDate": "2025-01-31"}'

# Generate statements
# Verify statement date range

# Reset
curl -X POST http://localhost:8082/api/admin/time/reset
```

## Environment Configuration

### Development (uses logical clock)
```properties
spring.profiles.active=dev
```

### Testing (uses logical clock)
```properties
spring.profiles.active=test
```

### Production (uses real clock, admin API disabled)
```properties
spring.profiles.active=prod
```

## Best Practices

### For Developers
1. ✅ Always inject `IClockService`
2. ✅ Never call `LocalDate.now()`, `LocalDateTime.now()`, `Instant.now()`
3. ✅ Set timestamps explicitly in service layer
4. ✅ Write time-independent unit tests
5. ✅ Use logical clock for integration tests

### For Testers
1. ✅ Always reset time after tests
2. ✅ Document time dependencies in test cases
3. ✅ Use specific dates for reproducibility
4. ✅ Check current time before assertions
5. ✅ Clean up test data with time dependencies

## Troubleshooting

### Admin API returns 404
- **Problem:** Running in production profile
- **Solution:** Set `spring.profiles.active=dev` or `test`

### Time doesn't change
- **Problem:** Wrong service implementation active
- **Solution:** Check active profile, ensure NOT `prod`

### Timestamps are null
- **Problem:** Service not setting timestamps
- **Solution:** Add `entity.setCreatedAt(clockService.getLogicalDateTime())`

### Time resets after restart
- **Problem:** Expected behavior
- **Solution:** Logical clock resets on app restart - this is by design

## Quick Reference

| Task | Command |
|------|---------|
| View current time | `GET /api/admin/time/current` |
| Set date | `POST /api/admin/time/set-date` |
| Set instant | `POST /api/admin/time/set-instant` |
| Move forward | `POST /api/admin/time/advance` |
| Reset to now | `POST /api/admin/time/reset` |

## Need Help?

See full documentation in `LOGICAL_CLOCK_IMPLEMENTATION.md`
