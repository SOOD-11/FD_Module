# Swagger API Documentation

This project now includes comprehensive API documentation using Swagger/OpenAPI 3.0.

## Accessing Swagger UI

Once your application is running, you can access the Swagger UI at:

**http://localhost:8080/swagger-ui.html**

## Alternative Documentation URLs

- **OpenAPI JSON specification**: http://localhost:8080/api-docs
- **OpenAPI YAML specification**: http://localhost:8080/api-docs.yaml

## Features

The Swagger documentation includes:

### 1. **FD Account Management APIs** (`/api/v1/accounts`)
   - Create new FD accounts
   - Search accounts by various criteria
   - Add account holders with different roles
   - View transaction history
   - Perform premature withdrawal inquiries
   - Execute early withdrawals

### 2. **FD Reports APIs** (`/api/v1/reports`)
   - Get accounts maturing within specified days
   - Get accounts created between dates
   - Get accounts closed between dates (with optional status filter)

### 3. **Batch Jobs APIs** (`/api/v1/jobs`)
   - Manually trigger interest calculation job
   - Manually trigger maturity processing job

## Using Swagger UI

1. **Start your application**: Make sure your Spring Boot application is running
2. **Open Swagger UI**: Navigate to http://localhost:8080/swagger-ui.html in your browser
3. **Explore APIs**: 
   - Click on any endpoint to see detailed documentation
   - View request/response schemas
   - See example values
4. **Test APIs**: 
   - Click "Try it out" button
   - Fill in the required parameters
   - Click "Execute" to send the request
   - View the response

## Customization

The Swagger configuration can be customized in:
- **OpenApiConfig.java**: Main Swagger configuration (title, description, contact info)
- **application.properties**: Swagger UI paths and behavior settings

## Security Note

Currently, all endpoints are accessible without authentication as per your SecurityConfig. In production, you should implement proper authentication and authorization.

## Dependencies

The following dependency has been added to enable Swagger:

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

## Annotations Used

- `@Tag`: Groups related endpoints together
- `@Operation`: Describes individual API operations
- `@ApiResponses` / `@ApiResponse`: Documents possible HTTP responses
- `@Parameter`: Describes request parameters with examples

## Next Steps

To enhance your API documentation:
1. Add more detailed examples in DTOs using `@Schema` annotations
2. Document authentication requirements (when implemented)
3. Add more comprehensive error response examples
4. Include API versioning documentation
