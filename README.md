# Production-Grade Security Demo

A RESTful Spring Boot application demonstrating **role-based authentication and authorization** using Keycloak as the Identity Provider, Spring Security as the resource-server, and Nginx as a hardened reverse proxy.

---

## Architecture

```
Client
  │
  ├─ HTTP :80  ──► Nginx (redirect to HTTPS)
  └─ HTTPS :443 ─► Nginx ──► Spring Boot :8080 ──► Keycloak :8180
                   │                              │
                   Rate limit                     JWT Validation (JWKS)
                   TLS termination                Keycloak Admin API (signup)
                   Security headers               PostgreSQL (Keycloak storage)
```

### Roles & Access Matrix

| Endpoint                        | Anonymous | USER | MODERATOR | ADMIN |
|---------------------------------|-----------|------|-----------|-------|
| `GET  /api/public/**`           | ✅        | ✅   | ✅        | ✅    |
| `POST /api/auth/**`             | ✅        | ✅   | ✅        | ✅    |
| `GET  /api/user/profile`        | ❌        | ✅   | ✅        | ✅    |
| `GET  /api/moderator/reports`   | ❌        | ❌   | ✅        | ✅    |
| `GET  /api/admin/dashboard`     | ❌        | ❌   | ❌        | ✅    |

---

## Quick Start

### Prerequisites
- Docker & Docker Compose
- OpenSSL (for TLS certificate generation)

### 1 — Generate TLS certificates

```bash
cd nginx/ssl
chmod +x generate-certs.sh
./generate-certs.sh
```

### 2 — Configure secrets

```bash
cp .env.example .env
# Edit .env with your own passwords before running in any shared environment
```

### 3 — Start the stack

```bash
docker compose up --build
```

Startup order: PostgreSQL → Keycloak (imports realm) → Spring Boot → Nginx.
Keycloak takes ~60–90 s on first boot (realm import). Watch with:

```bash
docker compose logs -f keycloak
```

Wait for: `Listening on: http://0.0.0.0:8080`

---

## API Usage

All calls through Nginx: `https://localhost` (self-signed cert → add `-k` to curl).

### Public endpoints (no token needed)

```bash
curl -k https://localhost/api/public/hello
curl -k https://localhost/api/public/health
```

### Signup

```bash
curl -k -X POST https://localhost/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "username": "john_doe",
    "email": "john@example.com",
    "password": "Pass@1234",
    "firstName": "John",
    "lastName": "Doe",
    "role": "USER"
  }'
```

### Login (get JWT)

```bash
TOKEN=$(curl -k -s -X POST https://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"regular_user","password":"User@123!"}' \
  | jq -r '.data.access_token')
```

Pre-seeded demo credentials:

| Username         | Password     | Roles                    |
|------------------|--------------|--------------------------|
| `regular_user`   | `User@123!`  | USER                     |
| `moderator_user` | `Mod@123!`   | USER, MODERATOR          |
| `admin_user`     | `Admin@123!` | USER, MODERATOR, ADMIN   |

### Access secured resources

```bash
# USER-level (works for all three demo users)
curl -k https://localhost/api/user/profile \
  -H "Authorization: Bearer $TOKEN"

# MODERATOR-level (fails for regular_user)
curl -k https://localhost/api/moderator/reports \
  -H "Authorization: Bearer $TOKEN"

# ADMIN-level (only admin_user)
curl -k https://localhost/api/admin/dashboard \
  -H "Authorization: Bearer $TOKEN"
```

### Refresh token

```bash
curl -k -X POST https://localhost/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<your_refresh_token>"}'
```

### Logout (invalidates refresh token in Keycloak)

```bash
curl -k -X POST https://localhost/api/auth/logout \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<your_refresh_token>"}'
```

---

## Request Flows

### Signup Flow

**Request**
```
POST https://localhost/api/auth/signup
Content-Type: application/json

{
  "username": "john_doe",
  "email": "john@example.com",
  "password": "Pass@1234",
  "firstName": "John",
  "lastName": "Doe",
  "role": "USER"
}
```

**Step-by-step**

```
Client
  │  HTTPS POST /api/auth/signup
  ▼
Nginx                        — terminates SSL, forwards to security-app:8080
  │
  ▼
AuditLogFilter               — generates requestId (UUID), attaches to MDC + X-Request-ID header
  │
  ▼
RateLimitFilter              — 10 req/min per IP (Bucket4j); returns HTTP 429 if exceeded
  │
  ▼
Spring Security              — /api/auth/** is permitAll(); no token required, passes through
  │
  ▼
AuthController.signup()      — @Valid triggers bean validation on SignupRequest
  │  ✖ invalid → GlobalExceptionHandler.handleValidation() → HTTP 400 + field errors
  │  ✔ valid   ↓
  ▼
AuthService.signup()         — thin delegate, calls KeycloakAdminService.createUser()
  │
  ▼
KeycloakAdminService.createUser()
  ├─ usersResource.search(username, exact=true)   — duplicate check → HTTP 409 if exists
  ├─ builds CredentialRepresentation              — type=password, temporary=false
  ├─ builds UserRepresentation                    — username, email, name, enabled=true
  ├─ usersResource.create(user)                   — POST /admin/realms/demo-realm/users
  │   ✖ status != 201 → HTTP 500
  │   ✔ status == 201 → extractUserId() reads Location header
  └─ assignRealmRole(userId, "USER")              — attaches realm role in Keycloak
  │
  ▼
Keycloak                     — stores user in PostgreSQL, hashes password (pbkdf2-sha256)
  │
  ▼
AuthController               — returns HTTP 201 Created
                               { "success": true, "message": "User registered successfully..." }
```

