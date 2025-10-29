package com.example.demo.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.AddAccountHolderRequest;
import com.example.demo.dto.CreateFDAccountRequest;
import com.example.demo.dto.EarlyWithdrawlRequest;
import com.example.demo.dto.FDAccountView;
import com.example.demo.dto.FDTransactionView;
import com.example.demo.dto.FdAccountBalanceView;
import com.example.demo.dto.PrematureWithdrawalInquiryResponse;
import com.example.demo.dto.StatementRequest;
import com.example.demo.service.CustomerService;
import com.example.demo.service.FDAccountService;
import com.example.demo.service.StatementService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "FD Account Management", 
     description = """
        Comprehensive API for managing Fixed Deposit (FD) accounts throughout their complete lifecycle.
        
        ## Core Features
        - **Account Creation**: Create FD accounts with dynamic product configuration
        - **Account Holder Management**: Add owners, co-owners, nominees, guardians
        - **Transaction History**: View complete transaction timeline
        - **Premature Withdrawal**: Calculate penalties and process early closures
        - **Search & Retrieval**: Find accounts by multiple criteria
        
        ## Account Creation Process
        1. User obtains JWT token from Authentication Service (contains customer email in 'sub' claim)
        2. User creates FD calculation via Calculation Service (returns calcId)
        3. User calls POST /api/v1/accounts with JWT token and calcId
        4. System extracts customer email from JWT token automatically
        5. System calls Customer Service to get customer profile and customerNumber
        6. System fetches calculation details (maturity date, interest rate, product code)
        7. System fetches product configuration (balances, roles, communications)
        8. System creates account with all balance types (FD_PRINCIPAL, FD_INTEREST, PENALTY)
        9. System publishes events to Kafka (account creation, communications)
        10. System returns created account details
        
        ## Role Management
        Products define which roles are allowed. Common roles:
        - **OWNER**: Primary account owner (required, from JWT)
        - **CO_OWNER**: Joint account owner
        - **NOMINEE**: Beneficiary designation
        - **GUARDIAN**: For minor accounts
        
        ## Transaction Types
        All transactions are tracked with:
        - Transaction type (DEPOSIT, WITHDRAWAL, INTEREST_ACCRUED, PENALTY_DEBIT)
        - Amount and date
        - Transaction reference (UUID)
        - Description
        
        ## Search Capabilities
        Find accounts by:
        - **accountNumber**: Exact account number match
        - **customerId**: All accounts for a customer
        - **productCode**: All accounts for a product type
        
        ## Authentication
        All endpoints require JWT Bearer token in Authorization header.
        Customer email is automatically extracted from token's 'sub' claim and used to fetch customer profile.
        """)


public class FdAccountController {
	
	
	

	
	
	 private  FDAccountService fdAccountService;
	 private  CustomerService customerService;
	 private  StatementService statementService;

	public FdAccountController(FDAccountService fdAccountService, CustomerService customerService, StatementService statementService) {
		this.fdAccountService = fdAccountService;
		this.customerService = customerService;
		this.statementService = statementService;
	    }

