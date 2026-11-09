# Day 11 — JWT, OAuth2 Resource Server, Claims, and Role Mapping

## Recommended Git Branch

`day-11-jwt-oauth2-resource-server`

## Objective

Day 11 upgrades Day 10 from HTTP Basic + in-memory users to bearer-token security with JWT and Spring OAuth2 Resource Server.

By the end of the day, you should understand and demonstrate:

- JWT structure: header, payload, signature
- bearer-token authentication
- OAuth2 Resource Server
- issuer, audience, expiry, and signature validation
- JWKS and key rotation
- scopes vs roles
- `JwtAuthenticationToken`
- `JwtAuthenticationConverter`
- custom claim-to-authority mapping
- stateless security
- 401 vs 403 with JWT
- API Gateway forwarding the Authorization header
- MockMvc tests using mock JWTs

Do not build an identity provider today. Product Service should trust tokens issued by an external or local test issuer.

---

## 1. Day 11 Build Target

Replace Day 10's flow:

```text
Client
  |
  | Basic username/password
  v
Product Service
```

with:

```text
Client
  |
  | Authorization: Bearer <JWT>
  v
API Gateway
  |
  v
Product Service
  |
  v
OAuth2 Resource Server
  |
  +--> validate signature
  +--> validate expiry
  +--> validate issuer
  +--> map claims to authorities
  |
  v
Controller / Service
```

Target policy:

```text
GET /products/**
    product.read

POST /products
PUT /products/**
PATCH /products/**
DELETE /products/**
    product.write

/actuator/health
    public
```

---

## 2. Two-Hour Agenda

```text
00–10 min   Run Day 10 tests and verify baseline
10–20 min   Learn JWT structure and OAuth2 roles
20–35 min   Add OAuth2 Resource Server dependency
35–50 min   Configure issuer/JWKS validation
50–65 min   Replace HTTP Basic with Bearer JWT
65–80 min   Map scopes/roles to authorities
80–95 min   Secure endpoints and service methods
95–105 min  Test missing/invalid/expired tokens
105–115 min Add mock-JWT tests
115–120 min Gateway verification + interview recap
```

---

## 3. What Is a JWT?

JWT means:

```text
JSON Web Token
```

A signed JWT commonly looks like:

```text
xxxxx.yyyyy.zzzzz
```

It contains:

```text
Header.Payload.Signature
```

Conceptually:

```text
BASE64URL(header)
.
BASE64URL(payload)
.
signature
```

---

## 4. JWT Header

Example:

```json
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "key-123"
}
```

Important fields:

```text
alg -> signing algorithm
kid -> key identifier
typ -> token type
```

`kid` helps the Resource Server choose the correct public key from the issuer's JWK set.

---

## 5. JWT Payload

Example:

```json
{
  "sub": "user-123",
  "iss": "https://identity.example.com",
  "aud": "product-api",
  "exp": 1790000000,
  "iat": 1789996400,
  "scope": "product.read product.write",
  "roles": ["USER", "ADMIN"]
}
```

Common claims:

```text
sub -> subject / principal
iss -> issuer
aud -> intended audience
exp -> expiry
iat -> issued-at time
scope -> OAuth scopes
roles -> custom application roles
```

---

## 6. JWT Signature

With asymmetric signing:

```text
Authorization Server
    |
    +--> private key signs JWT
```

Resource Server:

```text
Product Service
    |
    +--> public key verifies JWT
```

The Resource Server must not need the issuer's private signing key.

---

## 7. Signed Does Not Mean Encrypted

A normal JWT/JWS payload can usually be decoded by anyone who possesses the token.

Therefore:

```text
Do not put secrets inside JWT claims.
```

Signing provides integrity/authenticity, not confidentiality.

---

## 8. OAuth2 Actors

OAuth2 terminology:

```text
Resource Owner
Client
Authorization Server
Resource Server
```

For this project:

```text
Client
    React app / API consumer

Authorization Server
    external identity system

Resource Server
    Product Service

Protected Resource
    /products APIs
```

---

## 9. Resource Server

A Resource Server accepts access tokens and:

```text
validates token
creates authenticated principal
maps authorities
protects resources
```

Product Service becomes the Resource Server on Day 11.

---

## 10. Add Dependency

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

Keep:

```text
spring-boot-starter-security
spring-security-test
```

---

## 11. Remove Day 10 In-Memory Authentication

Remove the Day 10 learning setup from Product Service:

```text
InMemoryUserDetailsManager
hard-coded USER/ADMIN accounts
HTTP Basic
```

Day 11 should use bearer-token authentication instead.

Do not keep multiple authentication models active accidentally.

---

## 12. Basic Resource Server Configuration

```java
@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http)
            throws Exception {

        http
            .csrf(csrf -> csrf.disable())

            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )

            .authorizeHttpRequests(auth -> auth

                .requestMatchers(
                    "/actuator/health"
                )
                .permitAll()

                .requestMatchers(
                    HttpMethod.GET,
                    "/products/**"
                )
                .hasAuthority(
                    "SCOPE_product.read"
                )

                .requestMatchers(
                    HttpMethod.POST,
                    "/products/**"
                )
                .hasAuthority(
                    "SCOPE_product.write"
                )

                .requestMatchers(
                    HttpMethod.PUT,
                    "/products/**"
                )
                .hasAuthority(
                    "SCOPE_product.write"
                )

                .requestMatchers(
                    HttpMethod.PATCH,
                    "/products/**"
                )
                .hasAuthority(
                    "SCOPE_product.write"
                )

                .requestMatchers(
                    HttpMethod.DELETE,
                    "/products/**"
                )
                .hasAuthority(
                    "SCOPE_product.write"
                )

                .anyRequest()
                .authenticated()
            )

            .oauth2ResourceServer(
                oauth2 ->
                    oauth2.jwt(
                        Customizer.withDefaults()
                    )
            );

        return http.build();
    }
}
```

---

## 13. Why `STATELESS`?

JWT bearer APIs usually do not need server-side login sessions.

```java
.sessionManagement(session ->
    session.sessionCreationPolicy(
        SessionCreationPolicy.STATELESS
    )
)
```

Each request carries its own access token.

Conceptually:

```text
request 1 -> bearer token -> validate
request 2 -> bearer token -> validate
```

---

## 14. Why `SCOPE_`?

Spring commonly maps OAuth scopes to authorities with a prefix.

Token scope:

```text
product.read
```

becomes:

```text
SCOPE_product.read
```

Therefore:

```java
.hasAuthority(
    "SCOPE_product.read"
)
```

matches that scope.

---

## 15. Scope vs Role

Useful distinction:

```text
Scope
    token/API capability
    e.g. product.read

Role
    business/application role
    e.g. ADMIN
```

You can use both.

Example:

```text
scope: product.write
role: ADMIN
```

---

## 16. Configure Issuer

Example:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${JWT_ISSUER_URI}
```

Environment:

```bash
export JWT_ISSUER_URI=https://issuer.example.com
```

Issuer configuration allows Spring Security to validate that tokens come from the trusted issuer and, when supported, discover signing-key metadata.

---

## 17. Configure JWKS Directly

Alternative:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${JWT_JWK_SET_URI}
```

Conceptually:

```text
Product Service
   |
   v
JWKS endpoint
   |
   v
public signing keys
```

---

## 18. What Is JWKS?

JWKS means:

```text
JSON Web Key Set
```

Example conceptual response:

```json
{
  "keys": [
    {
      "kid": "key-123",
      "kty": "RSA",
      "alg": "RS256",
      "n": "...",
      "e": "AQAB"
    }
  ]
}
```

The JWT header's `kid` identifies the relevant public key.

---

## 19. Key Rotation

Authorization Servers can rotate signing keys.

During rotation:

```text
old public key
new public key
```

may coexist in JWKS temporarily.

Do not design production systems around one hard-coded public key forever when issuer metadata/JWKS is available.

---

## 20. Token Validation Checklist

A Resource Server should validate the relevant security properties:

```text
signature
expiry
issuer
not-before where applicable
audience where required
```

A token being decodable does not make it valid.

---

## 21. Expired Token

If:

```text
exp < current time
```

expected result:

```text
401 Unauthorized
```

This is an authentication failure.

---

## 22. Invalid Signature

If the payload is modified without a valid private key:

```text
signature validation fails
```

Expected:

```text
401
```

Never trust claims from an invalid signature.

---

## 23. Wrong Issuer

