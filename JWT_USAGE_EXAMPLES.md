# JWT Usage Examples for Developers

## Overview
This document provides practical examples of how to work with JWT authentication in your FD Module controllers and services.

## Accessing User Information in Controllers

### Method 1: Using @AuthenticationPrincipal

```java
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

@GetMapping("/my-accounts")
public ResponseEntity<List<FDAccountView>> getMyAccounts(@AuthenticationPrincipal Jwt jwt) {
    String userId = jwt.getSubject();
    String email = jwt.getClaim("email");
    
    // Use the user info to fetch their accounts
    List<FDAccountView> accounts = fdAccountService.findAccountsByUserId(userId);
    return ResponseEntity.ok(accounts);
}
```

### Method 2: Using Authentication Object

```java
import org.springframework.security.core.Authentication;

@PostMapping("/accounts")
public ResponseEntity<FDAccountView> createAccount(
        @RequestBody CreateFDAccountRequest request,
        Authentication authentication) {
    
    String userId = authentication.getName();
    
    // Create account for the authenticated user
    FDAccountView account = fdAccountService.createAccount(request, userId);
    return ResponseEntity.ok(account);
}
```

### Method 3: Using JwtUtils (Static Methods)

```java
import com.example.demo.util.JwtUtils;

@GetMapping("/dashboard")
public ResponseEntity<DashboardView> getDashboard() {
    String userId = JwtUtils.getCurrentUserId();
    String email = JwtUtils.getCurrentUserEmail();
    String name = JwtUtils.getCurrentUserName();
    
    DashboardView dashboard = dashboardService.generateDashboard(userId);
    return ResponseEntity.ok(dashboard);
}
```

## Accessing User Information in Services

### Example: Audit Logging

```java
@Service
@RequiredArgsConstructor
public class FDAccountServiceImpl implements FDAccountService {

    @Override
    @Transactional
    public FDAccountView createAccount(CreateFDAccountRequest request) {
        // Get current user for audit logging
        String createdBy = JwtUtils.getCurrentUserId();
        String createdByEmail = JwtUtils.getCurrentUserEmail();
        
        FdAccount account = new FdAccount();
        account.setAccountName(request.accountName());
        account.setCreatedBy(createdBy);
        account.setCreatedByEmail(createdByEmail);
        account.setCreatedAt(LocalDateTime.now());
        
        // ... rest of the logic
        
        return toAccountView(account);
    }
}
```

### Example: Authorization Check

```java
@Override
public FDAccountView getAccount(String accountNumber) {
    FdAccount account = fdAccountRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    
    String currentUserId = JwtUtils.getCurrentUserId();
    
    // Check if user owns this account or has access
    if (!account.getAccountHolders().stream()
            .anyMatch(holder -> holder.getCustomerId().equals(currentUserId))) {
        throw new AccessDeniedException("You don't have access to this account");
    }
    
    return toAccountView(account);
}
```

## Reading Specific JWT Claims

### Example: Reading Custom Claims

```java
@GetMapping("/premium-features")
public ResponseEntity<?> getPremiumFeatures(@AuthenticationPrincipal Jwt jwt) {
    // Read custom claims from your JWT
    String accountTier = jwt.getClaim("accountTier");
    Boolean isPremium = jwt.getClaim("isPremium");
    List<String> permissions = jwt.getClaim("permissions");
    
    if (isPremium) {
        return ResponseEntity.ok(premiumService.getFeatures());
    } else {
        return ResponseEntity.status(403).body("Premium subscription required");
    }
}
```

### Example: Reading All Claims

```java
@GetMapping("/debug/token-info")
public ResponseEntity<Map<String, Object>> getTokenInfo(@AuthenticationPrincipal Jwt jwt) {
    Map<String, Object> tokenInfo = new HashMap<>();
    
    tokenInfo.put("subject", jwt.getSubject());
    tokenInfo.put("issuedAt", jwt.getIssuedAt());
    tokenInfo.put("expiresAt", jwt.getExpiresAt());
    tokenInfo.put("issuer", jwt.getIssuer());
    tokenInfo.put("allClaims", jwt.getClaims());
    
    return ResponseEntity.ok(tokenInfo);
}
```

