# Day 10 — Spring Security Fundamentals and Method Security

## Recommended Git Branch

`day-10-spring-security`

Alternative:

`day-10-security-method-authorization`

Recommended choice:

`day-10-spring-security`

## Objective

Day 10 introduces Spring Security at the service layer.

The goal is to secure the Product Service first, because it already has a clear split between read and write operations.

By the end of the day, you should understand and demonstrate:

- `SecurityFilterChain`
- `FilterChainProxy`
- authentication
- authorization
- `SecurityContext`
- users, roles, and authorities
- HTTP Basic for learning
- endpoint authorization rules
- `@EnableMethodSecurity`
- `@PreAuthorize`
- `@PostAuthorize`
- 401 vs 403
- CSRF basics
- security testing with MockMvc
- why authentication should not be mixed with business logic

For Day 10, use in-memory users and HTTP Basic only. JWT/OAuth2 comes on Day 11.


---

## 1. Day 10 Build Target

Secure Product Service with these rules:

```text
GET /products/**
    USER or ADMIN

POST /products
    ADMIN only

PUT /products/**
    ADMIN only

PATCH /products/**
    ADMIN only

DELETE /products/**
    ADMIN only

/actuator/health
    public
```

Expected behavior:

```text
Anonymous request -> 401
Authenticated USER doing GET -> 200
Authenticated USER doing POST -> 403
Authenticated ADMIN doing POST -> success
```


---

## 2. Two-Hour Agenda

```text
00–10 min   Run Day 09 tests and verify Product Service
10–20 min   Authentication vs authorization
20–35 min   Add Spring Security dependency
35–50 min   Configure SecurityFilterChain
50–65 min   Add in-memory users and roles
65–80 min   Protect endpoints by HTTP method/path
80–95 min   Add @EnableMethodSecurity and @PreAuthorize
95–105 min  Understand 401 vs 403 and SecurityContext
105–115 min Write MockMvc security tests
115–120 min Filter-chain interview review + scorecard
```


---

## 3. Add Spring Security Dependency

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

After adding the dependency, previously public endpoints may immediately start returning:

```text
401 Unauthorized
```

because Spring Security inserts a filter chain before your controller.

```text
HTTP Request
   |
   v
Spring Security Filters
   |
   v
DispatcherServlet
   |
   v
Controller
```


---

## 4. Authentication vs Authorization

### Authentication

Question:

```text
Who are you?
```

Examples:

```text
username/password
JWT
OAuth2 login
API key
client certificate
```

### Authorization

Question:

```text
What are you allowed to do?
```

Examples:

```text
USER can read
ADMIN can create/update/delete
```

Interview answer:

> Authentication establishes identity. Authorization decides what that authenticated identity is allowed to do.


---

## 5. Security Filter Chain

Conceptually:

```text
Request
  |
  v
FilterChainProxy
  |
  v
SecurityFilterChain
  |
  +--> authentication filters
  +--> authorization
  +--> exception handling
  +--> CSRF
  |
  v
DispatcherServlet
```

`FilterChainProxy` is Spring Security's main servlet-filter delegate. It coordinates one or more `SecurityFilterChain` instances.


---

## 6. Create `SecurityFilterChain`

```java
@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http)
            throws Exception {

        http
            .csrf(csrf ->
                csrf.disable()
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
                .hasAnyRole(
                    "USER",
                    "ADMIN"
                )

                .requestMatchers(
                    HttpMethod.POST,
                    "/products/**"
                )
                .hasRole("ADMIN")

                .requestMatchers(
                    HttpMethod.PUT,
                    "/products/**"
                )
                .hasRole("ADMIN")

                .requestMatchers(
                    HttpMethod.PATCH,
                    "/products/**"
                )
                .hasRole("ADMIN")

                .requestMatchers(
                    HttpMethod.DELETE,
                    "/products/**"
                )
                .hasRole("ADMIN")

                .anyRequest()
                .authenticated()
            )

            .httpBasic(
                Customizer.withDefaults()
            );

        return http.build();
    }
}
```

Specific rules should appear before broader rules.


---

## 7. `permitAll()` and `authenticated()`

```java
.requestMatchers(
    "/actuator/health"
)
.permitAll()
```

means no login is required.

```java
.anyRequest()
.authenticated()
```

means all remaining requests need an authenticated user.

Do not publicly expose sensitive Actuator endpoints such as environment/configuration details.


