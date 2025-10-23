# JWT Authentication Implementation Guide

## Overview
This application now implements JWT (JSON Web Token) based authentication using RSA public key validation. The application acts as a stateless resource server that validates tokens issued by an external authentication service.

## Architecture

### Authentication Flow
1. Client obtains JWT token from the authentication service (http://localhost:3020)
2. Client includes the JWT in the `Authorization` header as `Bearer <token>`
3. FD Module validates the JWT using the public key fetched from the auth service
4. If valid, the request is processed; otherwise, a 401 Unauthorized response is returned

### Stateless Design
- No session management
- No server-side token storage
- Each request is independently authenticated
- Scalable and cloud-ready

## Configuration

### Application Properties

The authentication service URL is configurable in `application.properties`:

```properties
# Base URL for the authentication service
auth.service.base-url=http://localhost:3020

# Public key endpoint path
auth.service.public-key-path=/api/auth/public-key
```

**To change the auth service URL:**
Simply update `auth.service.base-url` in `application.properties` - no code changes needed!

### Public Key Endpoint

The auth service must expose a public key endpoint that returns:

```json
{
  "keys": [
    {
      "kty": "RSA",
      "e": "AQAB",
      "use": "sig",
      "kid": "nexabank-auth-key-1",
      "alg": "RS256",
      "n": "AM4VMDVb9jk9giR_VufzHiu..."
    }
  ]
}
```

## Security Rules

### Protected Endpoints
All API endpoints under `/api/v1/**` require authentication:
- `/api/v1/accounts/**` - Account management (protected)
- `/api/v1/reports/**` - Reporting (protected)
- `/api/v1/jobs/**` - Batch jobs (protected)

### Public Endpoints
The following endpoints are publicly accessible:
- `/swagger-ui/**` - Swagger UI interface
- `/api-docs/**` - OpenAPI documentation
- `/v3/api-docs/**` - OpenAPI v3 documentation
- `/actuator/**` - Spring Boot actuator endpoints

## Making Authenticated Requests

### Using cURL

```bash
curl -X GET http://localhost:8080/api/v1/accounts/search?idType=accountNumber&value=FD2025001234 \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
```

### Using Postman

1. Select your request
2. Go to the "Authorization" tab
3. Select "Bearer Token" from the Type dropdown
4. Paste your JWT token in the Token field
5. Send the request

### Using Swagger UI

1. Navigate to http://localhost:8080/swagger-ui.html
2. Click the "Authorize" button (lock icon) at the top
3. Enter your JWT token in the format: `Bearer <your-token>`
4. Click "Authorize"
5. All subsequent requests will include the token

### Using JavaScript/Fetch

```javascript
fetch('http://localhost:8080/api/v1/accounts/search?idType=accountNumber&value=FD2025001234', {
  method: 'GET',
  headers: {
    'Authorization': 'Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...',
    'Content-Type': 'application/json'
  }
})
.then(response => response.json())
.then(data => console.log(data));
```

## JWT Token Structure

### Expected Claims
Your JWT should contain:
- `sub`: Subject (user identifier)
- `exp`: Expiration time
- `iat`: Issued at time
- `roles`: Array of user roles (optional, for authorization)

Example JWT payload:
```json
{
  "sub": "user123",
  "name": "John Doe",
  "email": "john.doe@nexusbank.com",
  "roles": ["CUSTOMER", "PREMIUM_CUSTOMER"],
  "exp": 1729699200,
  "iat": 1729695600
}
```

## Error Responses

### 401 Unauthorized
Returned when:
- No JWT token is provided
- JWT token is invalid or malformed
- JWT token is expired
- JWT signature verification fails

Example response:
```json
{
  "timestamp": "2025-10-22T10:30:00.000+00:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Full authentication is required to access this resource",
  "path": "/api/v1/accounts/search"
}
```

### 403 Forbidden
Returned when:
- JWT is valid but user lacks required permissions
- User role doesn't match endpoint requirements

## Components Overview

### 1. JwtConfigProperties
- Manages configuration from `application.properties`
- Provides the auth service URL
- Easy to update without code changes

### 2. JwtPublicKeyProvider
- Fetches RSA public key from the auth service
- Parses the JWK (JSON Web Key) format
- Constructs Java `RSAPublicKey` object
- Handles errors gracefully

### 3. SecurityConfig
- Configures Spring Security filter chain
- Defines protected and public endpoints
- Sets up JWT decoder with public key
- Configures JWT to Spring Security authorities conversion
- Enforces stateless session management

### 4. OpenApiConfig
- Updated to include JWT security scheme
- Swagger UI now shows "Authorize" button
- Documents that endpoints require authentication

## Testing

### 1. Start the Application
```bash
./mvnw spring-boot:run
```

### 2. Verify Public Endpoints (No Token Required)
```bash
# Should return Swagger UI
curl http://localhost:8080/swagger-ui.html

# Should return OpenAPI spec
curl http://localhost:8080/api-docs
```

### 3. Test Protected Endpoints Without Token
```bash
# Should return 401 Unauthorized
curl http://localhost:8080/api/v1/accounts/search?idType=accountNumber&value=TEST
```

### 4. Test Protected Endpoints With Valid Token
```bash
# Should return account data
curl -H "Authorization: Bearer <valid-jwt-token>" \
  http://localhost:8080/api/v1/accounts/search?idType=accountNumber&value=TEST
```

## Troubleshooting

### Problem: "Failed to fetch public key"
**Solution:**
- Ensure auth service is running at the configured URL
- Check `auth.service.base-url` in application.properties
- Verify network connectivity to auth service
- Check auth service logs for errors

### Problem: "Invalid token" or "Token verification failed"
**Solution:**
- Ensure token is from the correct auth service
- Check token expiration
- Verify token format (should be Bearer <token>)
- Ensure public key from auth service matches the private key used to sign the token

### Problem: "403 Forbidden" despite valid token
**Solution:**
- Check that user has required roles
- Verify JWT contains 'roles' claim
- Review SecurityConfig authorization rules

### Problem: Swagger UI not accessible
**Solution:**
- Check that `/swagger-ui/**` is in the public endpoints list
- Clear browser cache
- Try accessing directly: http://localhost:8080/swagger-ui/index.html

## Advanced Configuration

### Adding Role-Based Access Control

To protect specific endpoints based on roles:

```java
.requestMatchers("/api/v1/accounts/**").hasRole("CUSTOMER")
.requestMatchers("/api/v1/reports/**").hasRole("MANAGER")
.requestMatchers("/api/v1/jobs/**").hasRole("ADMIN")
```

### Customizing JWT Claims

Modify `jwtAuthenticationConverter()` in SecurityConfig to extract custom claims:

```java
@Bean
public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
    grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");
    grantedAuthoritiesConverter.setAuthoritiesClaimName("roles"); // Your custom claim name

    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
    return converter;
}
```

### Refreshing Public Key

Currently, the public key is fetched on application startup. To refresh periodically:

1. Use `@Scheduled` to fetch the key at intervals
2. Implement caching with TTL
3. Use Spring Cache with expiration

## Production Considerations

### 1. HTTPS/TLS
- Always use HTTPS in production
- Encrypt all token transmission
- Validate SSL certificates

### 2. Token Expiration
- Keep token lifetime short (15-30 minutes)
- Implement token refresh mechanism
- Handle expired token gracefully

### 3. Public Key Caching
- Cache the public key to reduce calls to auth service
- Implement cache invalidation strategy
- Handle key rotation

### 4. Monitoring
- Log authentication failures
- Monitor token validation errors
- Set up alerts for unusual patterns

### 5. Rate Limiting
- Implement rate limiting on endpoints
- Protect against brute force attacks
- Use tools like Spring Cloud Gateway or bucket4j

## Migration from Old System

If you had a previous authentication system:

1. Update client applications to obtain JWT from new auth service
2. Update all API calls to include `Authorization: Bearer <token>` header
3. Remove old authentication headers/cookies
4. Test thoroughly in staging environment
5. Deploy with backward compatibility period if needed

## Dependencies Added

```xml
<!-- JWT OAuth2 Resource Server Support -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>

<!-- Nimbus JOSE + JWT for JWT processing -->
<dependency>
    <groupId>com.nimbusds</groupId>
    <artifactId>nimbus-jose-jwt</artifactId>
    <version>9.37.3</version>
</dependency>
```

## Support and Contact

For issues or questions:
- Check application logs for detailed error messages
- Review Spring Security documentation
- Contact the authentication service team for token issues
- Review Nexus Bank's security policies

---

**Security Note:** Never commit JWT tokens to version control. Always use environment variables or secure vaults for sensitive configuration.
