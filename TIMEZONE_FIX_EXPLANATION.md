# Timezone Fix for Logical Clock API

## Problem

When setting the logical time using the `/set-instant` API with a UTC timestamp like `2027-11-01T22:58:00Z`, the response was showing a time that was 5:30 hours ahead (IST timezone).

### Example Issue
```json
Request:
{
  "newLogicalInstant": "2027-11-01T22:58:00Z"
}

Old Response (Confusing):
{
  "newLogicalDateTime": "2027-11-02T04:28:00"  // ← Why is this different?!
}
```

## Root Cause

The system was using `ZoneId.systemDefault()` (which is IST - UTC+5:30 in India) to convert the instant to `LocalDateTime`. This caused confusion because:

1. You set time in **UTC**: `2027-11-01T22:58:00Z`
2. System stored it correctly as an **Instant** (always UTC)
3. But when returning `LocalDateTime`, it converted to **IST**: `2027-11-02T04:28:00`

The time was set correctly internally, but the response was misleading!

## Solution

Updated all time admin API endpoints to return **both UTC and local timezone** information clearly:

### New Response Format

```json
{
  // Primary - Always in UTC
  "newLogicalInstant": "2027-11-01T22:58:00Z",
  "newLogicalDateTimeUTC": "2027-11-01T22:58:00",
  "newLogicalDateUTC": "2027-11-01",
  
  // Secondary - In system's local timezone (IST)
  "newLogicalDateTimeLocal": "2027-11-02T04:28:00",
  "newLogicalDateLocal": "2027-11-02",
  "systemTimezone": "Asia/Kolkata",
  
  "message": "Logical time set successfully"
}
```

Now you can clearly see:
- **UTC time**: `2027-11-01T22:58:00` (what you set)
- **Local time**: `2027-11-02T04:28:00` (UTC + 5:30 hours for IST)
- **Timezone**: `Asia/Kolkata` (so you know why they differ)

## Updated Endpoints

All time admin endpoints now return this enhanced format:

1. **GET `/api/admin/time/current`** - Get current logical time
2. **POST `/api/admin/time/set-instant`** - Set to specific instant
3. **POST `/api/admin/time/set-date`** - Set to specific date
4. **POST `/api/admin/time/advance`** - Advance time forward
5. **POST `/api/admin/time/reset`** - Reset to system time

## Usage Examples

### Setting Time in UTC

```bash
curl -X POST http://localhost:8082/api/admin/time/set-instant \
  -H "Content-Type: application/json" \
  -d '{
    "newLogicalInstant": "2027-11-01T22:58:00Z"
  }'
```

**Response:**
```json
{
  "newLogicalInstant": "2027-11-01T22:58:00Z",
  "newLogicalDateTimeUTC": "2027-11-01T22:58:00",
  "newLogicalDateUTC": "2027-11-01",
  "newLogicalDateTimeLocal": "2027-11-02T04:28:00",
  "newLogicalDateLocal": "2027-11-02",
  "systemTimezone": "Asia/Kolkata",
  "message": "Logical time set successfully"
}
```

### Checking Current Time

```bash
curl http://localhost:8082/api/admin/time/current
```

**Response:**
```json
{
  "logicalInstant": "2027-11-01T22:58:05.123Z",
  "logicalDateTimeUTC": "2027-11-01T22:58:05.123",
  "logicalDateUTC": "2027-11-01",
  "logicalDateTimeLocal": "2027-11-02T04:28:05.123",
  "logicalDateLocal": "2027-11-02",
  "systemTimezone": "Asia/Kolkata",
  "message": "Current logical time"
}
```

## Understanding the Response

### Which Time Should You Use?

**For API requests and batch jobs:**
- Use **UTC times** (`logicalInstant`, `logicalDateTimeUTC`, `logicalDateUTC`)
- These are timezone-agnostic and consistent across systems

**For displaying to users:**
- Use **Local times** (`logicalDateTimeLocal`, `logicalDateLocal`)
- These match what users see in their system timezone

**Always check:**
- `systemTimezone` field to understand the offset

### Time Conversion

```
UTC Time:   2027-11-01T22:58:00Z
            ↓ + 5:30 hours (IST offset)
Local Time: 2027-11-02T04:28:00 (IST)
```

## Internal Storage

Internally, the system stores time as:
- **Instant** (always UTC, no timezone)
- **Duration offset** from system time

When you call `getLogicalDateTime()`, it applies the system's default timezone (IST) to convert Instant → LocalDateTime.

This is correct behavior! The confusion was only in the API response not showing both representations.

## Testing

Now you can verify the time was set correctly:

```bash
# Set time
curl -X POST http://localhost:8082/api/admin/time/set-instant \
  -H "Content-Type: application/json" \
  -d '{"newLogicalInstant": "2027-11-01T22:58:00Z"}'

# Check - should show same instant
curl http://localhost:8082/api/admin/time/current | jq '.logicalInstant'
# Output: "2027-11-01T22:58:00.xxxZ"  ✓ Correct!
```

## Summary

✅ **Time is set correctly** - No bug in storage or calculation

✅ **API now shows both UTC and local times** - No more confusion

✅ **Timezone is displayed** - You know exactly what's happening

✅ **All endpoints updated** - Consistent format everywhere

The "+5:30 hours" you saw was just timezone conversion (IST = UTC+5:30). The actual stored time was always correct! Now the API makes this clear by showing both representations.
