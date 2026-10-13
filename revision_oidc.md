# Revision: OAuth2 / OIDC Authorization Server + Centralized Authorizer

> One read, top to bottom, should be enough to rebuild this in your head.
> Every snippet is from this repo. For the long-form build guides see
> [`README-Day11.md`](README-Day11.md) and [`readme_authorizer_setup.md`](readme_authorizer_setup.md).

---

## Table of contents

1. [TL;DR](#1-tldr)
2. [Vocabulary you must own](#2-vocabulary-you-must-own)
3. [The big picture](#3-the-big-picture)
4. [Flow 1 — Getting a token](#4-flow-1--getting-a-token-authorization-code-flow)
5. [Flow 2 — Using a token](#5-flow-2--using-a-token-request-time)
6. [Authorization Server deep dive (port 9000)](#6-authorization-server-deep-dive-port-9000)
7. [Authorizer deep dive (port 9001)](#7-authorizer-deep-dive-port-9001)
8. [API Gateway glue (port 8080)](#8-api-gateway-glue-port-8080)
9. [Product service — second line of defence (port 8082)](#9-product-service--second-line-of-defence-port-8082)
10. [Annotation cheat-sheet](#10-annotation-cheat-sheet)
11. [DSL / builder cheat-sheet](#11-dsl--builder-cheat-sheet)
12. [Token validation checklist](#12-token-validation-checklist)
13. [Gotchas in this codebase](#13-gotchas-in-this-codebase)
14. [Self-test Q&A](#14-self-test-qa)
15. [Hands-on script](#15-hands-on-script)

---

## 1. TL;DR

- The **Authorization Server** (`security/authorization-server`, :9000) is our own OIDC provider. It **logs users in**, knows the **registered clients**, and **issues signed JWTs** (access, ID, refresh tokens).
- The **Authorizer** (`security/authorizer`, :9001) is a **resource server** that only answers one question: *"Is this token valid, and may it do METHOD on PATH?"*
- The **API Gateway** (:8080) calls the Authorizer for every request. Allowed → forwards to the service with `X-Authenticated-*` headers. Not allowed → returns 401/403 and **never calls the service**.
- **Product** (:8082) validates the JWT again and applies `@PreAuthorize` rules (defence in depth).

| Question | Who answers it |
| --- | --- |
| Who are you? (authentication) | Authorization Server (form login) |
| Here is proof, signed (token issuance) | Authorization Server (`/oauth2/token`) |
| Is the proof genuine? (token validation) | Authorizer + Product (resource servers, via JWKS) |
| May you do this? (authorization decision) | Authorizer (`AuthorizerService`), Product (`@PreAuthorize`) |
| Enforce the decision | Gateway (`AuthorizationFilter`) |

---

## 2. Vocabulary you must own

### 2.1 OAuth2 roles mapped to this repo

| OAuth2 role | Meaning | In this repo |
| --- | --- | --- |
| Resource Owner | The human who owns the data | users `suprasanna` / `admin` |
| Client | App that wants access on the user's behalf | `customer-web` (React app on `localhost:3000`) |
| Authorization Server | Authenticates the user, issues tokens | `security/authorization-server` |
| Resource Server | API that accepts tokens | `security/authorizer`, `product` |
| Policy Decision Point (PDP) | Decides allow/deny | Authorizer |
| Policy Enforcement Point (PEP) | Blocks or forwards | Gateway |

### 2.2 OAuth2 vs OIDC

| | OAuth2 | OIDC (OpenID Connect) |
| --- | --- | --- |
| Purpose | **Authorization** — delegated access to APIs | **Authentication** — *who* the user is, built on top of OAuth2 |
| Trigger | any scopes | include the `openid` scope |
| Extra outputs | access token, refresh token | + **ID token**, `/userinfo` endpoint, discovery document |
| Enabled in code by | `http.oauth2AuthorizationServer(...)` | `authorizationserver.oidc(Customizer.withDefaults())` |

### 2.3 The three tokens

| Token | Audience | Used for | Send to APIs? |
| --- | --- | --- | --- |
| **Access token** (JWT here) | the API (`aud: customer-api`) | calling APIs as `Authorization: Bearer …` | **Yes** |
| **ID token** (always JWT) | the client (`aud: customer-web`) | client learns who logged in | **No** |
| **Refresh token** (opaque) | the Authorization Server | getting a new access token at `/oauth2/token` | **No** |

### 2.4 Scope vs role

| | Scope | Role |
| --- | --- | --- |
| Describes | what the **client app** is allowed to do on the user's behalf | what the **user** is in the business |
| Granted by | user consent screen | the user directory (`UserDetailsService`) |
| Claim in JWT | `scope` (`"openid profile product.read"`) | `roles` (custom, added by our token customizer) |
| Spring authority | `SCOPE_product.read` | `ROLE_ADMIN` |
| Example | `product.write` | `ADMIN` |

Rule of thumb: **effective permission = scope ∩ role**. A token with `product.write` held by a `USER` still shouldn't create products — that's why Product checks `hasRole('ADMIN')` too.

### 2.5 401 vs 403

| Code | Name | Means | Typical cause here |
| --- | --- | --- | --- |
| 401 | Unauthorized (really *unauthenticated*) | "I don't know who you are" | no/invalid/expired token, wrong `iss`/`aud` |
| 403 | Forbidden | "I know who you are, but no" | valid token without needed scope/role |

---

## 3. The big picture

```text
                    ┌──────────────────────────────────────────┐
  (1) login +       │  Authorization Server  :9000             │
      consent       │  /oauth2/authorize  /login  /oauth2/token│
  ┌────────────────►│  /oauth2/jwks  /.well-known/openid-...   │
  │                 └──────────────▲───────────────────────────┘
  │  (2) code → token              │ (5) fetch public keys (JWKS) once, cache
  │                                │
┌─┴──────────┐  (3) Bearer   ┌─────┴───────┐  (4) POST /internal/authorize  ┌──────────────┐
│ Browser /  │ ────────────► │ API Gateway │ ─────────────────────────────► │ Authorizer   │
│ customer-  │               │   :8080     │ ◄──── 200 decision / 401 / 403 │   :9001      │
│ web :3000  │ ◄──────────── │             │                                └──────────────┘
└────────────┘   response    └─────┬───────┘
                                   │ (6) forward + X-Authenticated-User/Roles/Scopes
                                   ▼
                             ┌─────────────┐
                             │ Product     │  validates JWT again + @PreAuthorize
                             │   :8082     │
                             └─────────────┘
```

Key insight: **nobody calls the Authorization Server per request.** Resource servers verify the JWT signature locally with the public key they fetched once from `/oauth2/jwks`. That is the whole point of self-contained JWTs.

---

## 4. Flow 1 — Getting a token (Authorization Code flow)

### 4.1 Step by step

```text
1. Browser → GET :9000/oauth2/authorize?response_type=code&client_id=customer-web
                    &scope=openid profile product.read&redirect_uri=http://localhost:3000/callback&state=xyz
2. Chain 1 (@Order(1)) matches /oauth2/authorize. User not logged in →
   LoginUrlAuthenticationEntryPoint redirects browser to /login (because Accept: text/html).
3. Chain 2 (@Order(2)) serves /login (formLogin). User submits suprasanna / bhaumik.
   UserDetailsService + BCryptPasswordEncoder verify. Session cookie (JSESSIONID) created.
4. Browser is redirected back to the saved /oauth2/authorize request.
5. requireAuthorizationConsent(true) → consent page: user ticks the scopes to grant.
6. Server redirects → http://localhost:3000/callback?code=AbC123&state=xyz
7. Client (back-channel) → POST :9000/oauth2/token
       Authorization: Basic base64(customer-web:customer-web-secret)   ← CLIENT_SECRET_BASIC
       grant_type=authorization_code&code=AbC123&redirect_uri=http://localhost:3000/callback
8. Response: { access_token, id_token (openid requested), refresh_token, scope, expires_in }
```

The comment at the top of `AuthorizationServerConfiguration` summarises steps 1–4:

```java
// file: security/authorization-server/.../config/AuthorizationServerConfiguration.java
/*
 * Browser requests /oauth2/authorize
 *   → Chain 1 matches the authorization endpoint
 *   → Authentication is required
 *   → Unauthenticated browser is redirected to /login
 *   → Chain 2 handles /login and authenticates the user
 *   → Browser resumes the authorization request
 *   → Chain 1 processes it with the authenticated user
 */
```

**Why a code and not the token directly?** The redirect goes through the browser (URL bar, history, logs). The code is short-lived, single-use, and useless without the client secret — the actual tokens travel only on the back channel (step 7).

### 4.2 What the access token looks like (decoded payload)

```json
{
  "iss": "http://localhost:9000",          // AuthorizationServerSettings.issuer
  "sub": "admin",                          // the username
  "aud": ["customer-api"],                 // added by OAuth2TokenCustomizer
  "scope": ["openid", "profile", "product.read", "product.write"],
  "roles": ["USER", "ADMIN"],              // added by OAuth2TokenCustomizer (ROLE_ stripped)
  "iat": 1760000000,
  "exp": 1760000300                        // default 5 min lifetime
}
```

Header: `{"alg":"RS256","kid":"<uuid from jwkSource()>"}`. The `kid` tells the resource server **which** public key in the JWKS to verify with.

Remember: **signed ≠ encrypted.** Anyone can base64-decode the payload. Never put secrets in claims.

---

## 5. Flow 2 — Using a token (request time)

```text
Client ──GET /api/v1/products/42, Authorization: Bearer eyJ...──► Gateway :8080
Gateway AuthorizationFilter (order -100):
   no "Bearer " header?                → 401, stop
   POST :9001/internal/authorize
        Authorization: Bearer eyJ...
        X-Original-Method: GET
        X-Original-Path:   /api/v1/products/42
Authorizer:
   BearerTokenAuthenticationFilter → JwtDecoder checks signature (JWKS), iss, aud, exp
        bad token                      → 401 (controller never runs)
   AuthorizerController → AuthorizerService.authorize(jwt, method, path)
        policy says no                 → AccessDeniedException → 403
        policy says yes                → 200 {allowed:true, subject, roles, scopes}
Gateway:
   401/403 from authorizer             → same status to client, Product NOT called
   200                                 → add X-Authenticated-User / -Roles / -Scopes, forward
Product:
   validates JWT again, URL rules, @PreAuthorize → business logic
```

---

## 6. Authorization Server deep dive (port 9000)

### 6.1 The dependency does most of the work

```xml
<!-- file: security/authorization-server/pom.xml -->
<artifactId>spring-boot-starter-security-oauth2-authorization-server</artifactId>
<artifactId>spring-boot-starter-webmvc</artifactId>
```

It brings Spring Security + Spring Authorization Server (protocol endpoints, token generation, Nimbus JOSE for JWT signing). Our job is only to provide **beans** describing users, clients, keys, and issuer.

### 6.2 The class-level annotations

```java
@Configuration      // this class contains @Bean factory methods
@EnableWebSecurity  // turn on Spring Security's web support / HttpSecurity builder
public class AuthorizationServerConfiguration { ... }
```

(In Spring Boot `@EnableWebSecurity` is applied automatically when the starter is present; adding it is explicit and harmless.)

### 6.3 Two filter chains and `@Order`

Spring Security holds a list of `SecurityFilterChain`s inside `FilterChainProxy`. For each request it picks **the first chain whose matcher matches**. `@Order` sets that list position (lower = earlier).

| Chain | `@Order` | Matches | Job |
| --- | --- | --- | --- |
| `oidcSecurityFilterChain` | 1 | only protocol endpoints (`/oauth2/**`, `/.well-known/**`, `/userinfo`, …) | OAuth2/OIDC protocol: validate clients, requests, issue tokens |
| `loginSecurityFilterChain` | 2 | everything else (no `securityMatcher` = any request) | Form login for humans |

If chain 2 were first, it would swallow `/oauth2/token` and treat it as "needs form login" — the protocol would break. **The specific chain must come before the catch-all chain.**

#### Chain 1 — protocol endpoints

```java
@Bean
@Order(1)
SecurityFilterChain oidcSecurityFilterChain(HttpSecurity http) throws Exception {
    http.oauth2AuthorizationServer(authorizationserver -> {
        http.securityMatcher(authorizationserver.getEndpointsMatcher()); // scope this chain to AS endpoints only
        authorizationserver.oidc(Customizer.withDefaults());             // enable OIDC: id_token, /userinfo, discovery
    });
    http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
    http.exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
        new LoginUrlAuthenticationEntryPoint("/login"),     // what to do when unauthenticated…
        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)    // …but only for browsers (Accept: text/html)
    ));
    return http.build();
}
```

| Line | Meaning |
| --- | --- |
| `oauth2AuthorizationServer(...)` | Registers all protocol filters (authorize, token, jwks, introspect, revoke, metadata). |
| `getEndpointsMatcher()` | A `RequestMatcher` covering exactly those endpoints → `securityMatcher` makes this chain ignore everything else. |
| `.oidc(withDefaults())` | Adds OIDC: ID token on `openid` scope, `/userinfo`, `/.well-known/openid-configuration`. Remove it and you get plain OAuth2. |
| `anyRequest().authenticated()` | Within this chain, nothing is anonymous (`/oauth2/authorize` needs a logged-in user; `/oauth2/token` needs an authenticated *client*). |
| `defaultAuthenticationEntryPointFor(..., TEXT_HTML)` | A browser hitting `/oauth2/authorize` unauthenticated is **redirected to `/login`**. A machine call (e.g. JSON) still gets a plain 401 instead of an HTML redirect. |

#### Chain 2 — the login page

```java
@Bean
@Order(2)
SecurityFilterChain loginSecurityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/login", "/error",
                             "/.well-known/appspecific/com.chrome.devtools.json").permitAll()
            .anyRequest().authenticated())
        .formLogin(Customizer.withDefaults());   // Spring's default /login page + POST handler
    return http.build();
}
```

- `permitAll()` on `/login` — otherwise you'd need to be logged in to see the login page (redirect loop).
- `/error` — Spring Boot's error page; without it, errors get turned into a redirect to login, hiding the real error.
- The chrome-devtools path is just noise suppression for Chrome.

### 6.4 Users: `PasswordEncoder` + `UserDetailsService`

```java
@Bean
PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

@Bean
UserDetailsService userDetailsService(PasswordEncoder encoder) {
    UserDetails admin = User.withUsername("admin")
        .password(encoder.encode("admin123"))
        .roles("USER", "ADMIN")          // stored as authorities ROLE_USER, ROLE_ADMIN
        .build();
    ...
    return new InMemoryUserDetailsManager(normalUser, admin);
}
```

- `.roles("ADMIN")` is sugar for `.authorities("ROLE_ADMIN")`. Spring adds the `ROLE_` prefix.
- Spring injects the `PasswordEncoder` bean into `userDetailsService(...)` as a method parameter — bean methods can depend on other beans this way.
- In production: replace `InMemoryUserDetailsManager` with a JDBC/LDAP-backed `UserDetailsService`.

### 6.5 Clients: `RegisteredClientRepository`

Users log in; **clients** are the apps allowed to ask for tokens.

```java
RegisteredClient client = RegisteredClient
    .withId(UUID.randomUUID().toString())                                // internal DB id
    .clientId("customer-web")                                            // public identifier
    .clientSecret(encoder.encode("customer-web-secret"))                 // hashed like a password
    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC) // Basic auth at /oauth2/token
    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)   // browser login flow
    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)        // can renew access tokens
    .redirectUri("http://localhost:3000/callback")                       // must match EXACTLY
    .scope(OidcScopes.OPENID)                                            // "openid" → ID token
    .scope(OidcScopes.PROFILE)                                           // "profile" → name claims on /userinfo
    .scope("product.read")
    .scope("product.write")
    .clientSettings(ClientSettings.builder()
        .requireAuthorizationConsent(true)                               // show consent screen
        .build())
    .build();
return new InMemoryRegisteredClientRepository(client);
```

| Setting | Why it matters |
| --- | --- |
| `redirectUri` | Prevents an attacker from getting the code sent to their own site. Exact string match. |
| `scope(...)` | Upper bound: the client can never be granted a scope not listed here. |
| grant types | Only listed flows are allowed. `client_credentials` (machine-to-machine) is **not** registered here. |
| `requireAuthorizationConsent` | User explicitly approves scopes; granted scopes ⊆ requested scopes ⊆ registered scopes. |

### 6.6 Signing keys: `JWKSource`

```java
@Bean
JWKSource<SecurityContext> jwkSource(@Value("${authorization-server.key-dir:./keys}") Path keyDir) throws Exception {
    KeyPair keyPair = loadOrCreateRsaKey(keyDir);             // 2048-bit RSA, read from disk or created on first start
    RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
        .privateKey((RSAPrivateKey) keyPair.getPrivate())
        .keyIDFromThumbprint()                                // "kid" = hash of the public key → stable across restarts
        .build();
    return new ImmutableJWKSet<>(new JWKSet(rsaKey));
}
```

`@Value("${prop:default}")` injects a config property into a bean method parameter (here the folder for the key files, default `./keys`, which is git-ignored).

- **Private key** → signs tokens (never leaves port 9000).
- **Public key** → published at `GET /oauth2/jwks`. Resource servers download it and verify.
- **Asymmetric (RS256)** means resource servers can verify but can never forge tokens.
- The key pair is **saved to `keys/signing-key.{private,public}` on first start and reused afterwards**, so tokens survive a restart. (Before this fix a new key was generated in memory on every start, and every issued token broke on restart.)
- Production: load from a keystore/KMS, never a plain file, and rotate by publishing old + new keys together in the JWKS (that's why `kid` exists).

### 6.7 Issuer: `AuthorizationServerSettings`

```java
@Bean
AuthorizationServerSettings authorizationServerSettings() {
    return AuthorizationServerSettings.builder().issuer("http://localhost:9000").build();
}
```

- Becomes the `iss` claim in every token and the `issuer` field in discovery metadata.
- Resource servers configure `issuer-uri: http://localhost:9000` and **reject any token whose `iss` differs by even one character** (`localhost` vs `127.0.0.1` vs `authorization-server` in Docker — the classic "issuer trap").
- This bean also lets you customise endpoint paths (`.tokenEndpoint("/my/token")`, etc.).

### 6.8 Custom claims: `OAuth2TokenCustomizer<JwtEncodingContext>`

```java
@Bean
OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer() {
    return context -> {
        if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {   // only access tokens, not ID tokens
            List<String> roles = context.getPrincipal().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(role -> role.startsWith("ROLE_"))
                .map(role -> role.substring(5))                              // ROLE_ADMIN → ADMIN
                .toList();
            context.getClaims().claim("roles", roles);
            context.getClaims().audience(List.of("customer-api"));          // sets "aud"
        }
    };
}
```

- Called just before each JWT is signed. `context.getPrincipal()` is the **logged-in user**, so their roles end up in the token.
- `aud` = "this token is meant for `customer-api`". The Authorizer is configured with `audiences: customer-api`, so a token minted for some other API is rejected.
- Why strip `ROLE_`? Keep the token vendor-neutral; each resource server re-adds its own prefix (see Product's converter in §9).

### 6.9 Endpoints you get for free

| Endpoint | Purpose |
| --- | --- |
| `GET /.well-known/openid-configuration` | Discovery: lists all URLs below + supported algs, scopes. Resource servers read this via `issuer-uri`. |
| `GET /oauth2/authorize` | Start of the browser flow; returns the code. |
| `POST /oauth2/token` | Exchange code / refresh token for tokens. Client authenticates here. |
| `GET /oauth2/jwks` | Public keys for signature verification. |
| `GET /userinfo` | (OIDC) user claims, called with an access token holding `openid`. |
| `POST /oauth2/introspect` | "Is this token still active?" — for opaque tokens. |
| `POST /oauth2/revoke` | Revoke a refresh/access token. |

---

## 7. Authorizer deep dive (port 9001)

### 7.1 It's a resource server

```xml
<!-- file: security/authorizer/pom.xml -->
<artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
```

```yaml
# file: security/authorizer/src/main/resources/application.yml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:9000   # → fetch /.well-known/openid-configuration → jwks_uri; also validates "iss"
          audiences: customer-api             # validates "aud" contains customer-api
server:
  port: 9001
```

From just these two properties Spring Boot builds a `JwtDecoder` that checks **signature, `iss`, `aud`, `exp`/`nbf`**. Alternative: `jwk-set-uri: http://localhost:9000/oauth2/jwks` (skips discovery, but then `iss` isn't validated automatically).

### 7.2 Security chain

```java
// file: security/authorizer/.../configuration/SecurityConfiguration.java
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(r -> r.anyRequest().authenticated())
            .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()))
            .build();
    }
}
```

| Line | Why |
| --- | --- |
| `csrf.disable()` | CSRF exploits browsers auto-sending **cookies**. Bearer tokens are added explicitly by code, so CSRF doesn't apply. |
| `STATELESS` | No `HttpSession`; every request carries its own proof (the JWT). Scales horizontally. |
| `anyRequest().authenticated()` | No valid token, no entry. |
| `oauth2ResourceServer().jwt()` | Adds `BearerTokenAuthenticationFilter`: reads `Authorization: Bearer`, decodes via `JwtDecoder`, puts a `JwtAuthenticationToken` in the `SecurityContext`. Failure → 401 with `WWW-Authenticate: Bearer error="invalid_token"`. |

### 7.3 Controller

```java
// file: security/authorizer/.../controller/AuthorizerController.java
@RestController
@RequestMapping("/internal")
public class AuthorizerController {

    @Autowired
    AuthorizerService authorizerService;

    @PostMapping("/authorize")
    public AuthorizationDecision authorize(
        @AuthenticationPrincipal Jwt jwt,                     // the already-validated token
        @RequestHeader("X-Original-Method") String method,    // what the client really wanted
        @RequestHeader("X-Original-Path") String path) {
        return authorizerService.authorize(jwt, method, path);
    }
}
```

- `@AuthenticationPrincipal Jwt jwt` — pulls `authentication.getPrincipal()` from the `SecurityContext`. For a JWT resource server, the principal **is** the `Jwt` object. By the time this method runs, the token is guaranteed valid.
- `@RequestHeader` — the gateway passes the *original* request's method and path, because the authorizer itself is being called with `POST /internal/authorize`.
- Response is a `record` serialised to JSON:

```java
public record AuthorizationDecision(boolean allowed, String subject, List<String> roles, Set<String> scopes) {}
```

### 7.4 The policy

```java
// file: security/authorizer/.../service/AuthorizerService.java
boolean pathStartCondition = path.startsWith("/api/v1/products");
Set<String> adminMethodSet = Set.of("POST", "PUT", "PATCH", "DELETE");
Set<String> scopes = extractScopes(jwt);
List<String> roles = Optional.ofNullable(jwt.getClaimAsStringList("roles")).orElse(List.of());

if ("GET".equals(method) && pathStartCondition)                 allowed = scopes.contains("product.read");
else if (adminMethodSet.contains(method) && pathStartCondition) allowed = scopes.contains("product.write") || roles.contains("ADMIN");
else                                                            allowed = false;   // default deny

if (!allowed) throw new AccessDeniedException("Not authorized");   // → 403
return new AuthorizationDecision(true, jwt.getSubject(), roles, scopes);
```

| Method | Path | Allowed when |
| --- | --- | --- |
| GET | `/api/v1/products…` | scope `product.read` |
| POST/PUT/PATCH/DELETE | `/api/v1/products…` | scope `product.write` **or** role `ADMIN` |
| anything else | anything else | never (**default deny**) |

`extractScopes` handles both claim shapes: a space-separated string `"product.read product.write"` (RFC 8693 style), or a JSON array (what Spring AS emits), and falls back to `scp` used by some IdPs (e.g. Azure AD). Missing → empty set → no permissions.

Why it reads raw claims rather than `hasAuthority(...)`: the Authorizer is a policy engine; it needs the full picture (method, path, scopes, roles) in one place, not URL rules on its own endpoints.

---

## 8. API Gateway glue (port 8080)

```java
// file: gateway/api-gateway/.../filter/AuthorizationFilter.java
@Component
public class AuthorizationFilter implements GlobalFilter, Ordered {

    public AuthorizationFilter(@Value("${authorizer.base-url:http://localhost:9001}") String authorizerBaseUrl) {
        this.webClient = WebClient.builder().baseUrl(authorizerBaseUrl).build();   // docker profile overrides the URL
    }

    @Override public int getOrder() { return -100; }   // lower = runs earlier than other filters

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();                 // short-circuit: no downstream call
        }
        return webClient.post().uri("/internal/authorize")
            .header(HttpHeaders.AUTHORIZATION, authorization)            // pass the token through
            .header("X-Original-Method", exchange.getRequest().getMethod().name())
            .header("X-Original-Path", exchange.getRequest().getURI().getPath())
            .retrieve()
            .bodyToMono(AuthorizationDecision.class)
            .flatMap(decision -> {           // only reached on 200 = allowed
                ServerHttpRequest request = exchange.getRequest().mutate()
                    .headers(h -> {
                        h.set("X-Authenticated-User", decision.subject());
                        h.set("X-Authenticated-Roles", String.join(",", decision.roles()));
                        h.set("X-Authenticated-Scopes", String.join(",", decision.scopes()));
                    }).build();
                return chain.filter(exchange.mutate().request(request).build());   // continue to route
            })
            .onErrorResume(WebClientResponseException.class, ex -> { /* 401 → 401, else 403 */ });   // denials land here
    }
}
```

| Concept | Meaning |
| --- | --- |
| `GlobalFilter` | Runs for **every** route (vs a `GatewayFilter` attached to one route). |
| `Ordered` / `getOrder()` | Position among global filters; `-100` puts auth before routing/logging. |
| `@Component` | Makes it a bean so Spring Cloud Gateway picks it up. (Note `RequestLoggingFilter` has no `@Component`, so it is **not** active.) |
| `Mono<Void>` / `WebClient` | Gateway is reactive (WebFlux) — no blocking `RestTemplate` here. |
| `mutate()` | Requests are immutable; you build a modified copy to add headers. |
| `onErrorResume` | `retrieve()` turns 4xx/5xx into `WebClientResponseException`; we map them to the client's status. Because the authorizer **throws** on deny (403), this is where every denial is handled. |
| `@Value("${authorizer.base-url:…}")` | Authorizer URL from config: `localhost:9001` locally, `host.docker.internal:9001` in the `docker` profile. |
| Routes | `application-{local,docker}.yml` route `/api/v1/products/**` → Product (:8082). |

**Trust boundary:** downstream services trust `X-Authenticated-*` headers only because they're reachable **only through the gateway**. The gateway should also strip any such headers a client sent itself (it uses `set`, which overwrites, so that's covered for `X-Authenticated-User/Roles/Scopes`).

---

## 9. Product service — second line of defence (port 8082)

### 9.1 Mapping claims to authorities

By default, Spring's resource server maps only the `scope` claim → `SCOPE_xxx` authorities. Our custom `roles` claim is ignored unless we say otherwise:

```java
// file: product/.../config/SecurityConfiguration.java
@Bean
JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter scopesConverter = new JwtGrantedAuthoritiesConverter();   // scope → SCOPE_*
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(jwt -> {
        List<String> roles = jwt.getClaimAsStringList("roles");
        Stream<GrantedAuthority> roleAuthorities = roles == null ? Stream.empty()
            : roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role));      // roles → ROLE_*
        return Stream.concat(scopesConverter.convert(jwt).stream(), roleAuthorities).toList();
    });
    return converter;
}
// wired in with:
// .oauth2ResourceServer(oauth2 -> oauth2
//     .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
//     .authenticationEntryPoint(entryPoint))   // invalid/expired token → same JSON 401 as "no token"
```

For the admin token in §4.2 the resulting authorities are:
`SCOPE_openid, SCOPE_profile, SCOPE_product.read, SCOPE_product.write, ROLE_USER, ROLE_ADMIN`.

**Without this converter, `hasRole('ADMIN')` always fails** — there would be no `ROLE_ADMIN` authority.

### 9.2 URL-level rules

```java
@Configuration
@EnableMethodSecurity            // turns on @PreAuthorize / @PostAuthorize
public class SecurityConfiguration { ...
    .requestMatchers("/actuator/health").permitAll()
    .requestMatchers(HttpMethod.GET,  "/api/v1/products/**").hasAuthority("SCOPE_product.read")
    .requestMatchers(HttpMethod.POST, "/api/v1/products/**").hasAuthority("SCOPE_product.write")
    .requestMatchers(HttpMethod.PUT,  "/api/v1/products/**").access(hasScope("product.write"))   // same thing, typed helper
    .anyRequest().authenticated()
```

`hasScope("x")` (from `OAuth2AuthorizationManagers`) is equivalent to `hasAuthority("SCOPE_x")`.

### 9.3 Method-level rules

```java
// file: product/.../service/ProductService.java
@PreAuthorize("hasRole('ADMIN')")                         // checks ROLE_ADMIN
@Transactional
public ProductDTO createProduct(ProductRequest productRequest) { ... }

@PreAuthorize("hasAuthority('SCOPE_product.read')")       // checks a scope
@Cacheable(cacheNames = "products", key = "#productId")
public ProductDTO getProduct(UUID productId) { ... }
```

- `@PreAuthorize` evaluates a SpEL expression **before** the method runs; false → `AccessDeniedException` → 403.
- So `POST /api/v1/products` needs **both** `SCOPE_product.write` (URL rule) **and** `ROLE_ADMIN` (method rule). That's scope ∩ role in action.
- Method security works through a Spring proxy: calling a `@PreAuthorize` method from *inside the same class* bypasses the check.

### 9.4 Reading the token in a controller

```java
// file: product/.../controller/WhoAmIController.java
@RequestMapping("/token-info")
public Map<String, Object> getTokenInfo(@AuthenticationPrincipal Jwt jwt) {
    return Map.of("subject", jwt.getSubject(), "audience", jwt.getAudience(),
                  "expiry", jwt.getExpiresAt(), "issuer", jwt.getIssuer());
}
```

`/who` takes `Authentication authentication` instead — the full `JwtAuthenticationToken`, including the converted authorities.

### 9.5 Testing without real tokens

```java
// file: product/src/test/.../ProductControllerSecurityTest.java
@Test
@WithMockUser(username = "admin", authorities = {"ROLE_ADMIN", "SCOPE_product.write"})
public void canCreateProductWithAdminRole() throws Exception { ... status().isCreated() }

@Test
@WithMockUser(username = "user", authorities = {"ROLE_USER", "SCOPE_product.write"})
public void cannotCreateProductWithUserRole() throws Exception { ... status().isForbidden() }
```

`@WithMockUser` puts a fake `Authentication` straight into the `SecurityContext`, skipping JWT decoding entirely — it tests **authorization rules**, not token validation. (For JWT-shaped principals use `.with(jwt().authorities(...))` from `SecurityMockMvcRequestPostProcessors`.)

### 9.6 Product's resource-server config and the 401 response

```yaml
# file: product/src/main/resources/application.yml
spring.security.oauth2.resourceServer.jwt:
  issuer-uri: ${JWT_ISSUER_URI:http://localhost:9000}   # env var wins, otherwise local default
  audiences: customer-api                               # reject tokens minted for another API
```

```yaml
# file: product/src/main/resources/application-docker.yml  (the "issuer trap" fix)
spring.security.oauth2.resourceserver.jwt:
  jwk-set-uri: ${JWT_JWK_SET_URI:http://host.docker.internal:9000/oauth2/jwks}
```

Inside Docker, `localhost` means the container itself. Tokens still say `iss: http://localhost:9000` (the browser logged in via localhost), so we keep that as the **issuer to validate** and only fetch the **keys** over the Docker host network. When both `issuer-uri` and `jwk-set-uri` are set, Spring uses `jwk-set-uri` for keys and still checks `iss` against `issuer-uri`.

`RestAuthenticationEntryPoint` writes a JSON `ApiError` and `WWW-Authenticate: Bearer realm="product"` — the header tells the client which auth scheme to retry with (it used to say `Basic`, left over from Day 10).

---

## 10. Annotation cheat-sheet

| Annotation | Where in repo | What it does |
| --- | --- | --- |
| `@SpringBootApplication` | every `*Application.java` | `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan` |
| `@Configuration` | `AuthorizationServerConfiguration`, both `SecurityConfiguration`s | Class holds `@Bean` methods; proxied so bean methods return singletons |
| `@EnableWebSecurity` | AS + Authorizer config | Enables `HttpSecurity` / `FilterChainProxy` setup |
| `@EnableMethodSecurity` | Product config | Enables `@PreAuthorize`, `@PostAuthorize`, `@PreFilter`, `@PostFilter` |
| `@Value("${x:default}")` | `jwkSource(...)`, `AuthorizationFilter` constructor | Injects a config property, with a fallback default |
| `@Bean` | all `SecurityFilterChain`, `JWKSource`, `RegisteredClientRepository`… | Method's return value becomes a Spring-managed bean; params are injected beans |
| `@Order(n)` | the two AS filter chains | Ordering among beans of the same type; lower first → which chain matches first |
| `@PreAuthorize("…")` | `ProductService` | SpEL check before method call → 403 if false |
| `@PostAuthorize("…")` | (concept) | Check after method returns, can inspect `returnObject` |
| `@AuthenticationPrincipal` | `AuthorizerController`, `WhoAmIController` | Injects `Authentication.getPrincipal()` (a `Jwt` for resource servers) |
| `@RequestHeader("X")` | `AuthorizerController`, `ProductController` | Binds a header; missing header → 400 |
| `@RestController` | controllers | `@Controller` + `@ResponseBody` (return value → JSON) |
| `@RequestMapping` / `@PostMapping` | controllers | URL mapping (class-level prefix + method-level) |
| `@Service` / `@Component` | `AuthorizerService`, `AuthorizationFilter`, `RestAuthenticationEntryPoint` | Stereotype → picked up by component scan |
| `@Autowired` | `AuthorizerController` | Field injection (prefer constructor injection for testability/immutability) |
| `@Transactional` / `@Cacheable` | `ProductService` | Not security, but share the same proxy mechanism as `@PreAuthorize` |
| `@SpringBootTest` | security tests | Loads full app context |
| `@AutoConfigureMockMvc` | security tests | Provides a `MockMvc` wired with the real security filters |
| `@WithMockUser` | security tests | Fake authenticated user with given authorities |
| `@MockitoBean` | security tests | Replace a bean (repository) with a Mockito mock |

---

## 11. DSL / builder cheat-sheet

| Call | Meaning |
| --- | --- |
| `Customizer.withDefaults()` | "Enable this feature with default settings" (a no-op lambda) |
| `http.securityMatcher(m)` | Which requests **this whole chain** handles (chain selection) |
| `auth.requestMatchers(p)` | Within a chain, which requests a **rule** applies to |
| `permitAll()` / `authenticated()` / `denyAll()` | Anyone / any authenticated user / nobody |
| `hasRole("ADMIN")` | Checks authority `ROLE_ADMIN` (prefix added for you — never write `hasRole("ROLE_ADMIN")`) |
| `hasAuthority("SCOPE_product.read")` | Checks the exact string |
| `access(hasScope("product.write"))` | Typed helper = `hasAuthority("SCOPE_product.write")` |
| `oauth2ResourceServer(o -> o.jwt(...))` | Accept and validate Bearer JWTs |
| `oauth2AuthorizationServer(...)` | Become an OAuth2 Authorization Server |
| `formLogin(...)` | HTML login page + username/password processing |
| `exceptionHandling().authenticationEntryPoint(e)` | What to send on 401 (redirect, JSON body, …) |

---

## 12. Token validation checklist

A resource server must check, in this order:

1. **Well-formed** — three base64url parts.
2. **Signature** — using the JWKS key whose `kid` matches; `alg` must be an expected one (never `none`).
3. **`iss`** — equals the configured issuer exactly.
4. **`aud`** — contains this API (`customer-api`).
5. **`exp` / `nbf`** — not expired, not used before valid (with small clock skew, default 60s).
6. **Then** authorization: scopes / roles vs. the requested operation.

Steps 1–5 → failure is **401**. Step 6 → failure is **403**.

Never "decode without validate" (e.g. just base64-decoding the payload and trusting `roles`).

---

## 13. Gotchas in this codebase (and how they were fixed)

These were found while writing this revision and have since been fixed. Each one is a classic mistake worth recognising.

| # | Was | Fix | Lesson |
| --- | --- | --- | --- |
| 1 | `AuthorizerService` matched `/api/products`, Product serves `/api/v1/products` → every call 403 | Policy now matches `/api/v1/products` | Default-deny policies fail *closed* — a typo locks everyone out (better than letting everyone in) |
| 2 | Gateway had no Product route | `product` route added to `application-local.yml` / `-docker.yml` | |
| 3 | Header `X-Authenticate-Scopes` | Renamed `X-Authenticated-Scopes` | Header contracts between services need to be exact |
| 4 | Gateway's `if (!decision.allowed())` branch never ran (authorizer throws → 403) | Branch removed; denials handled in `onErrorResume` | Decide on **one** way to signal "deny" (status code here) |
| 5 | RSA key generated in memory per start → restart invalidated all tokens | Key pair persisted under `keys/` (git-ignored), `kid` from thumbprint | Signing keys are long-lived state, not per-process |
| 6 | Product's 401 said `WWW-Authenticate: Basic` | Now `Bearer`; entry point also used for invalid tokens | |
| 7 | Product `issuer-uri: ${JWT_ISSUER_URI}` with no default, no `audiences` | Default `http://localhost:9000`, `audiences: customer-api`, env set in docker-compose | Always validate `aud` |
| 8 | Docker issuer trap: containers can't reach `localhost:9000` | Product docker profile uses `jwk-set-uri` via `host.docker.internal`; gateway's authorizer URL is now configurable | Issuer (a name) and key location (a network address) are separate concerns |
| 9 | Authorizer `package-info.java` javadoc copy-pasted | Rewritten | |

Still deliberately open: the client has no `client_credentials` grant and doesn't require PKCE, so tokens come only from the browser flow. A public SPA client should use PKCE with no secret.

---

## 14. Self-test Q&A

<details><summary>1. Why does the Authorization Server have two <code>SecurityFilterChain</code>s, and why does order matter?</summary>

Chain 1 handles only the OAuth2/OIDC protocol endpoints (scoped by `securityMatcher(getEndpointsMatcher())`); chain 2 is a catch-all serving the form login. `FilterChainProxy` uses the **first** matching chain, so the narrow one must be `@Order(1)`; if the catch-all came first it would capture `/oauth2/token` too.
</details>

<details><summary>2. What does <code>.oidc(Customizer.withDefaults())</code> add over plain OAuth2?</summary>

ID token issuance when `openid` is requested, the `/userinfo` endpoint, and the `/.well-known/openid-configuration` discovery document.
</details>

<details><summary>3. What is <code>MediaTypeRequestMatcher(TEXT_HTML)</code> doing in chain 1?</summary>

It limits the "redirect to `/login`" behaviour to browser requests (`Accept: text/html`). API clients get a 401 instead of an HTML redirect.
</details>

<details><summary>4. Why exchange a code for a token instead of returning the token in the redirect?</summary>

The front channel (browser redirects) leaks through history, logs, referrers. The code is short-lived, single-use and requires client authentication to redeem; tokens travel only on the back channel.
</details>

<details><summary>5. How does the Authorizer know the token is genuine without calling port 9000 every time?</summary>

On startup (lazily) it reads `issuer-uri/.well-known/openid-configuration`, finds `jwks_uri`, downloads the public keys, and caches them. Each JWT is verified locally with the key matching its `kid`.
</details>

<details><summary>6. What happens if the AS issuer is <code>http://localhost:9000</code> but the Authorizer has <code>issuer-uri: http://127.0.0.1:9000</code>?</summary>

The `iss` claim won't match → every token rejected with 401 (and discovery itself may fail its issuer check at startup).
</details>

<details><summary>7. Why is the <code>aud</code> claim important?</summary>

It stops a token issued for API A being replayed against API B. The Authorizer requires `customer-api`, which our token customizer sets.
</details>

<details><summary>8. Where does <code>SCOPE_</code> come from?</summary>

`JwtGrantedAuthoritiesConverter` (the default in Spring's resource server) turns each entry of the `scope`/`scp` claim into an authority prefixed with `SCOPE_`.
</details>

<details><summary>9. Why would <code>hasRole('ADMIN')</code> fail in Product without the custom converter?</summary>

The default converter only maps scopes. The `roles` claim is ignored, so there's no `ROLE_ADMIN` authority.
</details>

<details><summary>10. Why does the token customizer strip <code>ROLE_</code>?</summary>

To keep tokens framework-neutral (`"roles": ["ADMIN"]`). Each consumer adds its own prefix convention.
</details>

<details><summary>11. Scope vs role — give an example where both are needed.</summary>

Creating a product: the client must have been granted `product.write` (scope — what the app may do) **and** the user must be `ADMIN` (role — what the person may do). Product enforces both.
</details>

<details><summary>12. Why can we disable CSRF in the Authorizer and Product?</summary>

CSRF relies on browsers automatically attaching cookies. With `STATELESS` + Bearer tokens there's no ambient credential to abuse. (The Authorization Server's login page *does* use a session cookie, so CSRF stays on there.)
</details>

<details><summary>13. Why <code>SessionCreationPolicy.STATELESS</code>?</summary>

Each request is self-authenticating via the JWT; no server session means no sticky sessions and easy horizontal scaling.
</details>

<details><summary>14. Who returns 401 vs 403 along the request path?</summary>

Gateway: 401 if no `Bearer` header. Authorizer: 401 if token invalid (signature/iss/aud/exp), 403 if policy denies. Gateway relays either. Product: 401 invalid token, 403 failing URL rule or `@PreAuthorize`.
</details>

<details><summary>15. Why shouldn't the ID token be sent to APIs?</summary>

Its audience is the client (`customer-web`), not the API. It proves *who logged in to the client*, not *what the bearer may access*. The API should reject it on `aud`.
</details>

<details><summary>16. What does <code>@AuthenticationPrincipal Jwt jwt</code> give you, and when is it populated?</summary>

The validated `Jwt` from the `SecurityContext`. It's populated by `BearerTokenAuthenticationFilter` before the controller is invoked; if validation failed, the controller is never reached.
</details>

<details><summary>17. Why does the gateway pass <code>X-Original-Method</code> and <code>X-Original-Path</code>?</summary>

The Authorizer is called with its own method/path (`POST /internal/authorize`); it needs the client's original request to evaluate the policy.
</details>

<details><summary>18. Why can Product trust <code>X-Authenticated-User</code>?</summary>

Only if Product is unreachable except through the gateway (network isolation) and the gateway overwrites client-supplied copies of those headers. Otherwise anyone could forge them — which is why Product also validates the JWT itself.
</details>

<details><summary>19. What happens to issued tokens when the Authorization Server restarts here, and how is that fixed in production?</summary>

Nothing now: the key pair is read back from `keys/` and the thumbprint-based `kid` is unchanged, so old tokens still verify. (Originally a fresh key was generated per start and every token broke.) Production loads keys from a keystore/KMS and rotates by publishing old and new keys in the JWKS with distinct `kid`s.
</details>

<details><summary>20. JWT vs opaque token — trade-off?</summary>

JWT: validated locally, fast, no AS call; but can't be revoked before `exp` (keep lifetimes short). Opaque: must be introspected at `/oauth2/introspect` per request (or cached); instantly revocable.
</details>

<details><summary>21. What does <code>@WithMockUser</code> test, and what does it skip?</summary>

It tests authorization rules (URL rules + `@PreAuthorize`) by injecting a fake `Authentication`. It skips JWT parsing/validation and the claim→authority converter entirely.
</details>

<details><summary>22. Calling a <code>@PreAuthorize</code> method from another method in the same class — is it enforced?</summary>

No. Method security is proxy-based; self-invocation bypasses the proxy.
</details>

---

## 15. Hands-on script

```bash
# 1. Start services (separate terminals)
(cd security/authorization-server && ./mvnw spring-boot:run)     # :9000
(cd security/authorizer          && ./mvnw spring-boot:run)     # :9001
(cd gateway/api-gateway          && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local)  # :8080
(cd product && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local)       # :8082 (issuer defaults to :9000)

# 2. Discovery + keys
curl -s http://localhost:9000/.well-known/openid-configuration | jq
curl -s http://localhost:9000/oauth2/jwks | jq

# 3. Browser: log in as admin/admin123, approve consent, copy ?code= from the callback URL
open "http://localhost:9000/oauth2/authorize?response_type=code&client_id=customer-web&scope=openid%20profile%20product.read%20product.write&redirect_uri=http://localhost:3000/callback&state=xyz"

# 4. Exchange the code
curl -s -u customer-web:customer-web-secret \
  -d grant_type=authorization_code -d code=PASTE_CODE \
  -d redirect_uri=http://localhost:3000/callback \
  http://localhost:9000/oauth2/token | jq
export TOKEN=<access_token>

# 5. Decode the payload (no validation!) to see iss/aud/scope/roles
echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | jq

# 6. Ask the Authorizer directly
curl -i -X POST http://localhost:9001/internal/authorize \
  -H "Authorization: Bearer $TOKEN" -H "X-Original-Method: GET" -H "X-Original-Path: /api/v1/products/1"
curl -i -X POST http://localhost:9001/internal/authorize \
  -H "Authorization: Bearer garbage" -H "X-Original-Method: GET" -H "X-Original-Path: /api/v1/products/1"   # 401

# 7. Product directly
curl -s http://localhost:8082/token-info -H "Authorization: Bearer $TOKEN" | jq
curl -s http://localhost:8082/who        -H "Authorization: Bearer $TOKEN" | jq   # see SCOPE_* and ROLE_*

# 8. Through the gateway (authorizer decides, then Product)
curl -i http://localhost:8080/api/v1/products/<id> -H "Authorization: Bearer $TOKEN"
curl -i http://localhost:8080/api/v1/products/<id>                                   # 401, Product never called

# 9. Refresh
curl -s -u customer-web:customer-web-secret \
  -d grant_type=refresh_token -d refresh_token=PASTE_REFRESH \
  http://localhost:9000/oauth2/token | jq
```

Experiments to cement the concepts:

- Log in as `suprasanna` (USER) with `product.write` → POST to Product → **403** from `@PreAuthorize("hasRole('ADMIN')")`.
- Untick `product.read` on consent → GET → **403**.
- Restart :9000, reuse the old token → still **200** (persisted key). Delete `security/authorization-server/keys/` and restart → old token **401**.
- Change `audiences` in the Authorizer to `something-else` → **401**.