    @Operation(
        summary = "Create a new Fixed Deposit account",
        description = """
            Create a new Fixed Deposit account with comprehensive configuration from external services.
            
            **What This Endpoint Does:**
            1. Extracts customer email from JWT token's 'sub' claim (automatic, no manual input needed)
            2. Calls Customer Service to get customer profile and customerNumber
            3. Fetches calculation details from FD Calculation Service using calcId
            4. Fetches product configuration from Product & Pricing Service
            5. Creates FD account with calculated maturity values
            6. Creates balance types based on product configuration (FD_PRINCIPAL, FD_INTEREST, PENALTY)
            7. Publishes account creation event to Kafka
            8. Sends communication events (Email/SMS) based on product templates
            9. Returns complete account details
            
            **Request Body:**
            - `accountName`: Display name for the account (e.g., "My Retirement Fund")
            - `calcId`: Calculation ID from FD Calculation Service (obtained from /api/fd/calculations)
            
            **JWT Token Requirements:**
            - Must contain 'sub' claim with customer email
            - Must be valid and not expired
            - Obtained from Authentication Service at http://localhost:3020
            
            **External Service Calls:**
            This endpoint integrates with:
            1. **Customer Service** (http://localhost:1005)
               - Fetches: customer profile, customerNumber (business identifier)
            2. **FD Calculation Service** (http://localhost:4030)
               - Fetches: maturity value, maturity date, interest rates, product code
            3. **Product & Pricing Service** (http://localhost:8080)
               - Fetches: balance types, allowed roles, transaction types, communication templates, penalty charges
            
            **What Gets Created:**
            - FD Account entity with all details
            - Account holder entry (OWNER role) using customerNumber from Customer Service
            - Initial deposit transaction (PRINCIPAL_DEPOSIT)
            - Multiple balance entries:
              * FD_PRINCIPAL: Set to principal amount
              * FD_INTEREST: Initialized to 0
              * PENALTY: Initialized to 0
            
            **Kafka Events Published:**
            - **fd.account.created**: Account creation notification
            - **fd.communication**: Customer notifications (based on product config)
              * COMM_OPENING event via SMS/Email
              * Uses templates from product configuration
              * Variables substituted: ${CUSTOMER_NAME}, ${ACCOUNT_NUMBER}, ${DATE}, etc.
            
            **Use Cases:**
            
            **Scenario 1: Standard FD Account Creation**
            - Customer completes FD calculation in UI
            - Receives calcId: 12345
            - Calls this endpoint with accountName and calcId
            - System creates account with customer as OWNER
            - Customer receives SMS/Email confirmation
            
            **Scenario 2: Multiple Accounts for Same Customer**
            - Customer already has one FD account
            - Creates another calculation for different term/amount
            - Calls endpoint with new calcId
            - System creates second account linked to same customer ID
            
            **Scenario 3: Product-Specific Configuration**
            - Different products (FD001, FD002) have different:
              * Balance types
              * Communication templates
              * Penalty structures
            - System automatically applies correct configuration
            
            **Related Endpoints:**
            - POST /api/fd/calculations - Create calculation first to get calcId
            - GET /api/products/{code} - View product configuration
            - GET /api/v1/accounts/search - Find created accounts
            - POST /api/v1/accounts/{accountNumber}/roles - Add co-owners/nominees
            
            **Error Scenarios:**
            - **400 Bad Request**: Invalid calcId or calculation not found
            - **401 Unauthorized**: Missing or invalid JWT token
            - **404 Not Found**: Product not found for product code from calculation
            - **500 Internal Error**: External service unavailable (Calculation or Product Service)
            
            **Example Request:**
            ```json
            POST /api/v1/accounts
            Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
            
            {
              "accountName": "My Retirement FD",
              "calcId": 12345
            }
            ```
            
            **Example Response:**
            ```json
            {
              "accountNumber": "FD202510220001",
              "accountName": "My Retirement FD",
              "productCode": "FD001",
              "status": "ACTIVE",
              "principalAmount": 100000.00,
              "maturityAmount": 108500.00,
              "interestRate": 8.0,
              "termInMonths": 12,
              "effectiveDate": "2025-10-22",
              "maturityDate": "2026-10-22",
              "accountHolders": [
                {
                  "customerId": "CUST123456",
                  "roleType": "OWNER",
                  "ownershipPercentage": 100.00
                }
              ]
            }
            ```
            
            **Important Notes:**
            - Customer ID is NEVER in request body - always extracted from JWT
            - Product code comes from calculation service - not in request
            - Interest rate, term, maturity details from calculation service
            - Balance types created automatically based on product configuration
            - This is an asynchronous operation - Kafka events published after response
            """
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Account created successfully with all balances and events published"),
        @ApiResponse(responseCode = "400", description = "Invalid input: calcId not found or invalid accountName"),
        @ApiResponse(responseCode = "401", description = "Unauthorized: Invalid or missing JWT token"),
        @ApiResponse(responseCode = "404", description = "Product not found for product code from calculation"),
        @ApiResponse(responseCode = "500", description = "Internal server error: External service unavailable")
    })
    @PostMapping
    public ResponseEntity<FDAccountView> createFdAccount(
            @Valid @RequestBody 
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = """
                    Account creation request containing:
                    - **accountName**: Display name for the account (3-100 characters)
                    - **calcId**: Calculation ID from FD Calculation Service (positive integer)
                    
                    **Note**: Customer ID is automatically extracted from JWT token's 'sub' claim.
                    Do NOT include customer ID in request body.
                    """,
                required = true
            )
            CreateFDAccountRequest request,
            @Parameter(
                description = "JWT token containing customer email in 'sub' claim. Obtained from Authentication Service.",
                required = true,
                hidden = true
            )
            @AuthenticationPrincipal Jwt jwt) {
        
        // Extract email from JWT token (sub claim)
        String email = jwt.getSubject();
        
        // Get the JWT token value
        String jwtToken = jwt.getTokenValue();
        
        // Fetch customer profile from Customer Service using email
        // and get customerNumber (e.g., CUST-20251024-000001)
        String customerNumber = customerService.getCustomerNumberByEmail(email, jwtToken);
        
        FDAccountView createdAccount = fdAccountService.createAccount(request, customerNumber);
        return new ResponseEntity<>(createdAccount, HttpStatus.CREATED);
    }

    @Operation(
        summary = "Search for FD accounts by various criteria",
        description = """
            Flexible search API to find FD accounts using different identification types and values.
            
            **Supported Search Types:**
            
            1. **accountNumber** - Find specific account by account number
               - Returns: Single account (or empty if not found)
               - Use case: Customer inquiry, account lookup
               - Example: idType=accountNumber&value=FD202510220001
            
            2. **customerId** - Find all accounts for a customer
               - Returns: List of all FD accounts owned/associated with customer
               - Use case: Customer portfolio view, relationship manager dashboard
               - Example: idType=customerId&value=CUST123456
            
            3. **productCode** - Find all accounts for a product type
               - Returns: All accounts using specific product
               - Use case: Product performance analysis, bulk operations
               - Example: idType=productCode&value=FD001
            
            **Response Details:**
            Each account in response includes:
            - Complete account details (number, name, status, amounts)
            - All account holders with roles and ownership percentages
            - Product information
            - Maturity details
            - Current balances (if applicable)
            
            **Use Cases:**
            
            **Scenario 1: Customer Service Representative Lookup**
            - Customer calls with account number
            - Rep searches: idType=accountNumber&value=FD202510220001
            - Gets complete account details instantly
            - Can view holders, transactions, status
            
            **Scenario 2: Customer Portfolio View**
            - Customer logs into internet banking
            - System searches: idType=customerId&value=<from JWT>
            - Returns all FD accounts for customer
            - Shows total holdings, maturity dates
            
            **Scenario 3: Product Manager Analytics**
            - Product manager wants FD001 performance
            - Searches: idType=productCode&value=FD001
            - Gets all FD001 accounts
            - Analyzes total deposits, average terms
            
            **Scenario 4: Relationship Manager Dashboard**
            - RM manages multiple customers
            - Searches each customer's accounts
            - Builds comprehensive portfolio view
            - Monitors maturity schedules
            
            **Search Tips:**
            - Account numbers are case-sensitive
            - Customer IDs must match exactly
            - Product codes are case-sensitive
            - Empty result returns 404 Not Found
            - Large result sets returned in single response (no pagination yet)
            
            **Related Endpoints:**
            - POST /api/v1/accounts - Create new account
            - GET /api/v1/accounts/{accountNumber}/transactions - View account transactions
            - POST /api/v1/accounts/{accountNumber}/roles - Add account holders
            
            **Performance Considerations:**
            - Account number search: O(1) - indexed lookup
            - Customer ID search: O(n) - filtered scan, consider pagination for large datasets
            - Product code search: O(n) - filtered scan
            
            **Example Requests:**
            ```
            # Find specific account
            GET /api/v1/accounts/search?idType=accountNumber&value=FD202510220001
            
            # Find all customer accounts
            GET /api/v1/accounts/search?idType=customerId&value=CUST123456
            
            # Find all accounts for product
            GET /api/v1/accounts/search?idType=productCode&value=FD001
            ```
            
            **Example Response (Customer ID search):**
            ```json
            [
              {
                "accountNumber": "FD202510220001",
                "accountName": "My Retirement FD",
                "productCode": "FD001",
                "status": "ACTIVE",
                "principalAmount": 100000.00,
                "maturityAmount": 108500.00
              },
              {
                "accountNumber": "FD202510220002",
                "accountName": "Emergency Fund FD",
                "productCode": "FD002",
                "status": "ACTIVE",
                "principalAmount": 50000.00,
                "maturityAmount": 53250.00
              }
            ]
            ```
            """
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Accounts found successfully. Returns list of matching accounts."),
        @ApiResponse(responseCode = "404", description = "No accounts found matching search criteria"),
        @ApiResponse(responseCode = "400", description = "Invalid search parameters: unsupported idType or missing value"),
        @ApiResponse(responseCode = "401", description = "Unauthorized: Missing or invalid JWT token")
    })
    @GetMapping("/search")
    public ResponseEntity<List<FDAccountView>> findAccounts(
            @Parameter(
                description = """
                    Type of identifier to search by. Supported values:
                    - **accountNumber**: Search by exact account number
                    - **customerId**: Find all accounts for a customer
                    - **productCode**: Find all accounts using a product
                    """,
                required = true,
                example = "customerId"
            )
            @RequestParam("idType") String idType,
            @Parameter(
                description = "Value of the identifier to search for. Must match the idType selected.",
                required = true,
                example = "CUST123456"
            )
            @RequestParam("value") String value) {
        List<FDAccountView> accounts = fdAccountService.findAccounts(idType, value);
        if (accounts.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(accounts);
    }
    
    @Operation(summary = "Add account holder role", description = "Add a new account holder with a specific role to an existing FD account")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Role added successfully"),
        @ApiResponse(responseCode = "404", description = "Account not found"),
        @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PostMapping("/{accountNumber}/roles")
    public ResponseEntity<FDAccountView> addRoleToAccount(
            @Parameter(description = "FD Account number", required = true)
            @PathVariable String accountNumber,
            @Valid @RequestBody AddAccountHolderRequest request) {
        FDAccountView updatedAccount = fdAccountService.addRoleToAccount(accountNumber, request);
        return ResponseEntity.ok(updatedAccount);
    }
    
    
    
    @Operation(summary = "Get account transactions", description = "Retrieve all transactions for a specific FD account")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Transactions retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Account not found")
    })
    @GetMapping("/{accountNumber}/transactions")
    public ResponseEntity<List<FDTransactionView>> getAccountTransactions(
            @Parameter(description = "FD Account number", required = true)
            @PathVariable String accountNumber) {
        List<FDTransactionView> transactions = fdAccountService.getTransactionsForAccount(accountNumber);
        return ResponseEntity.ok(transactions);
    }
    
   
   @Operation(summary = "Get premature withdrawal inquiry", description = "Get details about penalties and amounts for premature withdrawal")
   @ApiResponses(value = {
       @ApiResponse(responseCode = "200", description = "Inquiry details retrieved successfully"),
       @ApiResponse(responseCode = "404", description = "Account not found")
   })
   @GetMapping("/{accountNumber}/withdrawal-inquiry")
   public ResponseEntity<PrematureWithdrawalInquiryResponse> getWithdrawalInquiry(
           @Parameter(description = "FD Account number", required = true)
           @PathVariable("accountNumber") String accountNumber) {
       PrematureWithdrawalInquiryResponse inquiryResponse = fdAccountService.getPrematureWithdrawalInquiry(accountNumber);
       return ResponseEntity.ok(inquiryResponse);
   }

   @Operation(summary = "Perform early withdrawal", description = "Execute premature withdrawal and close the FD account")
   @ApiResponses(value = {
       @ApiResponse(responseCode = "200", description = "Early withdrawal completed successfully"),
       @ApiResponse(responseCode = "404", description = "Account not found"),
       @ApiResponse(responseCode = "400", description = "Invalid withdrawal request")
   })
   @PostMapping("/{accountNumber}/withdrawal")
   public ResponseEntity<FDAccountView> performEarlyWithdrawal(
           @Parameter(description = "FD Account number", required = true)
		   @PathVariable("accountNumber") String accountNumber,
           @RequestBody EarlyWithdrawlRequest request) {
       FDAccountView closedAccount = fdAccountService.performEarlyWithdrawal(accountNumber, request);
       return ResponseEntity.ok(closedAccount);
   }

   @Operation(
       summary = "Get account balances",
       description = "Retrieve all balance types (FD_PRINCIPAL, FD_INTEREST, PENALTY) for an FD account"
   )
   @ApiResponses(value = {
       @ApiResponse(
           responseCode = "200",
           description = "Balances retrieved successfully",
           content = @Content(
               mediaType = "application/json",
               schema = @Schema(implementation = FdAccountBalanceView.class),
               examples = @ExampleObject(
                   name = "Account Balances",
                   value = """
                   [
                       {
                           "balanceType": "FD_PRINCIPAL",
                           "balanceAmount": 100000.00,
                           "isActive": true,
                           "createdAt": "2024-01-15T10:30:00",
                           "updatedAt": "2024-01-15T10:30:00"
                       },
                       {
                           "balanceType": "FD_INTEREST",
                           "balanceAmount": 5250.00,
                           "isActive": true,
                           "createdAt": "2024-01-15T10:30:00",
                           "updatedAt": "2024-03-15T00:00:00"
                       }
                   ]
                   """
               )
           )
       ),
       @ApiResponse(responseCode = "404", description = "Account not found")
   })
   @GetMapping("/{accountNumber}/balances")
   public ResponseEntity<List<FdAccountBalanceView>> getAccountBalances(
           @Parameter(description = "FD Account number", required = true, example = "FD202401150001")
           @PathVariable("accountNumber") String accountNumber) {
       List<FdAccountBalanceView> balances = fdAccountService.getAccountBalances(accountNumber);
       return ResponseEntity.ok(balances);
   }

   @Operation(
       summary = "Generate account statement",
       description = "Generate and send FD account statement via Kafka to notification service for the specified period"
   )
   @ApiResponses(value = {
       @ApiResponse(
           responseCode = "200",
           description = "Statement generation request accepted",
           content = @Content(
               mediaType = "application/json",
               examples = @ExampleObject(
                   name = "Success Response",
                   value = """
                   {
                       "message": "Statement generation request accepted for account FD202401150001"
                   }
                   """
               )
           )
       ),
       @ApiResponse(responseCode = "404", description = "Account not found"),
       @ApiResponse(responseCode = "400", description = "Invalid date range")
   })
   @PostMapping("/{accountNumber}/statement")
   public ResponseEntity<Map<String, String>> generateStatement(
           @Parameter(description = "FD Account number", required = true, example = "FD202401150001")
           @PathVariable("accountNumber") String accountNumber,
           @Parameter(description = "Statement period details", required = true)
           @Valid @RequestBody StatementRequest request,
           @AuthenticationPrincipal Jwt jwt) {
       
       // Extract user email from JWT
       String email = jwt.getSubject();
       
       // Get JWT token value
       String jwtToken = jwt.getTokenValue();
       
       // Generate statement
       statementService.generateStatement(accountNumber, request.startDate(), request.endDate(), email, jwtToken);
       
       Map<String, String> response = new HashMap<>();
       response.put("message", "Statement generation request accepted for account " + accountNumber);
       
       return ResponseEntity.ok(response);
   }

}