---

## 8. Roles vs Authorities

Spring Security works internally with `GrantedAuthority`.

A role is a naming convention built on top of authorities.

```java
.hasRole("ADMIN")
```

typically checks for:

```text
ROLE_ADMIN
```

Do not normally write:

```java
.hasRole("ROLE_ADMIN")
```

Use either:

```java
.hasRole("ADMIN")
```

or:

```java
.hasAuthority("ROLE_ADMIN")
```


---

## 9. In-Memory Users

For Day 10 only:

```java
@Bean
UserDetailsService userDetailsService(
        PasswordEncoder passwordEncoder) {

    UserDetails user =
            User.withUsername("user")
                .password(
                    passwordEncoder.encode(
                        "user123"
                    )
                )
                .roles("USER")
                .build();

    UserDetails admin =
            User.withUsername("admin")
                .password(
                    passwordEncoder.encode(
                        "admin123"
                    )
                )
                .roles(
                    "USER",
                    "ADMIN"
                )
                .build();

    return new InMemoryUserDetailsManager(
            user,
            admin
    );
}
```

This is purely a learning setup.


---

## 10. Password Encoder

```java
@Bean
PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

Passwords should be hashed, not stored as plain text.

Do not use:

```text
plain text
MD5
SHA-1
plain SHA-256
```

as password-storage strategies.


---

## 11. HTTP Basic

```java
.httpBasic(
    Customizer.withDefaults()
)
```

HTTP Basic sends credentials in an Authorization header.

Conceptually:

```text
Authorization: Basic <base64(username:password)>
```

Base64 is not encryption.

Use Basic authentication only over HTTPS in real environments.


---

## 12. Manual Authentication Tests

Anonymous GET:

```bash
curl -i   http://localhost:8083/products/<product-id>
```

Expected:

```text
401
```

USER GET:

```bash
curl -i   -u user:user123   http://localhost:8083/products/<product-id>
```

Expected:

```text
200
```

USER POST:

```bash
curl -i   -u user:user123   -X POST   http://localhost:8083/products   -H "Content-Type: application/json"   -d '{
    "sku":"SKU-100",
    "name":"Keyboard",
    "price":2500,
    "inventoryStatus":"IN_STOCK"
  }'
```

Expected:

```text
403
```

ADMIN POST:

```bash
curl -i   -u admin:admin123   -X POST   http://localhost:8083/products   -H "Content-Type: application/json"   -d '{
    "sku":"SKU-100",
    "name":"Keyboard",
    "price":2500,
    "inventoryStatus":"IN_STOCK"
  }'
```

Expected:

```text
201
```


---

## 13. 401 vs 403

```text
401 Unauthorized
    authentication missing or invalid

403 Forbidden
    authenticated, but insufficient permission
```

Interview shortcut:

```text
401 -> Who are you?
403 -> I know who you are, but you cannot do this.
```


---

## 14. `SecurityContext`

After successful authentication, Spring stores security information in:

```text
SecurityContext
    |
    v
Authentication
    |
    +--> principal
    +--> authorities
    +--> authenticated flag
```

You can inject:

```java
Authentication authentication
```

into a controller method for learning/debugging.


---

## 15. Current User Example

```java
@GetMapping("/whoami")
public Map<String, Object> whoAmI(
        Authentication authentication) {

    return Map.of(
        "name",
        authentication.getName(),

        "authorities",
        authentication.getAuthorities()
    );
}
```

Use this only as a learning/debug endpoint and protect/remove it appropriately later.


---

## 16. URL Security vs Method Security

URL security:

```java
.requestMatchers(
    HttpMethod.POST,
    "/products/**"
)
.hasRole("ADMIN")
```

Method security:

```java
@PreAuthorize("hasRole('ADMIN')")
public ProductResponse createProduct(...) {
}
```

Recommended Day 10 pattern:

```text
SecurityFilterChain -> coarse HTTP access
@PreAuthorize        -> critical service authorization
```


---

## 17. `@EnableMethodSecurity`

```java
@EnableMethodSecurity
```

enables annotations such as:

```text
@PreAuthorize
@PostAuthorize
```

Without it, method-security annotations are not enforced.


---

## 18. `@PreAuthorize`

```java
@PreAuthorize(
    "hasRole('ADMIN')"
)
@Transactional
public ProductResponse createProduct(
        CreateProductRequest request) {

    ...
}
```

Other examples:

```java
@PreAuthorize(
    "hasAnyRole('USER', 'ADMIN')"
)
```

```java
@PreAuthorize(
    "hasAuthority('product:write')"
)
```


---

## 19. `@PostAuthorize`

Example:

```java
@PostAuthorize(
    "returnObject.ownerId == authentication.name"
)
```

Authorization occurs after the method returns.

It is less common than `@PreAuthorize` for ordinary CRUD APIs, but know what it does.


---

## 20. Product Service Example

```java
@Service
public class ProductService {

