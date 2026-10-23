# Product Service: Trusting the Gateway's Identity Headers

This document covers how the product service authenticates requests now that the
**authorizer** validates the JWT. It explains what changed, why, and what `@PreAuthorize`
needs to keep working.

---

## 1. Request flow

```
Client ──Bearer JWT──▶ API Gateway (AuthorizationFilter, order -100)
                          │
                          │ 1. POST /internal/authorize   (Authorization, X-Original-Method, X-Original-Path)
                          ▼
                       Authorizer (:9001)
                          ├─ JwtDecoder      → signature (JWKS), exp/nbf, iss, aud     → 401 if invalid
                          ├─ AuthorizerService → method + path vs scopes/roles       → 403 if denied
                          └─ 200 { subject, roles, scopes }
                          │
                          │ 2. gateway sets X-Authenticated-User / -Roles / -Scopes
                          ▼
                       Product Service (:8082)
                          ├─ GatewayAuthenticationFilter → builds Authentication from the headers
                          ├─ authorizeHttpRequests       → anyRequest().authenticated()   → 401 if no user header
                          └─ ProductService @PreAuthorize → hasRole / hasAuthority         → 403 if denied
```

The JWT is validated **once**, in the authorizer. The product service never decodes it.

---

## 2. How `@PreAuthorize` works here

`@PreAuthorize` has no knowledge of JWTs, headers or the gateway. It only reads the
`Authentication` in the `SecurityContextHolder` and compares its authorities:

| Expression | Passes when the authorities contain |
|---|---|
| `hasRole('ADMIN')` | `ROLE_ADMIN` |
| `hasAnyRole('ADMIN','USER')` | `ROLE_ADMIN` or `ROLE_USER` |
| `hasAuthority('SCOPE_customer.read')` | `SCOPE_customer.read` |

So some filter must put an `Authentication` with those authorities into the security
context before a service method runs. `@EnableMethodSecurity` only turns the checks on. It
does not fill the context.

Before this change, the context was filled by Spring Boot's **default** OAuth2
resource-server chain. That chain decoded the JWT with the default converter, which maps
scopes only. No `ROLE_*` authorities were ever created, so every `hasRole(...)` method
returned **403**.

---

## 3. Changes

### 3.1 New `filter/GatewayAuthenticationFilter.java`

This filter turns the gateway headers into an `Authentication`:

| Header (set by gateway) | Becomes |
|---|---|
| `X-Authenticated-User: 3f2a…` | principal name |
| `X-Authenticated-Roles: ADMIN,USER` | `ROLE_ADMIN`, `ROLE_USER` |
| `X-Authenticated-Scopes: customer.read,customer.write` | `SCOPE_customer.read`, `SCOPE_customer.write` |

- **Why the `ROLE_` / `SCOPE_` prefixes:** they match what `hasRole` and `hasAuthority('SCOPE_…')`
  expect, so the existing `@PreAuthorize` expressions in `ProductService` work unchanged.
- **Why no `@Component`:** Spring Boot registers every `Filter` bean as a servlet filter.
  The filter would then run once outside the security chain and once inside it. It is
  created with `new` and added only to the security chain.
- **Why it ignores roles when the user header is missing:** without a subject there is no
  identity. The request stays unauthenticated and gets a 401.
- **Why it matches the order service:** `order` already has an identical
  `GatewayAuthenticationFilter`. Both services read the identity the same way.

### 3.2 `config/SecurityConfiguration.java`: a real `SecurityFilterChain`

```java
http.csrf(disable)
    .sessionManagement(STATELESS)
    .authorizeHttpRequests(/actuator/health → permitAll, anyRequest → authenticated)
    .addFilterBefore(new GatewayAuthenticationFilter(), AnonymousAuthenticationFilter.class)
    .exceptionHandling(entryPoint → RestAuthenticationEntryPoint)
```

- **Why before `AnonymousAuthenticationFilter`:** that filter puts an *anonymous*
  `Authentication` into an empty context. Running our filter first means the gateway
  identity is in place before the request would be treated as anonymous.
- **Why the path rules are broad:** the coarse check (may this token call this method and
  path?) already happened in the authorizer. Fine-grained business rules stay on the
  service methods as `@PreAuthorize`, so they also apply when a method is called
  internally and not through a controller.
- **Why `STATELESS` and CSRF disabled:** no session or cookie exists. Identity arrives on
  every request.
