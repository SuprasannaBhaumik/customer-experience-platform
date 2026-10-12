# Custom OIDC Identity Provider + Centralized Authorizer + API Gateway

**Spring Boot refresher — implementation lab**  
**Suggested branch:** `day-11-custom-oidc-authorizer-gateway`  
**Java:** 21. Use a mutually compatible Spring Boot / Spring Security / Spring Cloud Gateway BOM. Examples assume the **Spring Cloud Gateway Server WebFlux** variant; if using Gateway Server MVC, the filter APIs differ.

## 0. Outcome and architectural contract

Build and test the following flow:

```text
                          (1) user login / token endpoint
  Browser / API client --------------------------------------> identity-provider :9000
         |                                                        |
         | (2) signed JWT access token                             | exposes public JWKS
         v                                                        v
  API Gateway :8080  --(3) bearer + method + path--> authorizer-service :9001
         ^                                                        |
         +---------(4) ALLOW identity / 401 / 403------------------+
         |
         | (5) Strip untrusted headers, create trusted identity headers
         v
  Product / Profile / Favorites service --> Redis / PostgreSQL
```

### Responsibility boundaries

| Component | Owns | Does **not** own |
|---|---|---|
| Identity Provider (IdP) / Authorization Server | User authentication, client registration, token issuance, signing keys, OIDC discovery/JWKS | Business product authorization |
| Authorizer | Token validation (`iss`, signature, `exp`, `nbf`, `aud`), per-route scope/role policy | User password login or access-token minting |
| API Gateway | Request interception, call Authorizer, fail closed, route, sanitize/propagate identity | Persistence/business rules |
| Product/Profile/Favorites | Business endpoints/data | Verifying arbitrary client-provided identity headers |

The Authorizer validates **each inbound API token locally using cached JWKS keys**; it does **not** call the IdP for every request. The *Gateway* calls the Authorizer for each protected API request in this exercise. This creates an additional dependency/latency hop, which is intentional for learning.

**Security boundary:** In this header-trust variant, downstream services must be private and accessible only to a trusted Gateway (enforced by network policy/firewall and ideally mTLS). A header alone is not proof of identity if clients can bypass the Gateway. Remove the original bearer token at the Gateway only after choosing this trust model. For stronger defense-in-depth, forward the token and validate it at each service as well.

**HTTP results:** Missing/malformed/invalid token → `401`; valid token lacking permission → `403`; Authorizer unavailable or timed out → `503` (fail closed); success → forwarded controller response.

## 1. Repository structure and local ports

```text
customer-experience-platform/
├── security/
│   ├── identity-provider/
│   └── authorizer-service/
├── gateway/
│   └── api-gateway/
├── services/
│   ├── profile-service/
│   ├── favorites-service/
│   └── product-service/
└── infrastructure/
    └── docker/
        └── compose.yaml
```

| Service | Local port |
|---|---:|
| API Gateway | 8080 |
| Profile Service | 8081 |
| Favorites Service | 8082 |
| Product Service | 8083 (adapt if your Product Service uses 8082) |
| Identity Provider | 9000 |
| Authorizer | 9001 |

**Recommended implementation order:** IdP → token issuance → Authorizer direct verification → Gateway filter → Product controller → failure drills → Docker. Start all Java apps *on the host* initially, so `http://localhost:9000` is a consistent issuer. Avoid mixing host-local and container-only issuer names at first.

## 2. Maven dependencies

### `identity-provider/pom.xml`

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-oauth2-authorization-server</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-test</artifactId>
  <scope>test</scope>
</dependency>
```

### `authorizer-service/pom.xml`

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-test</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.springframework.security</groupId>
  <artifactId>spring-security-test</artifactId>
  <scope>test</scope>
</dependency>
```

### `api-gateway/pom.xml`

Use the WebFlux Gateway starter matching your Spring Cloud release. For recent Spring Cloud Gateway releases the artifact is `spring-cloud-starter-gateway-server-webflux`; older releases use `spring-cloud-starter-gateway`. Import the **compatible Spring Cloud BOM** into your Maven `dependencyManagement` (do not guess a version). Add WebFlux/WebClient if not already transitively available and `spring-boot-starter-test`. **Do not add `spring-boot-starter-web` to this reactive Gateway.**

