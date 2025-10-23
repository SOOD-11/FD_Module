# JWT Authentication - Quick Start Guide

## 🔐 Authentication Setup

Your FD Module now requires JWT authentication for all API endpoints!

## Quick Configuration

### 1. Configure Auth Service URL

Edit `src/main/resources/application.properties`:

```properties
# Change this to point to your auth service
auth.service.base-url=http://localhost:3020

# This path usually stays the same
auth.service.public-key-path=/api/auth/public-key
```

### 2. Start Your Auth Service

Make sure your authentication service is running at the configured URL and exposes the public key endpoint.

### 3. Start FD Module

```bash
./mvnw spring-boot:run
```

On startup, the application will:
- Fetch the RSA public key from your auth service
- Configure JWT validation
- Protect all `/api/v1/**` endpoints

## Making API Calls

### Option 1: Using cURL

```bash
# Get your JWT token from the auth service first
export TOKEN="your-jwt-token-here"

# Make authenticated request
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "accountName": "My FD",
    "productCode": "FD-REGULAR-01",
    "customerId": "CUST123",
    "termInMonths": 12,
    "interestRate": 7.5,
    "principalAmount": 100000
  }'
```

### Option 2: Using Swagger UI

1. Open http://localhost:8080/swagger-ui.html
2. Click **"Authorize"** button (🔒 icon at the top)
3. Enter your JWT token
4. Click **"Authorize"**
5. Now all requests will include your token!

### Option 3: Using Postman

1. Create/Open your request
2. Go to **Authorization** tab
3. Select **Bearer Token** type
4. Paste your JWT token
5. Send request

## Testing Authentication

### Test 1: Verify Public Endpoints Work (No Token)

```bash
# Swagger UI - should work without token
curl http://localhost:8080/swagger-ui.html

# OpenAPI Docs - should work without token
curl http://localhost:8080/api-docs
```

### Test 2: Verify Protected Endpoints Require Token

```bash
# Should return 401 Unauthorized
curl http://localhost:8080/api/v1/accounts/search?idType=accountNumber&value=TEST
```

### Test 3: Verify Token Authentication Works

```bash
# Should return your user info
curl -H "Authorization: Bearer YOUR_TOKEN" \
  http://localhost:8080/api/v1/auth/me

# Should verify authentication
curl -H "Authorization: Bearer YOUR_TOKEN" \
  http://localhost:8080/api/v1/auth/verify
```

## What's Protected?

### ✅ Protected Endpoints (Require JWT)
- `/api/v1/accounts/**` - All account operations
- `/api/v1/reports/**` - All reports
- `/api/v1/jobs/**` - All batch jobs
- `/api/v1/auth/**` - Authentication verification endpoints

### ✅ Public Endpoints (No JWT Required)
- `/swagger-ui/**` - Swagger documentation UI
- `/api-docs/**` - OpenAPI specification
- `/actuator/**` - Health checks and monitoring

## Expected JWT Format

Your JWT should be a standard Bearer token:

```
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyMTIzIiwibmFtZSI6IkpvaG4gRG9lIiwiZW1haWwiOiJqb2huQGV4YW1wbGUuY29tIiwicm9sZXMiOlsiQ1VTVE9NRVIiXSwiZXhwIjoxNzI5Njk5MjAwLCJpYXQiOjE3Mjk2OTU2MDB9.signature
```

## Common Errors

### ❌ "Failed to fetch public key"
**Solution:** Make sure your auth service is running at `http://localhost:3020` (or whatever URL you configured)

### ❌ "401 Unauthorized"
**Solution:** 
- Make sure you're including the `Authorization: Bearer <token>` header
- Verify your token is not expired
- Check that token was issued by the correct auth service

### ❌ "Invalid token signature"
**Solution:** The token's signature doesn't match the public key. Ensure:
- Token is from the correct auth service
- Auth service public key hasn't changed
- Token hasn't been tampered with

## Changing Auth Service URL

To point to a different auth service:

1. Stop the application
2. Edit `application.properties`:
   ```properties
   auth.service.base-url=http://your-new-auth-service:port
   ```
3. Restart the application

No code changes needed! 🎉

## Getting JWT Tokens

You need to obtain JWT tokens from your authentication service. Typically:

1. **Login endpoint**: POST to your auth service with credentials
2. **Receive JWT**: Auth service returns a JWT token
3. **Use JWT**: Include in `Authorization: Bearer <token>` header

Example (adjust based on your auth service):
```bash
# Login and get token
curl -X POST http://localhost:3020/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "user@example.com", "password": "password123"}' \
  | jq -r '.token'
```

## For Developers

### Accessing User Info in Code

```java
@GetMapping("/example")
public ResponseEntity<?> example(@AuthenticationPrincipal Jwt jwt) {
    String userId = jwt.getSubject();
    String email = jwt.getClaim("email");
    List<String> roles = jwt.getClaim("roles");
    
    // Use user info in your logic
    return ResponseEntity.ok("Hello, " + email);
}
```

### Role-Based Authorization

To require specific roles for endpoints, update `SecurityConfig.java`:

```java
.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
.requestMatchers("/api/v1/reports/**").hasAnyRole("MANAGER", "ADMIN")
```

## Troubleshooting Checklist

- [ ] Auth service is running
- [ ] Auth service URL is correct in `application.properties`
- [ ] Public key endpoint is accessible
- [ ] JWT token is valid and not expired
- [ ] Authorization header format is correct: `Bearer <token>`
- [ ] Token was issued by the configured auth service

## Additional Resources

- 📖 Full documentation: `JWT_AUTHENTICATION_GUIDE.md`
- 📖 Swagger documentation: `SWAGGER_DOCUMENTATION.md`
- 🔧 Configuration file: `src/main/resources/application.properties`

---

**Need Help?** Check the application logs for detailed error messages.