- **Why `oauth2ResourceServer` is gone:** this service no longer accepts bearer tokens. It
  trusts the gateway.

### 3.3 Removed the OAuth2 resource server

| File | Removed | Reason |
|---|---|---|
| `pom.xml` | `spring-boot-starter-oauth2-resource-server` | No JWT is decoded here. Keeping it would make Boot build a `JwtDecoder` that nothing uses. |
| `application.yml` (main and test) | `spring.security.oauth2.resourceserver.jwt.*` | Only the resource server read these properties. |
| `application-docker.yml` | `jwk-set-uri` | Same reason. |
| `infrastructure/docker/docker-compose.yml` | `JWT_ISSUER_URI`, `JWT_JWK_SET_URI` on `product` | Nothing reads them now. |

The client's `Authorization` header still reaches the product service because the gateway
forwards it unchanged. It is simply ignored.

### 3.4 `controller/WhoAmIController.java`: removed `/token-info`

`/token-info` used `@AuthenticationPrincipal Jwt` to show `iss`, `aud` and `exp`. The
principal is now the user id (`String`), and those claims are not available here. For
token details, use the authorizer. `/who` still works: it returns the principal and the
authorities built from the headers, which is useful for debugging.

### 3.5 `config/RestAuthenticationEntryPoint.java`: no `WWW-Authenticate: Bearer`

That header tells a client to retry with a bearer token. The service does not accept one
now, so a 401 here only means "the request did not arrive through the gateway with
identity headers". The JSON error body is unchanged.

### 3.6 Tests: `ProductControllerSecurityTest`

The tests no longer use `@WithMockUser`. They send the same headers the gateway sends, so
the real `GatewayAuthenticationFilter` and the real `@PreAuthorize` rules are both tested:

| Test | Headers | Expected |
|---|---|---|
| `nonAuthenticatedUserShouldReceive401` | none | 401 |
| `rolesWithoutUserHeaderShouldReceive401` | roles + scopes, no user | 401 |
| `userCanReadProduct` | `USER`, `customer.read` | 200 |
| `readWithoutReadScopeShouldReceive403` | `USER`, no scope | 403 |
| `cannotCreateProductWithUserRole` | `USER`, `customer.write` | 403 |
| `canCreateProductWithAdminRole` | `ADMIN`, `customer.write` | 201 |

`ProductServiceSecurityTest` still uses `@WithMockUser`, which is fine. It calls the
service directly, and no HTTP filter is involved.

```bash
./mvnw test -Dtest='ProductControllerSecurityTest,ProductServiceSecurityTest'
```

---

## 4. The trade-off: why this is only safe behind the gateway

The `X-Authenticated-*` headers are plain strings. Anyone who can reach port 8082 directly
can send `X-Authenticated-Roles: ADMIN` and be treated as an admin. Option B depends on
these conditions:

1. **The service is not reachable from outside.** In `docker-compose.yml`, `product` has
   no `ports:` entry, so only containers on the compose network can reach it. Keep it that
   way. When run locally with `./mvnw spring-boot:run`, port 8082 *is* open on your machine.
2. **The gateway overwrites the headers.** `AuthorizationFilter` uses `headers.set(...)`,
   which replaces any `X-Authenticated-*` header a client sends to the gateway, so they
   cannot be spoofed through it.
3. **Every route to this service goes through `AuthorizationFilter`.** It is a
   `GlobalFilter`, so this holds for all gateway routes.

Ways to harden this later:
- mTLS between the gateway and the services, or a shared secret header that the filter checks.
- Network policies, such as Kubernetes `NetworkPolicy`, that allow traffic only from the gateway.
- Going back to JWT validation in each service (Option A, "zero trust"). This costs a
  local signature check per request.

---

## 5. Audience and scopes

The frontend uses all backend APIs as one API, so every service shares a single audience
and one pair of scopes:

| What | Value | Where it is set or checked |
|---|---|---|
| Audience (`aud`) | `customer-api` | issued by `authorization-server` (`tokenCustomizer`), checked by the `authorizer` (`audiences` in `application.yml`) |
| Read scope | `customer.read` | registered on the `customer-web` client, checked by `AuthorizerService` for `GET` and by `@PreAuthorize("hasAuthority('SCOPE_customer.read')")` |
| Write scope | `customer.write` | registered on the `customer-web` client, checked by `AuthorizerService` for `POST/PUT/PATCH/DELETE` |