Check dependency convergence:

```bash
./mvnw dependency:tree
./mvnw test
```

## 3. Build the custom Identity Provider

### 3.1 Base settings

`security/identity-provider/src/main/resources/application.yml`:

```yaml
server:
  port: 9000
spring:
  application:
    name: identity-provider
```

### 3.2 Authorization Server and login security chains

Create `security/identity-provider/src/main/java/.../config/SecurityConfig.java`. The OIDC protocol chain is **separate** from the interactive login chain.

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http)
            throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();

        http.securityMatcher(authorizationServer.getEndpointsMatcher())
            .with(authorizationServer, server ->
                server.oidc(Customizer.withDefaults()))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                new LoginUrlAuthenticationEntryPoint("/login"),
                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));

        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain loginSecurityFilterChain(HttpSecurity http)
            throws Exception {
        http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .formLogin(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService users(PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
            User.withUsername("learner")
                .password(encoder.encode("learner-pass"))
                .roles("USER")
                .build(),
            User.withUsername("admin")
                .password(encoder.encode("admin-pass"))
                .roles("USER", "ADMIN")
                .build());
    }
}
```

Imports: `@Configuration`, `@Bean`, `@Order`, `@EnableWebSecurity`, `SecurityFilterChain`, `HttpSecurity`, `Customizer`, `OAuth2AuthorizationServerConfigurer`, `LoginUrlAuthenticationEntryPoint`, `MediaTypeRequestMatcher`, `MediaType`, `User`, `UserDetailsService`, `UserDetails`, `InMemoryUserDetailsManager`, `PasswordEncoder`, `BCryptPasswordEncoder`.

What this does:

1. Chain 1 owns OAuth/OIDC endpoints such as `/oauth2/authorize`, `/oauth2/token`, and OIDC metadata.
2. Requests requiring user login are redirected to `/login`.
3. Chain 2 handles the actual browser login page and authenticated user session at the **IdP only**.
4. The separate API request path remains bearer-token-based and stateless.

### 3.3 Register an OAuth client

There are **two deliberate clients** for testing:

- `lab-cli`: confidential machine client for a quick **client-credentials** access token; no human login or ID token, ideal for testing Gateway → Authorizer.
- `customer-web-bff`: confidential server-side client for **authorization code**, refresh token, and `openid`. **Store its secret only in the Next.js server/BFF, never in client-side JavaScript.** If using a pure browser SPA instead, register a public client using `ClientAuthenticationMethod.NONE` with PKCE and no secret.

```java
@Configuration
class OAuthClientsConfig {

    @Bean
    RegisteredClientRepository registeredClientRepository(PasswordEncoder encoder) {
        RegisteredClient cli = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("lab-cli")
            .clientSecret(encoder.encode("lab-cli-secret"))
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .scope("product.read")
            .scope("product.write")
            .tokenSettings(TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofMinutes(5))
                .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                .build())
            .build();