Configured issuer:

```text
https://issuer-a.example.com
```

Token:

```json
{
  "iss": "https://issuer-b.example.com"
}
```

Expected:

```text
401
```

---

## 24. Audience

Example:

```json
{
  "aud": "product-api"
}
```

Audience answers:

```text
Which API/resource is this token intended for?
```

A token for `orders-api` should not automatically be trusted by `product-api`.

Even if you defer custom audience validation today, understand why it matters.

---

## 25. Bearer Token Request

```bash
curl -i   -H "Authorization: Bearer $TOKEN"   http://localhost:8083/products/<id>
```

Missing token:

```bash
curl -i   http://localhost:8083/products/<id>
```

Expected:

```text
401
```

---

## 26. Read Scope

Token contains:

```text
product.read
```

Request:

```http
GET /products/{id}
```

Expected:

```text
200
```

---

## 27. Read Token on Write Endpoint

Token:

```text
product.read
```

Request:

```http
POST /products
```

Expected:

```text
403 Forbidden
```

Authentication succeeded; authorization failed.

---

## 28. Write Scope

Token contains:

```text
product.write
```

Request:

```http
POST /products
```

Expected:

```text
201 Created
```

---

## 29. `JwtAuthenticationToken`

After successful validation, Spring creates an authenticated security object, commonly:

```text
JwtAuthenticationToken
```

Conceptually:

```text
SecurityContext
    |
    v
JwtAuthenticationToken
    |
    +--> Jwt principal
    +--> authorities
```

---

## 30. `@AuthenticationPrincipal`

Learning endpoint:

```java
@GetMapping("/token-info")
public Map<String, Object> tokenInfo(
        @AuthenticationPrincipal Jwt jwt) {

    return Map.of(
        "subject",
        jwt.getSubject(),

        "issuer",
        jwt.getIssuer()
    );
}
```

Do not expose all claims publicly in production.

---

## 31. Method Security with Scopes

Read:

```java
@PreAuthorize(
    "hasAuthority('SCOPE_product.read')"
)
@Transactional(readOnly = true)
public ProductResponse getProduct(
        UUID productId) {
    ...
}
```

Write:

```java
@PreAuthorize(
    "hasAuthority('SCOPE_product.write')"
)
@Transactional
public ProductResponse createProduct(
        CreateProductRequest request) {
    ...
}
```

---

## 32. Custom Role Claims

Some identity providers send roles like:

```json
{
  "roles": ["USER", "ADMIN"]
}
```

Spring does not automatically interpret every custom claim as authorities.

You may need:

```text
JwtAuthenticationConverter
```

---

## 33. `JwtAuthenticationConverter`

Concept:

```java
@Bean
JwtAuthenticationConverter
jwtAuthenticationConverter() {

    JwtAuthenticationConverter converter =
            new JwtAuthenticationConverter();

    converter.setJwtGrantedAuthoritiesConverter(
            jwtGrantedAuthoritiesConverter()
    );

    return converter;
}
```

---

## 34. Custom Role Converter

Example:

```java
Converter<Jwt, Collection<GrantedAuthority>>
jwtGrantedAuthoritiesConverter() {

    return jwt -> {

        List<String> roles =
                jwt.getClaimAsStringList(
                    "roles"
                );

        if (roles == null) {
            return List.of();
        }

        return roles.stream()
                .map(role ->
                    new SimpleGrantedAuthority(
                        "ROLE_" + role
                    )
                )
                .toList();
    };
}
```

Now:

```text
ADMIN -> ROLE_ADMIN
```

---

## 35. Configure Custom Converter

```java
.oauth2ResourceServer(
    oauth2 ->
        oauth2.jwt(
            jwt ->
                jwt.jwtAuthenticationConverter(
                    jwtAuthenticationConverter()
                )
        )
)
```

Then role-based method security can use:

```java
@PreAuthorize(
    "hasRole('ADMIN')"
)
```

---

## 36. Combining Scopes and Roles

A production token may contain both:

```text
scope
roles
```

Conceptually:

```text
product.read -> SCOPE_product.read
ADMIN        -> ROLE_ADMIN
```

A custom converter can merge both authority sets.

This is a good stretch goal.

---

## 37. Gateway Bearer Flow

Call through Gateway:

```bash
curl -i   -H "Authorization: Bearer $TOKEN"   http://localhost:8080/api/products/<id>
```

Expected:

```text
Gateway forwards Authorization header
Product Service validates token
request succeeds or fails based on token
```

---

## 38. Should Gateway Validate JWT Too?

Possible designs:

```text
Product only
Gateway + Product
Gateway only with strong internal trust
```

For this refresher:

```text
Product Service validates JWT itself.
```

Optional stretch:

```text
also configure Gateway as Resource Server
```

---

## 39. Why Service-Level Validation Matters

If Product Service becomes directly reachable internally, it still protects itself.

This is defense in depth.

Do not assume Gateway is the only possible network path forever.

---

## 40. Access Token vs Refresh Token

Access token:

```text
used to call APIs
short lived
sent as Bearer token
```

Refresh token:

```text
used by client to obtain new access token
sent to Authorization Server
not normally sent to Product Service
```

---

## 41. OAuth2 vs JWT

They are not the same.

```text
OAuth2
    authorization framework/protocol

JWT
    token format
```

OAuth2 access tokens can be:

```text
JWT
or
opaque
```

Do not say:

```text
OAuth2 = JWT
```

---

## 42. Opaque Token vs JWT

JWT:

```text
self-contained claims
local verification possible
```

Opaque token:

```text
random-looking value
Resource Server may introspect it with Authorization Server
```

Day 11 uses JWT.

---

## 43. Token Revocation Trade-Off

Local JWT validation improves performance and availability.

But immediate revocation can be harder because a token may remain valid until expiry.

Common mitigation:

```text
short access-token lifetime
controlled refresh tokens
selective revocation mechanisms
```

---

## 44. CSRF with Bearer Tokens

For stateless APIs where bearer tokens are explicitly supplied in the Authorization header, disabling CSRF is commonly appropriate.

Do not memorize:

```text
JWT = disable CSRF
```

Understand the credential-transport threat model.

---

## 45. Security Test Dependency

Keep:

```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

---

## 46. MockMvc — Missing Token

```java
@Test
void anonymousRequestShouldReturn401()
        throws Exception {

    mockMvc.perform(
        get(
            "/products/{id}",
            UUID.randomUUID()
        )
    )
    .andExpect(
        status().isUnauthorized()
    );
}
```

---

## 47. MockMvc — Read Scope

```java
mockMvc.perform(
    get("/products/{id}", id)
        .with(
            jwt()
                .jwt(jwt ->
                    jwt.subject("user-123")
                )
                .authorities(
                    new SimpleGrantedAuthority(
                        "SCOPE_product.read"
                    )
                )
        )
)
.andExpect(
    status().isOk()
);
```

---

## 48. MockMvc — Wrong Scope

```java
mockMvc.perform(
    post("/products")
        .with(
            jwt()
                .authorities(
                    new SimpleGrantedAuthority(
                        "SCOPE_product.read"
                    )
                )
        )
        .contentType(
            MediaType.APPLICATION_JSON
        )
        .content(
            sampleProductJson()
        )
)
.andExpect(
    status().isForbidden()
);
```

---

## 49. MockMvc — Write Scope

```java
mockMvc.perform(
    post("/products")
        .with(
            jwt()
                .authorities(
                    new SimpleGrantedAuthority(
                        "SCOPE_product.write"
                    )
                )
        )
        .contentType(
            MediaType.APPLICATION_JSON
        )
        .content(
            sampleProductJson()
        )
)
.andExpect(
    status().isCreated()
);
```

---

## 50. Mock JWT vs Real JWT Validation

The test helper:

```java
jwt()
```

is excellent for authorization tests.

It does not prove:

```text
real RSA signature verification
issuer metadata discovery
JWKS retrieval
expiry validation against an actual signed token
```

Perform at least one integration/manual test with a genuinely signed token.

---

## 51. Local Token Strategy

For this refresher, use one of these:

```text
Option A
Existing dev identity provider

Option B
Local/mock OIDC provider

Option C
Locally generated RSA key pair + signed test JWTs
```

The key learning outcome is:

```text
Product Service validates a token it did not invent inside business code.
```

Do not build a home-grown production identity provider.

---

## 52. Local RSA Concept

For local testing:

```text
private key
    token issuer only