    @PreAuthorize(
        "hasAnyRole('USER', 'ADMIN')"
    )
    @Transactional(readOnly = true)
    @Cacheable(
        cacheNames = "products",
        key = "#productId"
    )
    public ProductResponse getProduct(
            UUID productId) {
        ...
    }

    @PreAuthorize(
        "hasRole('ADMIN')"
    )
    @Transactional
    public ProductResponse createProduct(
            CreateProductRequest request) {
        ...
    }
}
```

Now security, transaction, and cache concerns may all be applied around the method through Spring infrastructure.


---

## 21. Multiple Interceptors

A service method can have:

```text
@PreAuthorize
@Transactional
@Cacheable
```

Conceptually:

```text
caller
  |
  v
security interceptor
  |
  v
transaction/cache interception
  |
  v
target method
```

Exact ordering can matter in advanced scenarios. For Day 10, understand that these are cross-cutting concerns applied around the method.


---

## 22. CSRF

CSRF means:

```text
Cross-Site Request Forgery
```

It matters especially when the browser automatically sends authentication credentials, such as session cookies.

For this learning REST API using curl and HTTP Basic, you may disable CSRF:

```java
.csrf(csrf ->
    csrf.disable()
)
```

Do not learn the rule:

```text
always disable CSRF
```

Instead understand that CSRF requirements depend on how authentication credentials are transported.


---

## 23. Gateway Consideration

Current flow:

```text
Client
  |
  v
Gateway
  |
  v
Product Service
```

Product Service performs authentication/authorization on Day 10.

Test through Gateway too:

```bash
curl -i   -u user:user123   http://localhost:8080/api/products/<id>
```

Verify the Authorization header reaches Product Service.


---

## 24. Gateway vs Service Security

Possible designs:

```text
Gateway-only
Service-only
Both
```

For Day 10:

```text
secure Product Service directly
```

This ensures the service protects itself even if reached without Gateway.

Day 11 will move toward JWT/OAuth2 resource-server security.


---

## 25. Add Security Test Dependency

```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

Useful test support includes:

```text
@WithMockUser
httpBasic(...)
user(...)
```


---

## 26. MockMvc — Anonymous Test

```java
@WebMvcTest(ProductController.class)
@Import(SecurityConfiguration.class)
class ProductControllerSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProductService productService;

    @Test
    void anonymousUserShouldReceive401()
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
}
```


---

## 27. MockMvc — USER GET

```java
@Test
@WithMockUser(
    username = "user",
    roles = "USER"
)
void userCanReadProduct()
        throws Exception {

    UUID id = UUID.randomUUID();

    when(
        productService.getProduct(id)
    )
    .thenReturn(
        sampleProduct(id)
    );

    mockMvc.perform(
        get("/products/{id}", id)
    )
    .andExpect(
        status().isOk()
    );
}
```


---

## 28. MockMvc — USER POST

```java
@Test
@WithMockUser(
    username = "user",
    roles = "USER"
)
void userCannotCreateProduct()
        throws Exception {

    mockMvc.perform(
        post("/products")
            .contentType(
                MediaType.APPLICATION_JSON
            )
            .content(
                "{\"sku\":\"SKU-1\",\"name\":\"Keyboard\",\"price\":1000,\"inventoryStatus\":\"IN_STOCK\"}"
            )
    )
    .andExpect(
        status().isForbidden()
    );
}
```


---

## 29. ADMIN POST Test

Use:

```java
@WithMockUser(
    username = "admin",
    roles = "ADMIN"
)
```

Expected:

```text
201 Created
```

This verifies ADMIN authorization.


---

## 30. `@WithMockUser`

Example:

```java
@WithMockUser(
    username = "sayantani",
    roles = {
        "USER",
        "ADMIN"
    }
)
```

This creates a mock SecurityContext for authorization tests.