        RegisteredClient web = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("customer-web-bff")
            .clientSecret(encoder.encode("customer-web-secret"))
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("http://localhost:3000/api/auth/callback")
            .scope(OidcScopes.OPENID)
            .scope(OidcScopes.PROFILE)
            .scope("product.read")
            .scope("product.write")
            .clientSettings(ClientSettings.builder()
                .requireProofKey(true)
                .requireAuthorizationConsent(true)
                .build())
            .tokenSettings(TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofMinutes(5))
                .build())
            .build();

        return new InMemoryRegisteredClientRepository(cli, web);
    }
}
```

Imports are from `org.springframework.security.oauth2.server.authorization.client.*`, `.settings.*`, `.OAuth2TokenType`, `OAuth2TokenFormat` (authorization-server core), and Spring OAuth2 core. The registered-client repository is in memory for the lab; in production use JDBC or another durable implementation, with stable client IDs/secrets and registration IDs.

**Important:** `client_credentials` proves machine authorization, not an authenticated user. A token issued with this grant may have `sub=lab-cli` and no `ROLE_USER`. The Authorizer's example policy below deliberately uses scopes, so both token types work.

### 3.4 Signing keys and issuer

Generate an RSA keypair **once per lab process** (development only). Export the public part in JWKS, never the private part.

```java
@Configuration
class TokenKeysConfig {
    @Bean
    JWKSource<SecurityContext> jwkSource() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        RSAKey key = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
            .privateKey((RSAPrivateKey) pair.getPrivate())
            .keyID(UUID.randomUUID().toString())
            .build();
        return new ImmutableJWKSet<>(new JWKSet(key));
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
            .issuer("http://localhost:9000")
            .build();
    }
}
```

Imports: Nimbus `RSAKey`, `JWKSet`, `JWKSource`, `ImmutableJWKSet`, `com.nimbusds.jose.proc.SecurityContext`, Java `KeyPair`, `KeyPairGenerator`, `RSAPublicKey`, `RSAPrivateKey`, `UUID`, Spring `AuthorizationServerSettings`.

For this lab, restarting the IdP changes the signing key and invalidates existing access tokens. **Production:** securely persist keys, rotate them deliberately, use TLS, and publish overlapping old/new public keys during rotation.

### 3.5 Attach audience and optional user roles to access token

```java
@Bean
OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer() {
    return context -> {
        if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) return;

        context.getClaims().audience(List.of("customer-api"));

        // For authorization-code user tokens, authorities may include ROLE_ values.
        // Client-credentials tokens generally will not have human roles.
        List<String> roles = context.getPrincipal().getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(authority -> authority.startsWith("ROLE_"))
            .map(authority -> authority.substring(5))
            .toList();
        context.getClaims().claim("roles", roles);
    };
}
```

The default `scope` claim in access tokens is handled by Spring Authorization Server. Preserve its normal behavior. For strict user roles, map authenticated user authorities explicitly and test them—do not infer a human role from a client-credentials token.

### 3.6 Discover IdP metadata

Start:

```bash
cd security/identity-provider
./mvnw spring-boot:run
```

Test:

```bash
curl -s http://localhost:9000/.well-known/openid-configuration | python3 -m json.tool
curl -s http://localhost:9000/oauth2/jwks | python3 -m json.tool
```

Check metadata: `issuer` is **exactly** `http://localhost:9000`; `authorization_endpoint` and `token_endpoint` are present; `jwks_uri` is discoverable; JWKS contains public keys and `kid` but no private RSA parameters.

## 4. Obtain a real JWT

### 4.1 Fastest: machine-to-machine token for testing

```bash
curl -s -u 'lab-cli:lab-cli-secret' \
  -X POST 'http://localhost:9000/oauth2/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=client_credentials' \
  --data-urlencode 'scope=product.read' \
  | python3 -m json.tool
```

Example response shape:

```json
{
  "access_token": "eyJ...",
  "token_type": "Bearer",
  "expires_in": 299,
  "scope": "product.read"
}
```

Save the access token to an environment variable without exposing it in logs or Git:

```bash
export TOKEN="$(curl -s -u 'lab-cli:lab-cli-secret' \
  -d 'grant_type=client_credentials' -d 'scope=product.read' \
  http://localhost:9000/oauth2/token \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])')"
```

To obtain a write token, use `scope=product.write`. **Do not use password grant**; it is not the modern recommended user-login flow.

### 4.2 Real user login: Authorization Code + PKCE

The client is a **server-side Next.js BFF**, with the client secret stored server-side. For the lab, build a small `/api/auth/login` route in Next.js that:

1. Creates cryptographically random `state` and a PKCE `code_verifier`.
2. Builds `code_challenge = BASE64URL(SHA256(code_verifier))`.
3. Saves `state`/verifier in a secure, short-lived server-side session/HTTP-only cookie.
4. Redirects the browser to the discovered `authorization_endpoint` with `response_type=code`, `client_id=customer-web-bff`, registered `redirect_uri`, `scope=openid profile product.read`, `state`, `code_challenge`, `code_challenge_method=S256`.
5. The user logs into the IdP's `/login` page and approves consent if required.
6. The IdP redirects to `http://localhost:3000/api/auth/callback?code=...&state=...`.
7. BFF validates `state`, then performs a **server-side** POST to `/oauth2/token` using `grant_type=authorization_code`, `code`, `redirect_uri`, `code_verifier`, and HTTP Basic client authentication.
8. The token response supplies an `access_token`, often a `refresh_token`, and because `openid` was requested, an `id_token`.
9. Call backend APIs with the **access token**, never the ID token. Store sensitive tokens server-side if using a BFF.

