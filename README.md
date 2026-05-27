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
