package com.example.demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fdModuleOpenAPI() {
        // Development Server
        Server devServer = new Server();
        devServer.setUrl("http://localhost:9090");
        devServer.setDescription("Local Development Server");

        // UAT Server
        Server uatServer = new Server();
        uatServer.setUrl("https://api-uat.nexusbank.com");
        uatServer.setDescription("User Acceptance Testing Environment");

        // Production Server
        Server prodServer = new Server();
        prodServer.setUrl("https://api.nexusbank.com");
        prodServer.setDescription("Production Environment");

        Contact contact = new Contact();
        contact.setName("Nexus Bank Development Team");
        contact.setEmail("api-support@nexusbank.com");
        contact.setUrl("https://www.nexusbank.com/developer-support");

        License license = new License()
                .name("Apache License 2.0")
                .url("https://www.apache.org/licenses/LICENSE-2.0.html");

        Info info = new Info()
                .title("Fixed Deposit Account Management API")
                .version("1.0.0")
                .contact(contact)
                .description(buildApiDescription())
                .license(license);

        // Define security scheme for JWT
        SecurityScheme securityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization")
                .description("JWT token obtained from the authentication service");

        // Define security requirement
        SecurityRequirement securityRequirement = new SecurityRequirement()
                .addList("Bearer Authentication");

        return new OpenAPI()
                .info(info)
                .servers(List.of(devServer, uatServer, prodServer))
                .addSecurityItem(securityRequirement)
                .schemaRequirement("Bearer Authentication", securityScheme);
    }

    /**
     * Build comprehensive API description with Markdown formatting
     */
    private String buildApiDescription() {
        return """
                # Comprehensive Fixed Deposit Account Management System
                
                ## Overview
                Enterprise-grade RESTful API for managing Fixed Deposit (FD) accounts in banking and financial institutions. 
                This system provides complete lifecycle management for FD accounts including account creation, holder management, 
                transaction processing, maturity handling, and comprehensive reporting capabilities.
                
                ## Key Features
                - **Account Management**: Create, retrieve, update, and search FD accounts with complete configuration
                - **Multi-Holder Support**: Add owners, co-owners, nominees, and guardians to accounts
                - **Transaction Processing**: Handle deposits, interest accrual, premature withdrawals with penalty calculation
                - **Automated Maturity**: Batch job processing for matured accounts with configurable payout instructions
                - **Interest Calculation**: Support for simple and compound interest with configurable compounding frequency
                - **Product Integration**: Dynamic configuration from Product & Pricing Service for balances, roles, transactions
                - **Real-time Events**: Kafka-based event streaming for account creation, maturity, and closure
                - **Communication Management**: Multi-channel notifications (Email, SMS, Push) based on product configuration
                - **JWT Security**: Stateless authentication with RSA public key validation
                - **Comprehensive Reporting**: Excel export with account holder details and transaction history
                
                ## API Design Principles
                - **RESTful Architecture**: Standard HTTP methods (GET, POST, PUT, DELETE)
                - **JWT Authentication**: Stateless security with customer ID extraction from token
                - **External Service Integration**: Seamless integration with Auth, Calculation, and Product services
                - **Event-Driven Architecture**: Kafka events for account lifecycle and communications
                - **Pagination Support**: Efficient data retrieval for large datasets
                - **Comprehensive Validation**: Request validation using Jakarta Bean Validation
                - **Audit Trail**: Automatic tracking of transactions and account changes
                - **Error Handling**: Standardized error responses with meaningful messages
                
                ## Response Codes
                - **200 OK**: Successful GET/PUT request
                - **201 Created**: Successful POST request with resource creation
                - **204 No Content**: Successful DELETE request
                - **400 Bad Request**: Invalid request data or validation failure
                - **401 Unauthorized**: Missing or invalid JWT token
                - **403 Forbidden**: Valid token but insufficient permissions
                - **404 Not Found**: Resource not found
                - **409 Conflict**: Business rule violation or duplicate resource
                - **500 Internal Server Error**: Server-side error
                
                ## Authentication
                All API endpoints require JWT authentication. Include the JWT token in the Authorization header:
                ```
                Authorization: Bearer <your-jwt-token>
                ```
                
                The JWT token must contain:
                - **sub**: Customer ID (extracted for account operations)
                - **iat**: Issued at timestamp
                - **exp**: Expiration timestamp
                
                JWT tokens are validated using RSA public key obtained from: `http://localhost:3020/api/auth/public-key`
                
                ## External Service Dependencies
                
                ### 1. Authentication Service
                - **URL**: `http://localhost:3020`
                - **Purpose**: JWT public key provider for token validation
                - **Endpoint**: `/api/auth/public-key`
                
                ### 2. FD Calculation Service
                - **URL**: `http://localhost:4030`
                - **Purpose**: Calculate maturity values, interest rates, and payout schedules
                - **Endpoint**: `/api/fd/calculations/{calcId}`
                
                ### 3. Product & Pricing Service
                - **URL**: `http://localhost:8080`
                - **Purpose**: Product configuration including balances, roles, transactions, communications
                - **Endpoint**: `/api/products/{productCode}`
                
                ## Kafka Topics
                - **fd.account.created**: Published when new FD account is created
                - **fd.account.matured**: Published when FD account reaches maturity
                - **fd.account.closed**: Published when FD account is closed (premature or matured)
                - **fd.communication**: Published for customer communications (Email, SMS, Push notifications)
                
                ## Data Flow
                
                ### Account Creation Flow
                1. User provides JWT token (containing customer ID) and calculation ID
                2. System extracts customer ID from JWT token's 'sub' claim
                3. System calls FD Calculation Service to get maturity details
                4. System calls Product Service to get product configuration
                5. System creates FD account with calculated values
                6. System creates balance types (FD_PRINCIPAL, FD_INTEREST, PENALTY) from product config
                7. System publishes account creation event to Kafka
                8. System sends communication events (Email/SMS) based on product templates
                9. System returns created account details
                
                ### Premature Withdrawal Flow
                1. User requests withdrawal inquiry or performs withdrawal
                2. System validates transaction type against product configuration
                3. System calculates term completion percentage
                4. System determines penalty rate:
                   - If < 50% completion: Uses PEN-H-{productSuffix} (high penalty)
                   - If >= 50% completion: Uses PEN-L-{productSuffix} (low penalty)
                5. System calculates final payout amount
                6. If performing withdrawal: Creates penalty and withdrawal transactions
                7. System publishes account closure event
                8. System returns final payout details
                
                ## Product-Driven Configuration
                
                This API leverages product configuration from the Product & Pricing Service to enable:
                
                ### Balance Types
                Different FD products may have different balance types:
                - **FD_PRINCIPAL**: Principal deposit amount
                - **FD_INTEREST**: Accrued interest amount
                - **PENALTY**: Penalty charges for premature withdrawal
                
                ### Role Types
                Products define which roles are allowed:
                - **OWNER**: Primary account owner
                - **CO_OWNER**: Joint account owner
                - **NOMINEE**: Designated beneficiary
                - **GUARDIAN**: Legal guardian for minor accounts
                
                ### Transaction Types
                Products define allowed transactions:
                - **DEPOSIT**: Principal deposit
                - **WITHDRAWAL**: Premature withdrawal
                - **INTEREST_ACCRUED**: Automatic interest posting
                - **PENALTY_DEBIT**: Penalty charges
                
                ### Communication Templates
                Products define customer notifications:
                - **COMM_OPENING**: Account opening confirmation (SMS/Email)
                - **COMM_MONTHLY_STATEMENT**: Monthly statements (Email)
                - **COMM_MATURITY_REMINDER**: Maturity date reminders (SMS/Email)
                - **COMM_INTEREST_CREDIT**: Interest posting notifications
                
                ### Penalty Charges
                Products define penalty structures:
                - **PEN-H-{suffix}**: High penalty for < 50% term completion
                - **PEN-L-{suffix}**: Low penalty for >= 50% term completion
                - Supports **PERCENTAGE** (% of principal) or **FLAT** (fixed amount)
                
                ## Batch Processing
                
                ### Interest Calculation Job
                - **Schedule**: Daily at 00:01
                - **Purpose**: Calculate and credit daily interest to FD accounts
                - **Processing**: Iterates through all ACTIVE accounts and posts interest
                
                ### Maturity Processing Job
                - **Schedule**: Daily at 00:05
                - **Purpose**: Process matured FD accounts
                - **Actions**:
                  - Update account status to MATURED
                  - Apply maturity instructions (RENEW, PAYOUT, TRANSFER)
                  - Publish maturity events to Kafka
                
                ## Getting Started
                1. Obtain JWT token from Authentication Service
                2. Create FD calculation using Calculation Service
                3. Use calculation ID to create FD account via this API
                4. Add additional account holders if needed
                5. Monitor account via transaction history
                6. Handle maturity or premature withdrawal
                
                ## Support
                For technical support, API access requests, or documentation:
                - **Email**: api-support@nexusbank.com
                - **Developer Portal**: https://www.nexusbank.com/developers
                - **Status Page**: https://status.nexusbank.com
                
                ## Changelog
                - **v1.0.0** (2025-10-22): Initial release with comprehensive FD management
                """;
    }
}