It does not test actual password authentication.


---

## 31. HTTP Basic Test Helper

To exercise actual Basic authentication:

```java
mockMvc.perform(
    get("/products/{id}", id)
        .with(
            httpBasic(
                "user",
                "user123"
            )
        )
)
.andExpect(
    status().isOk()
);
```


---

## 32. Method Security Test

```java
@SpringBootTest
class ProductMethodSecurityTest {

    @Autowired
    ProductService productService;

    @Test
    @WithMockUser(roles = "USER")
    void userCannotCallCreateProduct() {

        assertThrows(
            AccessDeniedException.class,
            () -> productService
                    .createProduct(
                        sampleRequest()
                    )
        );
    }
}
```

This must run against the Spring-managed bean so the method-security proxy is active.


---

## 33. Why Plain Mockito Is Not Enough

If you do:

```java
new ProductService(...)
```

Spring Security's proxy is absent.

Therefore:

```java
@PreAuthorize
```

will not execute.

Same lesson as:

```text
@Transactional
@Cacheable
```

Proxy-based cross-cutting behavior requires the Spring-managed bean.


---

## 34. Authentication Failure vs Authorization Failure

Invalid credentials:

```bash
curl -i   -u user:wrongPassword   http://localhost:8083/products/<id>
```

Expected:

```text
401
```

Valid USER credentials on ADMIN operation:

```text
403
```


---

## 35. `AuthenticationEntryPoint` and `AccessDeniedHandler`

Authentication missing/invalid:

```text
AuthenticationEntryPoint -> 401
```

Authenticated but denied:

```text
AccessDeniedHandler -> 403
```

Optional stretch goal: return your standard JSON API error structure for both.


---

## 36. Security Context Lifecycle

Conceptually:

```text
Request enters
  |
  v
Authentication established
  |
  v
SecurityContext populated
  |
  v
Controller/service executes
  |
  v
Request completes
  |
  v
context cleaned up/managed
```

Do not store the current user in a global static variable yourself.


---

## 37. Thread Context Concept

In servlet applications, Spring Security commonly associates SecurityContext with the current execution thread.

This means custom asynchronous threads need explicit security-context propagation.

You only need the concept today.


---

## 38. Security and Caching

Product GET now has:

```text
authorization
+
caching
```

Unauthorized callers must never obtain cached data merely because the value already exists in Redis.

Caching is a performance concern, not a security bypass.


---

## 39. Security and Actuator

For today:

```text
/actuator/health -> public
```

Do not automatically expose:

```text
/actuator/env
/actuator/configprops
/actuator/beans
```

to unauthenticated users.


---

## 40. Common Mistakes

### Hardcoded production users

Day 10 users are only for learning.

### Role checks in controller `if` statements

Use Spring authorization infrastructure instead.

### UI-only authorization

Hiding a React button is not security. Backend authorization is authoritative.

### CORS vs security confusion

```text
CORS -> which browser origins may call
Authentication -> who are you
Authorization -> what may you do
```

### 401 for everything

Use 401 for authentication failures and 403 for permission failures.

### Wrong role prefix

Prefer `hasRole("ADMIN")`, not `hasRole("ROLE_ADMIN")`.

### Method security not enabled

Always verify `@EnableMethodSecurity` with a negative test.


---

## 41. Security Debug Logging

For learning:

```yaml
logging:
  level:
    org.springframework.security: DEBUG
```

Observe:

```text
request matching
authentication
authorization decisions
filter processing
```

Disable excessive debug logging in production.


---

## 42. Access Matrix

Run this matrix:

```text
Endpoint               Anonymous   USER   ADMIN
------------------------------------------------
GET /products/{id}        401       200     200
POST /products             401       403     201
PUT /products/{id}         401       403     200
DELETE /products/{id}      401       403     204
/actuator/health           200       200     200
```

Repeat the Product API rows through:

```text
http://localhost:8080/api/products/**
```


---

## 43. Interview Questions