## Role-Based Access Control

### Example: Checking Roles in Code

```java
@PostMapping("/admin/bulk-close")
public ResponseEntity<?> bulkCloseAccounts(@RequestBody List<String> accountNumbers) {
    // Check if user has admin role
    if (!JwtUtils.hasRole("ADMIN")) {
        return ResponseEntity.status(403).body("Admin access required");
    }
    
    accountService.bulkClose(accountNumbers);
    return ResponseEntity.ok("Accounts closed successfully");
}
```

### Example: Method-Level Security

```java
import org.springframework.security.access.prepost.PreAuthorize;

@Service
public class FDReportService {

    @PreAuthorize("hasRole('MANAGER')")
    public List<FDAccountView> getConfidentialReport() {
        // Only users with MANAGER role can call this
        return generateConfidentialReport();
    }
    
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public void deleteAccount(String accountNumber) {
        // Only ADMIN or MANAGER can delete accounts
        performDeletion(accountNumber);
    }
}
```

**Note:** To enable method-level security, add to your main application class:
```java
@EnableMethodSecurity
@SpringBootApplication
public class DemoApplication {
    // ...
}
```

## Working with Authorities/Roles

### Example: Getting User Roles

```java
@GetMapping("/my-permissions")
public ResponseEntity<?> getMyPermissions(Authentication authentication) {
    Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
    
    List<String> roles = authorities.stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toList());
    
    return ResponseEntity.ok(Map.of(
        "username", authentication.getName(),
        "roles", roles
    ));
}
```

### Example: Conditional Logic Based on Roles

```java
@GetMapping("/reports/financial")
public ResponseEntity<FinancialReport> getFinancialReport(@AuthenticationPrincipal Jwt jwt) {
    List<String> roles = jwt.getClaim("roles");
    
    FinancialReport report;
    if (roles.contains("ADMIN")) {
        // Return detailed report for admins
        report = reportService.getDetailedFinancialReport();
    } else if (roles.contains("MANAGER")) {
        // Return summary for managers
        report = reportService.getSummaryFinancialReport();
    } else {
        // Return basic report for others
        report = reportService.getBasicFinancialReport();
    }
    
    return ResponseEntity.ok(report);
}
```

## Exception Handling

### Example: Global Exception Handler for Auth Errors

```java
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(403).body(Map.of(
            "error", "Access Denied",
            "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<?> handleAuthenticationNotFound(
            AuthenticationCredentialsNotFoundException ex) {
        return ResponseEntity.status(401).body(Map.of(
            "error", "Unauthorized",
            "message", "Valid JWT token required"
        ));
    }
}
```

## Testing Controllers with JWT

### Example: Unit Test with Mocked JWT

```java
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@WebMvcTest(FdAccountController.class)
class FdAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FDAccountService fdAccountService;

    @Test
    @WithMockUser(username = "user123", roles = {"CUSTOMER"})
    void testCreateAccount() throws Exception {
        // Test with mocked user
        mockMvc.perform(post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{...}"))
                .andExpect(status().isCreated());
    }
}
```

