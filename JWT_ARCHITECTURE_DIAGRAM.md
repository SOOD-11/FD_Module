# JWT Authentication Architecture

## System Architecture Diagram

```
┌─────────────────┐
│                 │
│     Client      │
│  (Web/Mobile)   │
│                 │
└────────┬────────┘
         │
         │ 1. Login Request
         ▼
┌─────────────────────────────┐
│   Authentication Service    │
│   (http://localhost:3020)   │
│                             │
│  POST /api/auth/login       │
│  - Validates credentials    │
│  - Generates JWT            │
│  - Signs with private key   │
└──────────┬──────────────────┘
           │
           │ 2. Returns JWT Token
           ▼
┌─────────────────┐
│     Client      │
│  Stores Token   │
└────────┬────────┘
         │
         │ 3. API Request
         │    Authorization: Bearer <JWT>
         ▼
┌─────────────────────────────────────────┐
│         FD Module Application           │
│       (http://localhost:8080)           │
│                                         │
│  ┌───────────────────────────────────┐ │
│  │       Security Filter Chain       │ │
│  │                                   │ │
│  │  1. Extract JWT from Header      │ │
│  │  2. Fetch Public Key (once)      │ ◄─┐
│  │  3. Validate JWT Signature       │ │ │
│  │  4. Check Expiration             │ │ │
│  │  5. Extract User Claims          │ │ │
│  │  6. Set Security Context         │ │ │
│  └───────────┬───────────────────────┘ │ │
│              │                          │ │
│              ▼                          │ │
│  ┌───────────────────────────────────┐ │ │
│  │         Controllers               │ │ │
│  │  /api/v1/accounts                │ │ │
│  │  /api/v1/reports                 │ │ │
│  │  /api/v1/jobs                    │ │ │
│  └───────────┬───────────────────────┘ │ │
│              │                          │ │
│              ▼                          │ │
│  ┌───────────────────────────────────┐ │ │
│  │         Services                  │ │ │
│  │  - JwtUtils.getCurrentUserId()   │ │ │
│  │  - Access JWT claims             │ │ │
│  │  - Business logic                │ │ │
│  └───────────────────────────────────┘ │ │
└─────────────────────────────────────────┘ │
                                            │
                                            │ 4. GET /api/auth/public-key
                                            │    (On startup)
                                            │
                                            │
┌───────────────────────────────────────────┴───┐
│         Authentication Service                │
│       (http://localhost:3020)                 │
│                                               │
│  GET /api/auth/public-key                    │
│  Returns:                                     │
│  {                                            │
│    "keys": [{                                 │
│      "kty": "RSA",                            │
│      "e": "AQAB",                             │
│      "n": "AM4VMDVb9jk9giR..."                │
│    }]                                         │
│  }                                            │
└───────────────────────────────────────────────┘
```

## Request Flow

### 1. Authentication Flow
```
Client                    Auth Service              FD Module
  │                            │                        │
  │─── POST /login ───────────>│                        │
  │    (credentials)           │                        │
  │                            │                        │
  │<── JWT Token ──────────────│                        │
  │                            │                        │
```

### 2. API Request Flow
```
Client                    FD Module                Auth Service
  │                          │                          │
  │─── GET /api/v1/... ─────>│                          │
  │    Authorization:        │                          │
  │    Bearer <JWT>          │                          │
  │                          │                          │
  │                          │── Get Public Key ───────>│
  │                          │   (First request only)   │
  │                          │                          │
  │                          │<── RSA Public Key ───────│
  │                          │                          │
  │                          │                          │
  │                          │ Validate JWT             │
  │                          │ - Verify signature       │
  │                          │ - Check expiration       │
  │                          │ - Extract claims         │
  │                          │                          │
  │<── Response ─────────────│                          │
  │    (Data or 401)         │                          │
```

## Component Interactions

```
┌─────────────────────────────────────────────────────┐
│              Spring Security Filter Chain            │
│                                                      │
│  ┌────────────────────────────────────────────┐    │
│  │  1. JwtAuthenticationFilter                │    │
│  │     - Extracts "Bearer <token>" from       │    │
│  │       Authorization header                 │    │
│  └──────────────┬─────────────────────────────┘    │
│                 │                                    │
│                 ▼                                    │
│  ┌────────────────────────────────────────────┐    │
│  │  2. JwtDecoder                             │    │
│  │     - Uses RSA Public Key                  │    │
│  │     - Validates signature                  │    │
│  │     - Parses claims                        │    │
│  └──────────────┬─────────────────────────────┘    │
│                 │                                    │
│                 ▼                                    │
│  ┌────────────────────────────────────────────┐    │
│  │  3. JwtAuthenticationConverter             │    │
│  │     - Extracts "roles" claim               │    │
│  │     - Converts to GrantedAuthorities       │    │
│  │     - Creates Authentication object        │    │
│  └──────────────┬─────────────────────────────┘    │
│                 │                                    │
│                 ▼                                    │
│  ┌────────────────────────────────────────────┐    │
│  │  4. SecurityContextHolder                  │    │
│  │     - Stores Authentication                │    │
│  │     - Available to all components          │    │
│  └────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────┘
                        │
                        ▼
            ┌───────────────────────┐
            │   Your Controllers    │
            │   - Can access JWT    │
            │   - Can get user info │
            └───────────────────────┘
```