1. What is authentication?
2. What is authorization?
3. Authentication vs authorization?
4. What is Spring Security?
5. What is `FilterChainProxy`?
6. What is `SecurityFilterChain`?
7. What is `SecurityContext`?
8. What is `Authentication`?
9. What is a principal?
10. What is a `GrantedAuthority`?
11. Role vs authority?
12. Why does `hasRole("ADMIN")` correspond to `ROLE_ADMIN`?
13. What does `permitAll()` do?
14. What does `authenticated()` do?
15. Why does matcher ordering matter?
16. What does `@EnableMethodSecurity` do?
17. What does `@PreAuthorize` do?
18. What does `@PostAuthorize` do?
19. URL security vs method security?
20. Why can `new ProductService()` bypass `@PreAuthorize`?
21. 401 vs 403?
22. What is `AuthenticationEntryPoint`?
23. What is `AccessDeniedHandler`?
24. What is CSRF?
25. Why can a stateless bearer-token API have different CSRF considerations?
26. Why should Basic authentication use HTTPS?
27. Why must backend authorization exist even if UI hides actions?


---

## 44. Testing Requirements

Minimum:

```text
anonymous GET -> 401
USER GET -> 200
ADMIN GET -> 200
USER POST -> 403
ADMIN POST -> 201
USER DELETE -> 403
ADMIN DELETE -> 204
health -> 200 without login
method security USER -> denied
method security ADMIN -> success
```


---

## 45. Coding Checklist

```text
[ ] spring-boot-starter-security added

[ ] spring-security-test added

[ ] SecurityConfiguration created

[ ] SecurityFilterChain bean created

[ ] /actuator/health permitAll

[ ] GET products protected for USER/ADMIN

[ ] write endpoints protected for ADMIN

[ ] HTTP Basic enabled

[ ] PasswordEncoder created

[ ] USER account created

[ ] ADMIN account created

[ ] anonymous GET -> 401 verified

[ ] USER GET works

[ ] USER POST -> 403 verified

[ ] ADMIN POST works

[ ] @EnableMethodSecurity added

[ ] @PreAuthorize added

[ ] method-security test added

[ ] 401 vs 403 demonstrated

[ ] SecurityContext inspected

[ ] Gateway forwards Authorization header

[ ] direct Product calls tested

[ ] Gateway Product calls tested

[ ] security DEBUG logs inspected

[ ] docs/day-10.md updated

[ ] Git commit completed
```


---

## 46. Scorecard

```text
Security configuration works                 1/1
Authentication demonstrated                  1/1
GET authorization rules work                1/1
ADMIN write rules work                      1/1
401 vs 403 demonstrated                     1/1
Method security works                       1/1
SecurityContext understood                  1/1
Gateway security path verified              1/1
Security tests pass                         1/1
Docs + interview recap + commit             1/1

TOTAL                                      /10
```

Target:

```text
8/10 minimum
10/10 preferred
```


---

## 47. Recommended Day Notes

Create:

```text
docs/day-10.md
```

Record:

```text
SecurityFilterChain:
...

Public endpoints:
...

USER permissions:
...

ADMIN permissions:
...

401 experiment:
...

403 experiment:
...

SecurityContext:
...

@PreAuthorize example:
...

Gateway Authorization-header result:
...

CSRF explanation:
...

Still unclear:
...
```


---

## 48. Topics Deliberately Deferred

Do not go deep into today:

```text
JWT
OAuth2 Resource Server
JWKS
refresh tokens
Cognito/Keycloak/Auth0
custom AuthenticationProvider
database-backed users
MFA
OIDC
OAuth2 login
fine-grained ABAC
```

These begin on Day 11.


---

# Day 10 Definition of Done

Day 10 is complete when you can explain this flow:

```text
HTTP Request
     |
     v
FilterChainProxy
     |
     v
SecurityFilterChain
     |
     +--> Authenticate
     |
     +--> Populate SecurityContext
     |
     +--> Authorize
     |
     +--> allowed
     |      |
     |      v
     |   DispatcherServlet
     |      |
     |      v
     |   Controller
     |      |
     |      v
     |   @PreAuthorize
     |      |
     |      v
     |   ProductService
     |
     +--> denied
            |
            +--> 401 or 403
```

You must demonstrate:

```text
Anonymous GET -> 401
USER GET -> 200
USER POST -> 403
ADMIN POST -> 201
```

You should be able to explain without notes:

```text
authentication vs authorization
FilterChainProxy
SecurityFilterChain
SecurityContext
Authentication
role vs authority
401 vs 403
@PreAuthorize
@EnableMethodSecurity
CSRF
proxy-based security
```

If you can run the access matrix directly against Product Service and through API Gateway, and negative tests prove unauthorized operations never reach business logic, Day 10 is complete.