public key
    Product Service validation
```

Never commit real production private keys to Git.

Development-only keys must be clearly labeled.

---

## 53. 401 vs 403 with JWT

```text
401
    missing token
    malformed token
    expired token
    invalid signature
    wrong issuer

403
    token valid
    identity established
    required authority missing
```

---

## 54. Security Error Handling

JWT authentication failures happen before controller logic.

Therefore:

```text
@RestControllerAdvice
```

will not necessarily handle them.

Spring Security uses:

```text
AuthenticationEntryPoint -> 401
AccessDeniedHandler      -> 403
```

Optional stretch goal: make these return the same JSON error contract used by the rest of your APIs.

---

## 55. Avoid Logging Tokens

Never log:

```text
full Authorization header
full access token
refresh token
```

Tokens are credentials and may be replayable until expiry.

---

## 56. Keep JWT Claims Minimal

Avoid putting large or sensitive user profiles in JWTs.

Reasons:

```text
claims are usually readable
JWT travels on every request
large headers increase network/proxy overhead
claims stay stale until token renewal
```

---

## 57. Common Mistake — Decode Without Validate

Bad:

```text
Base64 decode token
read claims
trust them
```

Correct:

```text
verify signature
validate issuer/expiry/etc.
then trust claims
```

---

## 58. Common Mistake — Trusting Any Issuer

A syntactically valid JWT from an untrusted issuer is still untrusted.

Validate the expected issuer.

---

## 59. Common Mistake — Ignoring Audience

If audience is part of your security contract, accepting a token meant for another API is a security flaw.

---

## 60. Common Mistake — ID Token as API Token

OIDC can return:

```text
ID token
Access token
```

Use the access token for API calls.

ID token is primarily for the client to understand authenticated identity.

---

## 61. Common Mistake — Refresh Token Sent to API

Refresh token belongs between:

```text
client
and
authorization server
```

Do not send it to Product Service.

---

## 62. Debug Logging

For local learning:

```yaml
logging:
  level:
    org.springframework.security: DEBUG
```

Observe:

```text
Bearer-token processing
authentication outcome
authorization decisions
```

Do not enable token-value logging.

---

## 63. Manual Test Matrix

```text
Scenario                         Expected
------------------------------------------------
No token                         401
Malformed token                  401
Invalid signature                401
Expired token                    401
Wrong issuer                     401
Valid read token GET             200
Read token POST                  403
Valid write token POST           201
Valid write token DELETE         204
Health without token             200
```

Run both:

```text
direct Product Service
through API Gateway
```

---

## 64. Interview Questions — JWT

1. What is a JWT?
2. What are its three parts?
3. What is `alg`?
4. What is `kid`?
5. What does the signature prove?
6. Is JWT encrypted by default?
7. What is `sub`?
8. What is `iss`?
9. What is `aud`?
10. What is `exp`?
11. What is JWKS?
12. How does key rotation work?
13. Why should you not log JWTs?

---

## 65. Interview Questions — OAuth2

1. What is OAuth2?
2. OAuth2 vs JWT?
3. What is a Resource Server?
4. What is an Authorization Server?
5. What is an access token?
6. What is a refresh token?
7. Should a Resource Server receive refresh tokens?
8. What is an opaque token?
9. JWT vs opaque token?

---

## 66. Interview Questions — Spring Security

1. What does OAuth2 Resource Server support do?
2. What does `.oauth2ResourceServer().jwt()` configure?
3. What is `JwtAuthenticationToken`?
4. What does `@AuthenticationPrincipal` provide?
5. Why are scopes commonly mapped to `SCOPE_...`?
6. What is `JwtAuthenticationConverter`?
7. How do you map a custom `roles` claim?
8. Scope vs role?
9. How does `@PreAuthorize` work with JWT authorities?
10. Why is a Spring-managed bean required for method-security interception?

---

## 67. Interview Questions — Behavior

1. Missing token: 401 or 403?
2. Expired token: 401 or 403?
3. Valid token with missing scope: 401 or 403?
4. Invalid signature: 401 or 403?
5. Why is the API stateless?
6. What does `SessionCreationPolicy.STATELESS` do?
7. Why should Gateway forward the original bearer token?
8. Why might Product Service validate JWT even behind Gateway?
9. What is the revocation trade-off with locally validated JWTs?

---

## 68. Testing Requirements

Minimum Spring tests:

```text
anonymous GET -> 401
read JWT GET -> 200
read JWT POST -> 403
write JWT POST -> 201
write JWT DELETE -> 204
health without token -> 200
method security wrong authority -> denied
method security correct authority -> success
```

Manual/integration validation:

```text
expired token -> 401
invalid signature -> 401
wrong issuer -> 401
valid signed token -> success
```

---

## 69. Coding Checklist

```text
[ ] oauth2-resource-server dependency added