## JWT Token Structure

```
┌─────────────────────────────────────────────────────┐
│                    JWT Token                         │
├─────────────────────────────────────────────────────┤
│                                                      │
│  Header (Algorithm & Type)                          │
│  {                                                   │
│    "alg": "RS256",                                   │
│    "typ": "JWT"                                      │
│  }                                                   │
│                                                      │
├─────────────────────────────────────────────────────┤
│                                                      │
│  Payload (Claims)                                    │
│  {                                                   │
│    "sub": "user123",           // User ID           │
│    "name": "John Doe",         // User name         │
│    "email": "john@example.com",// Email             │
│    "roles": ["CUSTOMER"],      // User roles        │
│    "exp": 1729699200,          // Expiration        │
│    "iat": 1729695600           // Issued at         │
│  }                                                   │
│                                                      │
├─────────────────────────────────────────────────────┤
│                                                      │
│  Signature (Verified with Public Key)               │
│  RSASHA256(                                          │
│    base64UrlEncode(header) + "." +                  │
│    base64UrlEncode(payload),                        │
│    private_key  // Signed by auth service           │
│  )                                                   │
│  // Verified with public_key in FD Module           │
│                                                      │
└─────────────────────────────────────────────────────┘
```

## Public Key Fetching Process

```
On Application Startup:
┌─────────────────────────────────────────────────────┐
│  FD Module Startup                                  │
├─────────────────────────────────────────────────────┤
│                                                      │
│  1. Load Configuration                              │
│     - Read auth.service.base-url                    │
│     - Read auth.service.public-key-path             │
│                                                      │
│  2. JwtPublicKeyProvider.fetchPublicKey()           │
│     │                                                │
│     ├─> HTTP GET to:                                │
│     │   http://localhost:3020/api/auth/public-key   │
│     │                                                │
│     ├─> Parse JSON response:                        │
│     │   {                                            │
│     │     "keys": [{                                 │
│     │       "n": "AM4VMDVb...",  // Modulus          │
│     │       "e": "AQAB"          // Exponent         │
│     │     }]                                         │
│     │   }                                            │
│     │                                                │
│     ├─> Decode Base64 URL values                    │
│     │                                                │
│     ├─> Create RSAPublicKeySpec                     │
│     │                                                │
│     └─> Return RSAPublicKey                         │
│                                                      │
│  3. Configure JwtDecoder with public key            │
│                                                      │
│  4. Ready to validate JWTs!                         │
│                                                      │
└─────────────────────────────────────────────────────┘
```

## Security Endpoints Configuration

```
┌─────────────────────────────────────────────────────┐
│  SecurityConfig - Endpoint Protection Rules         │
├─────────────────────────────────────────────────────┤
│                                                      │
│  PUBLIC (No Authentication Required)                │
│  ✓ /swagger-ui/**                                   │
│  ✓ /api-docs/**                                     │
│  ✓ /v3/api-docs/**                                  │
│  ✓ /actuator/**                                     │
│                                                      │
├─────────────────────────────────────────────────────┤
│                                                      │
│  PROTECTED (JWT Required)                           │
│  🔒 /api/v1/accounts/**                             │
│  🔒 /api/v1/reports/**                              │
│  🔒 /api/v1/jobs/**                                 │
│  🔒 /api/v1/auth/**                                 │
│  🔒 All other requests                              │
│                                                      │
└─────────────────────────────────────────────────────┘
```

## Data Flow in Controllers

```
HTTP Request
    │
    ▼
┌─────────────────────────────────────┐
│  SecurityFilterChain                │
│  - Validates JWT                    │
│  - Sets SecurityContext             │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│  Controller Method                  │
│                                     │
│  @GetMapping("/api/v1/example")    │
│  public ResponseEntity<?> method(  │
│    @AuthenticationPrincipal Jwt jwt│ ◄── JWT injected here
│  ) {                                │
│                                     │
│    String userId = jwt.getSubject();│
│    String email = jwt.getClaim(...);│
│                                     │
│    return service.getData(userId); │
│  }                                  │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│  Service Layer                      │
│                                     │
│  public Data getData(String userId){│
│    // Can also use:                │
│    String userId =                 │
│      JwtUtils.getCurrentUserId(); │
│                                     │
│    return repository.find(userId); │
│  }                                  │
└─────────────────────────────────────┘
```

## Error Flow

```
Request without JWT
    │
    ▼
┌─────────────────────────────────────┐
│  SecurityFilterChain                │
│  - No Authorization header          │
│  - Or invalid JWT                   │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│  Return 401 Unauthorized            │
│                                     │
│  {                                  │
│    "timestamp": "...",              │
│    "status": 401,                   │
│    "error": "Unauthorized",         │
│    "message": "Full auth required", │
│    "path": "/api/v1/..."            │
│  }                                  │
└─────────────────────────────────────┘
```

## Summary

This architecture provides:
- ✅ **Stateless authentication** - No sessions
- ✅ **Secure** - RSA signature validation
- ✅ **Scalable** - Each instance validates independently
- ✅ **Flexible** - Easy to change auth service
- ✅ **Standard** - OAuth2/JWT industry standards