**Token exchange sketch (server-side only):**

```bash
curl -u 'customer-web-bff:customer-web-secret' \
  -X POST 'http://localhost:9000/oauth2/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=authorization_code' \
  --data-urlencode 'code=<CODE_FROM_CALLBACK>' \
  --data-urlencode 'redirect_uri=http://localhost:3000/api/auth/callback' \
  --data-urlencode 'code_verifier=<ORIGINAL_CODE_VERIFIER>'
```

The code expires quickly and may be used only once. A bare pasted `/oauth2/authorize` URL will **not** complete the entire flow without correct `state`, PKCE verifier, redirect handler, and token exchange.

## 5. Build the Authorizer service

### 5.1 `application.yml`

```yaml
server:
  port: 9001
spring:
  application:
    name: authorizer-service
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:9000
          audiences: customer-api
```

The validator must use the **same issuer string** as the signed token's `iss`. The `audiences` property adds the intended API check. By default Spring Security also checks signature and expiry/`nbf` and maps OAuth scopes to `SCOPE_...` authorities.

### 5.2 Security chain: JWT validated before controller

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain authorizerChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(
                SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/internal/authorize").authenticated()
                .anyRequest().denyAll())
            .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
```

`@RestControllerAdvice` is **not** the primary handler for authentication failures in filters. Invalid JWTs are rejected by Spring Security before the Authorizer controller. Keep Spring's default 401/403 security behavior or customize `AuthenticationEntryPoint` and `AccessDeniedHandler` for a JSON contract.

### 5.3 Define request and decision DTO

```java
public record AuthorizationDecision(
    boolean authorized,
    String subject,
    List<String> scopes,
    List<String> roles
) {}
```

The Gateway provides the original HTTP method and **canonical external route path**. In this lab we forward them in internal headers. Treat them as trusted only because the Authorizer endpoint is accessible **solely from Gateway infrastructure**. In production, authenticate Gateway-to-Authorizer calls (mTLS/service credential) and prevent arbitrary clients from asserting a method/path.

### 5.4 Scope extraction and route policy

```java
@Service
public class AuthorizationService {
    public AuthorizationDecision authorize(Jwt jwt, String method, String path) {
        String scopeText = jwt.getClaimAsString("scope");
        Set<String> scopes = new HashSet<>();
        if (scopeText != null && !scopeText.isBlank()) {
            scopes.addAll(Arrays.asList(scopeText.trim().split("\\s+")));
        }
        List<String> scp = jwt.getClaimAsStringList("scp");
        if (scp != null) scopes.addAll(scp);

        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null) roles = List.of();

        String requiredScope;
        if (path.matches("^/api/products(?:/.*)?$")
                && method.equals("GET")) {
            requiredScope = "product.read";
        } else if (path.matches("^/api/products(?:/.*)?$")
                && Set.of("POST", "PUT", "PATCH", "DELETE").contains(method)) {
            requiredScope = "product.write";
        } else {
            // Deny unrecognized routes; add Profile/Favorites rules explicitly.
            throw new AccessDeniedException("No authorization policy for route");
        }

        if (!scopes.contains(requiredScope)) {
            throw new AccessDeniedException("Insufficient scope");
        }
        return new AuthorizationDecision(true, jwt.getSubject(),
            scopes.stream().sorted().toList(), roles);
    }
}
```

Why explicit allowlists? Avoid `startsWith("/api/products")` alone; it can inadvertently match `/api/products-admin`. A product write token should not automatically grant access to other services. Define separate `profile.read`, `favorites.write`, etc., before routing their APIs through this Authorizer.

### 5.5 Internal controller

```java
@RestController
@RequestMapping("/internal")
public class AuthorizationController {
    private final AuthorizationService authorizationService;