[ ] Day 10 in-memory authentication removed

[ ] HTTP Basic removed

[ ] JWT Resource Server enabled

[ ] STATELESS session policy configured

[ ] issuer-uri or jwk-set-uri configured

[ ] GET requires product.read

[ ] writes require product.write

[ ] @PreAuthorize uses JWT authorities

[ ] @AuthenticationPrincipal experiment completed

[ ] JwtAuthenticationToken understood

[ ] scope mapping verified

[ ] custom role converter understood/implemented

[ ] missing token -> 401

[ ] read-token GET -> 200

[ ] read-token POST -> 403

[ ] write-token POST -> 201

[ ] invalid-signature behavior observed

[ ] expired-token behavior observed

[ ] issuer validation observed

[ ] Gateway forwards Bearer token

[ ] direct Product Service call protected

[ ] token values are not logged

[ ] docs/day-11.md updated

[ ] Git commit completed
```

---

## 70. Scorecard

```text
Resource Server configuration works        1/1
JWT validation demonstrated                1/1
Read scope works                           1/1
Write scope works                          1/1
401 vs 403 demonstrated                    1/1
Method security with JWT works             1/1
Role/scope mapping understood              1/1
Gateway Bearer flow verified               1/1
Security tests pass                        1/1
Docs + interview recap + commit            1/1

TOTAL                                     /10
```

Target:

```text
8/10 minimum
10/10 preferred
```

---

## 71. Recommended Day Notes

Create:

```text
docs/day-11.md
```

Record:

```text
Issuer:
...

JWKS URL:
...

Algorithm:
...

Read authority:
SCOPE_product.read

Write authority:
SCOPE_product.write

Role claim:
...

Custom converter:
...

Missing-token result:
...

Expired-token result:
...

Wrong-scope result:
...

Gateway-forwarding result:
...

Still unclear:
...
```

---

## 72. Topics Deliberately Deferred

Do not go deep today into:

```text
Authorization Code flow implementation
PKCE implementation
refresh-token rotation
Cognito/Keycloak administration
client-credentials flow
OAuth2 Login
OIDC Login
token introspection
mTLS
DPoP
fine-grained policy engines
```

Day 11 stays focused on:

```text
JWT
OAuth2 Resource Server
Bearer tokens
issuer/JWKS validation
claims
scopes
roles
authorities
method security
testing
```

---

# Day 11 Definition of Done

Day 11 is complete when you can explain this flow:

```text
Client
  |
  | Authorization: Bearer <JWT>
  v
API Gateway
  |
  v
Product Service
  |
  v
Bearer Token Processing
  |
  v
JWT Decoder / Validator
  |
  +--> verify signature
  +--> validate expiry
  +--> validate issuer
  +--> read claims
  |
  v
JwtAuthenticationToken
  |
  v
SecurityContext
  |
  v
Authorization
  |
  +--> SCOPE_product.read
  +--> SCOPE_product.write
  |
  v
Controller / @PreAuthorize / Service
```

You must demonstrate:

```text
No token -> 401
Invalid token -> 401
Expired token -> 401
Valid read token GET -> 200
Valid read token POST -> 403
Valid write token POST -> 201
```

You should be able to explain without notes:

```text
JWT structure
signature verification
issuer
audience
JWKS
kid
access token
refresh token
OAuth2 vs JWT
Resource Server
scope vs role
SCOPE_ prefix
JwtAuthenticationToken
JwtAuthenticationConverter
401 vs 403
stateless security
```

If Product Service can validate a signed bearer token both directly and through API Gateway, reject invalid/expired tokens, enforce read/write authorities, and your tests prove authentication and authorization behavior, Day 11 is complete.