**Classes & methods involved**

| Class | Method | Role |
|---|---|---|
| `AuditLogFilter` | `doFilterInternal()` | Assigns request ID, logs method/URI/status/duration |
| `RateLimitFilter` | `doFilterInternal()`, `clientIp()` | Enforces 10 req/min per IP on auth endpoints |
| `SecurityConfig` | `securityFilterChain()` | Permits `/api/auth/**` without a token |
| `SignupRequest` | Bean validation annotations | Validates username, email, password strength, role |
| `GlobalExceptionHandler` | `handleValidation()`, `handleResponseStatus()` | Returns 400 on bad input, 409 on duplicate |
| `AuthController` | `signup()` | Entry point — validates, delegates, returns 201 |
| `AuthService` | `signup()` | Delegates to `KeycloakAdminService` |
| `KeycloakAdminService` | `createUser()` | Duplicate check, builds user, calls Keycloak Admin API |
| `KeycloakAdminService` | `extractUserId()` | Parses `Location` header to get new user's ID |
| `KeycloakAdminService` | `assignRealmRole()` | Attaches `USER`/`MODERATOR` realm role to the user |

---

### Login Flow

**Request**
```
POST https://localhost/api/auth/login
Content-Type: application/json

{ "username": "regular_user", "password": "User@123!" }
```

**Step-by-step**

```
Client
  │  HTTPS POST /api/auth/login
  ▼
Nginx                        — terminates SSL, forwards to security-app:8080
  │
  ▼
AuditLogFilter               — generates requestId, starts duration timer
  │
  ▼
RateLimitFilter              — 10 req/min per IP; HTTP 429 if exceeded
  │
  ▼
Spring Security              — /api/auth/** is permitAll(); passes through
  │
  ▼
AuthController.login()       — @Valid checks @NotBlank on username + password
  │  ✖ blank fields → GlobalExceptionHandler.handleValidation() → HTTP 400
  │  ✔ valid        ↓
  ▼
AuthService.login()
  ├─ builds form body:
  │   grant_type=password, client_id=demo-app, client_secret=...,
  │   username=regular_user, password=User@123!, scope=openid profile email
  ├─ tokenUrl() → http://keycloak:8080/realms/demo-realm/protocol/openid-connect/token
  └─ restTemplate.exchange(tokenUrl, POST, body, TokenResponse.class)
      │
      ▼
    Keycloak (internal Docker network)
      ├─ validates client_id + client_secret
      ├─ looks up user in PostgreSQL
      ├─ verifies password hash
      └─ issues tokens:
          ├─ access_token  (JWT RS256, expires 15 min)
          └─ refresh_token (JWT, expires 30 min)
      │
      ├─ ✖ wrong credentials → HTTP 401
      │     HttpClientErrorException caught in AuthService
      │     → throws AuthException("Invalid username or password")
      │     → GlobalExceptionHandler.handleGeneric() → HTTP 500
      │
      └─ ✔ HTTP 200 → TokenResponse mapped via @JsonProperty
  │
  ▼
AuthController               — wraps in ApiResponse, returns HTTP 200
                               { access_token, refresh_token, expires_in, ... }
  │
  ▼
AuditLogFilter (finally)     — logs: POST /api/auth/login | status=200 | ip=... | 312ms
```

**Classes & methods involved**

| Class | Method | Role |
|---|---|---|
| `AuditLogFilter` | `doFilterInternal()` | Request ID, full request lifecycle logging |
| `RateLimitFilter` | `doFilterInternal()`, `clientIp()`, `buildBucket()` | Token-bucket rate limiting per IP |
| `SecurityConfig` | `securityFilterChain()` | `/api/auth/**` is `permitAll()` |
| `LoginRequest` | `@NotBlank` annotations | Minimal validation — just ensures fields are present |
| `AuthController` | `login()` | Entry point — validates, delegates, wraps response |
| `AuthService` | `login()`, `tokenUrl()`, `formHeaders()` | Builds and sends OIDC password grant to Keycloak |
| `TokenResponse` | `@JsonProperty` fields | Maps Keycloak's snake_case JSON to Java fields |
| `GlobalExceptionHandler` | `handleValidation()`, `handleGeneric()` | 400 on blank fields, 500 on auth failure |