    public AuthorizationController(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @PostMapping("/authorize")
    public AuthorizationDecision authorize(
        @AuthenticationPrincipal Jwt jwt,
        @RequestHeader("X-Original-Method") String method,
        @RequestHeader("X-Original-Path") String path) {
        return authorizationService.authorize(jwt, method, path);
    }
}
```

The controller only executes if Spring Security accepts the JWT. No manual Base64 parsing or hand-written RSA signature code.

### 5.6 Direct tests before Gateway

```bash
cd security/authorizer-service
./mvnw spring-boot:run

# 200: GET allowed with read-scope token
curl -i -X POST http://localhost:9001/internal/authorize \
  -H "Authorization: Bearer $TOKEN" \
  -H 'X-Original-Method: GET' \
  -H 'X-Original-Path: /api/products/123'

# 403: write not allowed with read token
curl -i -X POST http://localhost:9001/internal/authorize \
  -H "Authorization: Bearer $TOKEN" \
  -H 'X-Original-Method: DELETE' \
  -H 'X-Original-Path: /api/products/123'

# 401: invalid bearer
curl -i -X POST http://localhost:9001/internal/authorize \
  -H 'Authorization: Bearer invalid' \
  -H 'X-Original-Method: GET' \
  -H 'X-Original-Path: /api/products/123'
```

## 6. Add centralized authorization to API Gateway

### 6.1 Local Gateway route

In current Spring Cloud Gateway Server WebFlux versions the route property namespace is typically `spring.cloud.gateway.server.webflux.routes`; older versions use `spring.cloud.gateway.routes`. Confirm your selected release's configuration metadata.

```yaml
server:
  port: 8080
app:
  authorizer:
    base-url: http://localhost:9001
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: product-service
              uri: http://localhost:8083
              predicates:
                - Path=/api/products,/api/products/**
              filters:
                - StripPrefix=1
```

Incoming `/api/products/<id>` becomes downstream `/products/<id>`. The authorizer receives the **original** `/api/products/<id>` before `StripPrefix`, since the authorization filter executes before forwarding. All paths you intend to protect must match a configured Gateway route; requests with no route should result in 404, never accidental forwarding.

### 6.2 Add the `WebClient` bean

```java
@Configuration
public class AuthorizerClientConfig {
    @Bean
    WebClient authorizerWebClient(
        WebClient.Builder builder,
        @Value("${app.authorizer.base-url}") String baseUrl) {
        return builder.baseUrl(baseUrl)
            .clientConnector(new ReactorClientHttpConnector(
                HttpClient.create()
                    .responseTimeout(Duration.ofSeconds(2))))
            .build();
    }
}
```

The Authorizer remote call must be nonblocking, bounded by a timeout, and fail closed. Avoid `RestTemplate`, `block()`, and blocking JDBC on the Gateway event loop.

### 6.3 Full Gateway `GlobalFilter`

This is a **minimal teaching implementation** with explicit response-status behavior. In a hardened system, separately authenticate Gateway-to-Authorizer, set URI/connection limits, sanitize error messages, and use route metadata for policy identification rather than arbitrary path strings.

```java
@Component
public final class CentralAuthorizationFilter implements GlobalFilter, Ordered {
    private static final String USER = "X-Authenticated-User";
    private static final String ROLES = "X-Authenticated-Roles";
    private static final String SCOPES = "X-Authenticated-Scopes";

    private final WebClient client;

    public CentralAuthorizationFilter(WebClient authorizerWebClient) {
        this.client = authorizerWebClient;
    }

    @Override
    public int getOrder() { return -100; }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getRawPath();

        // This filter is applied to all MATCHED Gateway routes.
        // For this lab, all configured API routes are protected.
        String bearer = exchange.getRequest().getHeaders()
            .getFirst(HttpHeaders.AUTHORIZATION);

        if (bearer == null || !bearer.startsWith("Bearer ")
                || bearer.substring(7).isBlank()) {
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }

        // Never trust identity headers supplied by the external client.
        ServerHttpRequest clean = exchange.getRequest().mutate()
            .headers(h -> {
                h.remove(USER);
                h.remove(ROLES);
                h.remove(SCOPES);
            }).build();
        ServerWebExchange cleanExchange = exchange.mutate().request(clean).build();

        Mono<AuthorizationDecision> authorization = client.post()
            .uri("/internal/authorize")
            .header(HttpHeaders.AUTHORIZATION, bearer)
            .header("X-Original-Method", clean.getMethod().name())
            .header("X-Original-Path", path)
            .exchangeToMono(response -> {
                if (response.statusCode().value() == 401) {
                    return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
                }
                if (response.statusCode().value() == 403) {
                    return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN));
                }
                if (!response.statusCode().is2xxSuccessful()) {
                    return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));
                }
                return response.bodyToMono(AuthorizationDecision.class)
                    .switchIfEmpty(Mono.error(new IllegalStateException("Empty decision")));
            })
            .timeout(Duration.ofSeconds(3));

        // Only the Authorizer network call is mapped to a Gateway response.
        // Errors from downstream chain.filter are NOT mislabeled Authorizer failures.
        return authorization
            .map(decision -> {
                if (!decision.authorized() || decision.subject() == null
                        || decision.subject().isBlank()) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN);
                }
                return decision;
            })
            .onErrorResume(ex -> {
                HttpStatus status = (ex instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 401)
                    ? HttpStatus.UNAUTHORIZED
                    : (ex instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 403)
                      ? HttpStatus.FORBIDDEN : HttpStatus.SERVICE_UNAVAILABLE;
                return reject(cleanExchange, status).then(Mono.empty());
            })
            .flatMap(decision -> {
                ServerHttpRequest trusted = clean.mutate().headers(h -> {
                    h.set(USER, decision.subject());
                    h.set(ROLES, String.join(",", decision.roles()));
                    h.set(SCOPES, String.join(",", decision.scopes()));
                    h.remove(HttpHeaders.AUTHORIZATION); // header-trust model only
                }).build();
                return chain.filter(cleanExchange.mutate().request(trusted).build());
            });
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }
}

record AuthorizationDecision(boolean authorized, String subject,
                             List<String> scopes, List<String> roles) {}
```

**Production refinement:** The Authorizer call is kept separate from `chain.filter(...)`, so downstream errors are handled normally rather than being mislabeled Authorizer failures. Also authenticate Gateway-to-Authorizer calls (mTLS or service identity), use signed internal identity or mTLS for downstream trust, and restrict the Authorizer endpoint to Gateway infrastructure.

### 6.4 Run locally and verify

```bash
cd gateway/api-gateway
./mvnw spring-boot:run

# Read token: GET should reach Product
curl -i -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/products/<existing-product-uuid>

# No token: Gateway returns 401, Product is NOT called
curl -i http://localhost:8080/api/products/<existing-product-uuid>

# Read token on DELETE: Gateway returns 403, Product is NOT called
curl -i -X DELETE -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/products/<existing-product-uuid>
```

Create a write token (`scope=product.write`) and test an admin-like write endpoint. No roles are needed for the scope-only policy used here.

## 7. Downstream Product Service and trusted headers

To prove the architecture, keep Product Service simple. **Remove Day 10 Basic and Day 11 Resource Server validation from this service only for the header-trust exercise.** Product authorization has already occurred at the Gateway/Authorizer. Do **not** do this unless the service is isolated from untrusted direct connections.

```java
@RestController
@RequestMapping("/products")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/{productId}")
    public ProductResponse getProduct(
        @PathVariable UUID productId,
        @RequestHeader("X-Authenticated-User") String user) {
        // For the lab, user arrives from Gateway after authorization.
        // Business authorization (e.g., row ownership) would still live in service.
        return productService.getProduct(productId);
    }
}
```

**Safer production variant:** Keep the JWT and validate it again at Product Service, or pass a short-lived signed internal token issued by trusted infrastructure. If you trust plain `X-Authenticated-User` and expose `localhost:8083` to hostile callers, an attacker can forge that header.

## 8. Docker Compose integration and the issuer trap

The earlier Spring Boot labs moved PostgreSQL into Docker. Add these application services to the existing Compose network:

```yaml
services:
  identity-provider:
    build: ../../security/identity-provider
    environment:
      SPRING_PROFILES_ACTIVE: docker
      # stable issuer configured in your IdP's docker profile
    ports:
      - "9000:9000"

  authorizer-service:
    build: ../../security/authorizer-service
    environment:
      SPRING_PROFILES_ACTIVE: docker
      # issuer and JWKS must be consistent and reachable

  api-gateway:
    build: ../../gateway/api-gateway
    environment:
      SPRING_PROFILES_ACTIVE: docker
      APP_AUTHORIZER_BASE_URL: http://authorizer-service:9001
    ports:
      - "8080:8080"
    depends_on:
      - authorizer-service

  product-service:
    build: ../../services/product-service
    environment:
      SPRING_PROFILES_ACTIVE: docker
    # no host port in this trust-header variant
```

**Do not blindly set** the issuer to `http://identity-provider:9000` when browser-issued tokens use `http://localhost:9000`. The `iss` claim is an exact-match security identifier. Use a *single stable hostname* resolvable both from the host browser and the Authorizer container, e.g. `http://idp.local.test:9000`, and configure DNS/hosts or a reverse proxy appropriately; or run the IdP on the host for the initial exercises and have the container reach the same issuer hostname through a deliberately configured routing mechanism.

For a controlled Docker-only test, a simpler alternative is configure both IdP and Authorizer with issuer `http://identity-provider:9000` and request **client-credentials tokens from another Compose container**, so that the issuer remains consistent. This does not, however, give the host browser a resolvable issuer without extra local DNS configuration.

**Recommended before Docker deployment:**

```text
[ ] Same issuer string appears in access token and Authorizer config
[ ] Issuer discovery endpoint reachable from Authorizer container
[ ] JWKS URI reachable from Authorizer container
[ ] Public hostname resolvable from client/browser for login
[ ] Signing keys stable across intended restarts
[ ] Gateway can reach Authorizer over private network
[ ] Backend service ports not externally exposed
[ ] Gateway-to-Authorizer identity is authenticated (production requirement)
```

## 9. Failure and attack simulations

| Scenario | Expected response | Must Product controller execute? |
|---|---|---|
| No Authorization header | 401 | No |
| Malformed token | 401 | No |
| Expired `exp` | 401 | No |
| Wrong `iss` | 401 | No |
| Wrong `aud` | 401 | No |
| Tampered JWT payload/signature | 401 | No |
| Valid read scope, GET `/api/products/{id}` | Product response (200/404) | Yes |
| Valid read scope, DELETE | 403 | No |
| Valid write scope, DELETE | Product response (204/404) | Yes |
| Valid token, unknown protected policy | 403 | No |
| Authorizer down/timeouts | 503, fail closed | No |
| Client supplies `X-Authenticated-User: admin` | Gateway strips it, uses Authorizer identity | Only if token authorized |
| Direct backend request with forged header | **Must be blocked by networking/service authentication** | No |

Check the Product Service logs to prove denied requests never invoke its controller. Do not infer controller absence from an HTTP status alone.

Useful negative tests:

```java
// Spring MVC Authorizer test (mock JWT authentication, tests policy not real signature):
mockMvc.perform(post("/internal/authorize")
    .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_product.read")))
    .header("X-Original-Method", "DELETE")
    .header("X-Original-Path", "/api/products/123"))
    .andExpect(status().isForbidden());
```

**Real cryptographic integration tests** should call an actual issuer/JWKS server and exercise valid, expired, wrong issuer/audience, and tampered signatures; `jwt()` MockMvc fixtures bypass actual signature validation.

## 10. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `/.well-known/openid-configuration` 404 | OIDC configurer not enabled or wrong IdP config | Check authorization-server chain and `.oidc(...)` |
| Redirect repeatedly to `/login` | Authentication cookie/session, matcher, or login chain misconfigured | Inspect both chains and browser cookies |
| `/oauth2/token` returns `invalid_client` | Client ID/secret/auth method mismatch | Check registered client and HTTP Basic credentials |
| `unauthorized_client` / `unsupported_grant_type` | Grant not registered | Check `authorizationGrantType(...)` |
| `invalid_scope` | Requested scope not registered for that client | Register scopes explicitly |
| JWT issued but Authorizer 401 | Issuer/audience mismatch, JWKS unreachable, signing key changed, expiry | Compare token `iss`, config, issuer discovery, and `kid` |
| Gateway 401 | Missing/invalid bearer | Send `Authorization: Bearer <access_token>` |
| Gateway 403 | Valid JWT but missing scope | Request `product.read` or `product.write` as required |
| Gateway 503 | Authorizer connection refused/timeout | Start Authorizer, check DNS and logs |
| Product 404 through Gateway, direct call works | Route/`StripPrefix` mapping incorrect | Verify transformed downstream URL |
| JWT works locally but not in Docker | `localhost` vs Compose DNS and issuer mismatch | Use one stable issuer hostname |
| Controller can be reached with fake identity header | Product service publicly reachable | Fix network isolation and service authentication immediately |

Debug endpoints:

```bash
curl -i http://localhost:9000/.well-known/openid-configuration
curl -i http://localhost:9000/oauth2/jwks
curl -i http://localhost:8080/api/products/<id>
```

Turn on Spring Security DEBUG logging only during local debugging, and **never log bearer tokens, private keys, refresh tokens, or passwords**.

## 11. Interview questions

1. How is an Authorization Server different from an Authorizer?
2. What is an OAuth Client? What are authorization-code, PKCE, and client-credentials grants?
3. What does `openid` change in the authorization flow?
4. How does signing with a private key and verifying against JWKS work?
5. What are `iss`, `aud`, `exp`, `nbf`, `scope`, and `kid`?
6. Why must the issuer match exactly across services?
7. Why is decoding JWT Base64 **not** token validation?
8. Why can Authorizer validate JWTs without calling the IdP on every request?
9. Why does missing/invalid token produce 401 but insufficient scope produces 403?
10. Why does Gateway use nonblocking `WebClient`, not `.block()`?
11. What does `GlobalFilter` do and how does filter ordering affect routing?
12. What does `StripPrefix=1` change?
13. Why is forwarding plain trusted headers a security-sensitive design?
14. How do you prevent header spoofing and Gateway bypass?
15. What happens when the Authorizer goes down? Should Gateway fail open?
16. What does the extra network hop cost? When would local JWT validation be preferable?
17. What does centralized policy buy you for dynamic entitlements or tenant ownership?
18. What happens to previously issued JWTs after changing an ephemeral signing key?
19. Why is a `client_credentials` token different from a user authorization-code token?
20. What is the distinction between an ID token, access token, and refresh token?

## 12. Definition of done and scorecard

```text
[ ] IdP starts and shows OIDC discovery metadata
[ ] JWKS exposes public RSA key (not private key)
[ ] Client credentials grant produces signed JWT
[ ] Authorization Code + PKCE login exchange documented/tested with BFF
[ ] JWT `iss`, `aud`, `scope`, expiry verified
[ ] Authorizer validates via Spring Resource Server
[ ] Authorizer returns 200 for permitted GET
[ ] Authorizer returns 403 for forbidden DELETE
[ ] Authorizer returns 401 for bad/expired tokens
[ ] Gateway calls Authorizer on protected routes
[ ] Gateway strips client-supplied identity headers
[ ] Gateway forwards trusted identity only on allow
[ ] Gateway fails closed on Authorizer outage
[ ] Product receives the authorized request
[ ] Product cannot be bypassed from an untrusted network
[ ] Automated tests cover positive + negative flows
[ ] Docker issuer hostname strategy validated
[ ] README reflects actual ports and routes
```

| Measured result | Points |
|---|---:|
| OIDC discovery + JWKS work | 1 |
| Machine access token issued | 1 |
| Real authorization-code/PKCE flow works | 1 |
| Authorizer 401/403/200 behavior correct | 2 |
| Gateway prevents unauthorized downstream calls | 2 |
| Header spoofing and bypass protections validated | 1 |
| Docker issuer / connectivity correct | 1 |
| Automated tests and interview explanation | 1 |
| **Total** | **10** |

**Pass target:** 8/10 minimum; 10/10 preferred. This lab is intentionally deeper than a single 2-hour session. Complete a runnable machine-token vertical slice first; then implement browser login, Docker and security hardening.

## Official references

- [Spring Authorization Server — Getting Started](https://docs.spring.io/spring-security/reference/servlet/oauth2/authorization-server/getting-started.html)
- [Spring Security — OAuth2 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Spring Cloud Gateway — Global Filters](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/global-filters.html)
- [Spring Cloud Gateway — Route Predicates and Filters](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/configuring-route-predicate-factories-and-filter-factories.html)