### Example: Integration Test with Real JWT

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FdAccountIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void testGetAccountsWithJwt() {
        String jwtToken = getValidJwtToken(); // Get from your auth service
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtToken);
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        ResponseEntity<FDAccountView[]> response = restTemplate.exchange(
            "/api/v1/accounts/search?idType=customerId&value=CUST123",
            HttpMethod.GET,
            entity,
            FDAccountView[].class
        );
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
```

## Best Practices

### ✅ DO

1. **Use @AuthenticationPrincipal for controller methods:**
   ```java
   public ResponseEntity<?> method(@AuthenticationPrincipal Jwt jwt)
   ```

2. **Use JwtUtils in services for convenience:**
   ```java
   String userId = JwtUtils.getCurrentUserId();
   ```

3. **Validate user ownership of resources:**
   ```java
   if (!resource.getOwnerId().equals(currentUserId)) {
       throw new AccessDeniedException("Not your resource");
   }
   ```

4. **Log authentication events:**
   ```java
   log.info("User {} accessed account {}", JwtUtils.getCurrentUserId(), accountNumber);
   ```

### ❌ DON'T

1. **Don't trust client-provided user IDs in request body:**
   ```java
   // BAD - user could fake their ID
   @PostMapping
   public ResponseEntity<?> create(@RequestBody Request req) {
       service.create(req.getUserId()); // Don't do this!
   }
   
   // GOOD - use JWT userId
   @PostMapping
   public ResponseEntity<?> create(@RequestBody Request req, @AuthenticationPrincipal Jwt jwt) {
       service.create(jwt.getSubject()); // Use this instead
   }
   ```

2. **Don't cache JWT between requests:**
   ```java
   // BAD - JWT might expire
   private Jwt cachedJwt;
   
   // GOOD - get fresh JWT from SecurityContext each time
   Jwt jwt = JwtUtils.getCurrentJwt();
   ```

3. **Don't expose sensitive JWT claims:**
   ```java
   // BAD - might expose sensitive info
   return ResponseEntity.ok(jwt.getClaims());
   
   // GOOD - return only what's needed
   return ResponseEntity.ok(Map.of("name", jwt.getClaim("name")));
   ```

## Common Patterns

### Pattern 1: Audit Trail

```java
@Aspect
@Component
public class AuditAspect {
    
    @Before("@annotation(Audited)")
    public void logAccess(JoinPoint joinPoint) {
        String userId = JwtUtils.getCurrentUserId();
        String method = joinPoint.getSignature().getName();
        
        auditLog.log("User {} called {}", userId, method);
    }
}
```

### Pattern 2: Multi-Tenancy

```java
@Service
public class TenantAwareService {
    
    public List<FDAccount> getAccounts() {
        String tenantId = JwtUtils.getClaim("tenantId");
        return accountRepository.findByTenantId(tenantId);
    }
}
```

### Pattern 3: User Preference Loading

```java
@Component
public class UserContextLoader {
    
    @PostConstruct
    public void loadUserPreferences() {
        if (JwtUtils.isAuthenticated()) {
            String userId = JwtUtils.getCurrentUserId();
            UserPreferences prefs = preferencesService.load(userId);
            // Apply preferences...
        }
    }
}
```

## Debugging Tips

### Print JWT Details in Development

```java
@GetMapping("/debug/jwt")
public ResponseEntity<?> debugJwt(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) {
        return ResponseEntity.ok("No JWT found");
    }
    
    Map<String, Object> debug = new HashMap<>();
    debug.put("subject", jwt.getSubject());
    debug.put("claims", jwt.getClaims());
    debug.put("headers", jwt.getHeaders());
    debug.put("issuedAt", jwt.getIssuedAt());
    debug.put("expiresAt", jwt.getExpiresAt());
    debug.put("notBefore", jwt.getNotBefore());
    
    return ResponseEntity.ok(debug);
}
```

### Log Authentication Events

```java
@Component
public class AuthenticationEventListener {
    
    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        log.info("User {} authenticated successfully", event.getAuthentication().getName());
    }
    
    @EventListener
    public void onAuthenticationFailure(AuthenticationFailureEvent event) {
        log.warn("Authentication failed: {}", event.getException().getMessage());
    }
}
```

## Summary

- Use `@AuthenticationPrincipal Jwt jwt` in controllers to access JWT
- Use `JwtUtils` utility class in services for convenience
- Always validate user ownership of resources
- Use role-based access control for authorization
- Log all authentication and authorization events
- Test with both mocked and real JWTs

---

**Remember:** Never log or expose the actual JWT token in production - it's a credential!