**What's inside the JWT access token**

```json
{
  "iss": "http://keycloak:8080/realms/demo-realm",
  "sub": "c1147de5-f442-4e95-...",
  "preferred_username": "regular_user",
  "realm_access": { "roles": ["USER"] },
  "exp": 1779876970,
  "scope": "email profile"
}
```

Use this token as `Authorization: Bearer <access_token>` on all subsequent protected API calls.

**Signup vs Login — key differences**

| | Signup | Login |
|---|---|---|
| Keycloak interaction | Admin Client API (creates user) | Standard OIDC token endpoint |
| Class used | `KeycloakAdminService` | `AuthService` (RestTemplate) |
| Keycloak endpoint | `POST /admin/realms/.../users` | `POST /realms/.../openid-connect/token` |
| Returns | HTTP 201, no token | HTTP 200, JWT access + refresh tokens |
| Input validation | Heavy — regex on all fields | Minimal — `@NotBlank` only |

---

## Security Practices Implemented

### Authentication & Authorization
- **Keycloak as IdP** — industry-standard OAuth2 / OpenID Connect provider
- **JWT (RS256)** — stateless, signed tokens; Spring validates against Keycloak's JWKS endpoint
- **Short token lifetime** — access tokens expire in 15 minutes (configurable in realm)
- **Refresh token rotation** — `revokeRefreshToken: true` in realm config
- **Brute-force protection** — Keycloak locks accounts after 5 failures
- **Role-based access control** — endpoint authorization at both filter-chain and method level (`@PreAuthorize`)

### Transport & Network
- **TLS 1.2/1.3 only** — older protocols disabled in Nginx
- **HTTP → HTTPS redirect** — port 80 only serves 301
- **Rate limiting (two layers)**:
  - Nginx: 5 req/min on login/signup per IP (zone-based)
  - Spring Boot: Bucket4j token-bucket, same thresholds, second defence for direct access
- **Connection limiting** — 20 concurrent connections per IP in Nginx

### HTTP Security Headers
- `Strict-Transport-Security` (HSTS, 1 year, includeSubDomains)
- `X-Frame-Options: DENY`
- `X-Content-Type-Options: nosniff`
- `Content-Security-Policy: default-src 'self'; frame-ancestors 'none'`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Permissions-Policy: camera=(), microphone=(), geolocation=()`
- Nginx version hidden (`server_tokens off`)

### API Design
- **Input validation** — `@Valid` + Jakarta Validation on all request bodies
- **CORS** — explicit allowlist, no wildcard origins
- **Generic error messages** — login failure returns "Invalid username or password" regardless of which field is wrong (prevents username enumeration)
- **Request ID tracing** — every request gets a UUID (`X-Request-ID` header) propagated through logs via MDC
- **Audit logging** — method, URI, status, IP, and duration logged for every request
- **Non-root container** — Spring Boot Docker image runs as `appuser`

### What to add for production
- Replace self-signed TLS cert with Let's Encrypt (`certbot`)
- Replace in-memory Bucket4j map with Redis backend for multi-instance rate limiting
- Enable Keycloak `sslRequired: external` in realm config
- Set strong secrets in `.env` and inject via a secrets manager (Vault, AWS SM)
- Add `spring-boot-starter-actuator` metrics to Prometheus/Grafana
- Configure Keycloak email verification flow

---

## Project Structure

```
security-demo/
├── docker-compose.yml
├── .env.example
├── keycloak/
│   └── realm-export.json          # Pre-configured realm, roles, demo users
├── nginx/
│   ├── Dockerfile
│   ├── nginx.conf                 # Rate limiting, TLS, security headers
│   └── ssl/
│       └── generate-certs.sh
└── security-demo-app/
    ├── Dockerfile                 # Multi-stage, non-root runtime
    ├── pom.xml
    └── src/main/java/com/demo/security/
        ├── config/
        │   ├── SecurityConfig.java      # Filter chain, endpoint rules
        │   ├── JwtAuthConverter.java    # Extracts Keycloak realm roles from JWT
        │   └── WebConfig.java          # CORS, RestTemplate
        ├── controller/
        │   ├── AuthController.java     # signup, login, refresh, logout
        │   ├── PublicController.java   # No auth required
        │   ├── UserController.java     # Requires USER role
        │   ├── ModeratorController.java # Requires MODERATOR role
        │   └── AdminController.java    # Requires ADMIN role
        ├── dto/                        # Request/response models with validation
        ├── exception/
        │   ├── GlobalExceptionHandler.java
        │   └── AuthException.java
        ├── filter/
        │   ├── AuditLogFilter.java     # Request ID + structured logging
        │   └── RateLimitFilter.java    # Bucket4j token-bucket
        └── service/
            ├── AuthService.java        # login/logout via Keycloak token endpoint
            └── KeycloakAdminService.java # User creation via Keycloak Admin REST API
```
