# Day 07 — Favorites Service, API Gateway, Docker Integration, and Week 1 Assessment

## Recommended Git Branch

```text
day-07-favorites-gateway
```

Alternative:

```text
day-07-favorites-api-gateway
```

Recommended choice:

```text
day-07-favorites-gateway
```

---

# Objective

Day 07 is the first day where the project becomes a real multi-service system.

By the end of the day, you will have:

```text
Profile Service
Favorites Service
API Gateway
PostgreSQL databases
Dockerized services
Docker Compose integration
```

The main learning goals are:

- Create a new Spring Boot microservice from scratch.
- Define a clear bounded context for Favorites.
- Avoid sharing JPA entities across services.
- Give Favorites Service its own database.
- Introduce Spring Cloud Gateway.
- Understand Gateway routes, predicates, and filters.
- Route external requests through the Gateway.
- Understand Docker service networking.
- Dockerize multiple Spring Boot services.
- Run the system using Docker Compose.
- Verify services communicate using container DNS names.
- Understand why `localhost` behaves differently inside Docker.
- Perform a Week 1 architecture and interview review.

Target request flow:

```text
Client
  |
  v
API Gateway
  |
  +----------------------+
  |                      |
  v                      v
Profile Service      Favorites Service
  |                      |
  v                      v
profile_db            favorites_db
```

---

# 1. Why Introduce Another Microservice Now?

Until now, most work has happened inside:

```text
profile-service
```

That allowed you to focus on:

```text
Spring Core
Spring MVC
Validation
Exception handling
JPA
Relationships
Transactions
```

Day 07 introduces the first true microservice boundary.

The important question now becomes:

> Which service owns this data and business capability?

---

# 2. Favorites Service Responsibility

Favorites Service owns:

```text
Customer favorites / wishlist
```

It should answer questions such as:

```text
Which products has this customer favorited?
Is product X already favorited?
When was the favorite added?
```

It should not own:

```text
Customer profile data
Product details
Payment methods
Orders
Addresses
```

This is a bounded-context decision.

---

# 3. Target Architecture

At the end of the day:

```text
                        Client
                          |
                          v
                   API Gateway
                    /        \
                   /          \
                  v            v
       Profile Service      Favorites Service
              |                  |
              v                  v
         profile_db          favorites_db
```

---

# 4. Two-Hour Agenda

```text
00–10 min   Run Day 06 tests and verify profile-service
10–20 min   Define Favorites bounded context and API contract
20–35 min   Generate favorites-service
35–50 min   Implement entity/repository/service/controller
50–65 min   Add validations, exceptions, and tests
65–80 min   Create API Gateway
80–95 min   Configure routes and test through Gateway
95–105 min  Dockerize profile-service and favorites-service
105–113 min Build Docker Compose stack
113–118 min Failure drills and Docker networking experiment
118–120 min Week 1 interview recap + scorecard
```

---

# 5. Create Favorites Service

Create:

```text
services/favorites-service
```

Recommended package:

```text
com.customer.favorites
```

Recommended dependencies:

```text
Spring Web
Validation
Spring Data JPA
PostgreSQL Driver
Spring Boot Actuator
Spring Boot Test
```

Use the same Java version as Profile Service.

---

# 6. Favorites Domain Model

A favorite should minimally contain:

```text
favoriteId
customerId
productId
createdAt
```

Do not import:

```java
CustomerProfileEntity
```

from Profile Service.

Do not create:

```java
@ManyToOne
private CustomerProfileEntity profile;
```

across services.

Instead use:

```java
UUID customerId;
```

Microservices should communicate using IDs/contracts, not shared persistence entities.

---

# 7. Favorite Entity

```java
@Entity
@Table(
    name = "favorite",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_favorite_customer_product",
            columnNames = {
                "customer_id",
                "product_id"
            }
        )
    }
)
public class FavoriteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long favoriteId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FavoriteEntity() {
    }

    public FavoriteEntity(
            UUID customerId,
            UUID productId,
            Instant createdAt) {

        this.customerId = customerId;
        this.productId = productId;
        this.createdAt = createdAt;
    }

    // getters
}
```

---

# 8. Why No JPA Relationship to Profile?

Favorites Service owns its own persistence model.

Use:

```java
UUID customerId;
```

instead of importing the Profile Service entity.

This avoids:

```text
Shared database coupling
Shared entity coupling
Cross-service foreign keys
Independent deployment problems
```

---

# 9. Database-per-Service Principle

Use:

```text
profile_db
favorites_db
```

not one shared database for both services.

Conceptually:

```text
Profile Service
    owns
profile_db

Favorites Service
    owns
favorites_db
```

Another service should not query another service's tables directly.

---

# 10. Favorites Repository

```java
public interface FavoriteRepository
        extends JpaRepository<FavoriteEntity, Long> {

    List<FavoriteEntity>
    findByCustomerId(UUID customerId);

    Optional<FavoriteEntity>
    findByCustomerIdAndProductId(
            UUID customerId,
            UUID productId
    );

    boolean existsByCustomerIdAndProductId(
            UUID customerId,
            UUID productId
    );

    void deleteByCustomerIdAndProductId(
            UUID customerId,
            UUID productId
    );
}
```

This revises derived-query concepts from Day 04.

---

# 11. Favorites API Contract

Implement:

```http
POST   /customers/{customerId}/favorites/{productId}
GET    /customers/{customerId}/favorites
GET    /customers/{customerId}/favorites/{productId}
DELETE /customers/{customerId}/favorites/{productId}
```

Example response:

```json
{
  "favoriteId": 10,
  "customerId": "...",
  "productId": "...",
  "createdAt": "..."
}
```

---

# 12. Add Favorite Controller

```java
@PostMapping(
    "/customers/{customerId}/favorites/{productId}"
)
@ResponseStatus(HttpStatus.CREATED)
public FavoriteResponse addFavorite(
        @PathVariable UUID customerId,
        @PathVariable UUID productId) {

    return favoriteService.addFavorite(
            customerId,
            productId
    );
}
```

---

# 13. Service Logic

```java
@Service
public class FavoriteService {

    private final FavoriteRepository repository;

    public FavoriteService(
            FavoriteRepository repository) {

        this.repository = repository;
    }

    @Transactional
    public FavoriteResponse addFavorite(
            UUID customerId,
            UUID productId) {

        if (repository
                .existsByCustomerIdAndProductId(
                        customerId,
                        productId
                )) {

            throw new DuplicateFavoriteException(
                    customerId,
                    productId
            );
        }

        FavoriteEntity favorite =
                new FavoriteEntity(
                        customerId,
                        productId,
                        Instant.now()
                );

        FavoriteEntity saved =
                repository.save(favorite);

        return toResponse(saved);
    }
}
```

---

# 14. Duplicate Favorite Rule

Business invariant:

```text
A customer should not favorite the same product twice.
```

Protect it at two levels.

## Application Level

```java
existsByCustomerIdAndProductId(...)
```

## Database Level

```java
@UniqueConstraint(
    columnNames = {
        "customer_id",
        "product_id"
    }
)
```

Why both?

```text
Application check -> friendly error
DB constraint     -> concurrency/data integrity
```

---

# 15. Custom Exceptions

Duplicate:

```java
public class DuplicateFavoriteException
        extends RuntimeException {

    public DuplicateFavoriteException(
            UUID customerId,
            UUID productId) {

        super(
            "Product " + productId +
            " is already favorited by customer " +
            customerId
        );
    }
}
```

Map to:

```text
409 Conflict
```

Missing favorite:

```java
public class FavoriteNotFoundException
        extends RuntimeException {
}
```

Map to:

```text
404 Not Found
```

Reuse the global exception-handling pattern from Day 03.

---

# 16. Get Favorites

```java
@Transactional(readOnly = true)
public List<FavoriteResponse> getFavorites(
        UUID customerId) {

    return repository
            .findByCustomerId(customerId)
            .stream()
            .map(this::toResponse)
            .toList();
}
```

Controller:

```java
@GetMapping(
    "/customers/{customerId}/favorites"
)
public List<FavoriteResponse> getFavorites(
        @PathVariable UUID customerId) {

    return favoriteService
            .getFavorites(customerId);
}
```

---

# 17. Get Single Favorite

Use:

```java
findByCustomerIdAndProductId(...)
```

Expected:

```text
200 if exists
404 if not
```

---

# 18. Delete Favorite

```java
@Transactional
public void deleteFavorite(
        UUID customerId,
        UUID productId) {

    if (!repository
            .existsByCustomerIdAndProductId(
                    customerId,
                    productId
            )) {

        throw new FavoriteNotFoundException();
    }

    repository
            .deleteByCustomerIdAndProductId(
                    customerId,
                    productId
            );
}
```

Controller should return:

```text
204 No Content
```

---

# 19. Favorites Service Tests

Minimum service tests:

```text
shouldAddFavorite
shouldRejectDuplicateFavorite
shouldReturnFavoritesForCustomer
shouldReturnSingleFavorite
shouldThrowWhenFavoriteMissing
shouldDeleteFavorite
```

Repository tests:

```text
findByCustomerId
findByCustomerIdAndProductId
unique constraint
```

Controller tests:

```text
POST returns 201
duplicate returns 409
missing favorite returns 404
DELETE returns 204
```

---

# 20. Favorites Database

Add another PostgreSQL container:

```yaml
favorites-db:
  image: postgres:17

  environment:
    POSTGRES_DB: favorites_db
    POSTGRES_USER: favorites_user
    POSTGRES_PASSWORD: favorites_password

  ports:
    - "5433:5432"

  volumes:
    - favorites-db-data:/var/lib/postgresql/data
```

Local mapping:

```text
Host 5433 -> PostgreSQL container 5432
```

---

# 21. Local Service Ports

Example:

```text
Gateway           -> 8080
Profile Service   -> 8081
Favorites Service -> 8082
```

Local Favorites datasource:

```yaml
server:
  port: 8082

spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/favorites_db
    username: favorites_user
    password: favorites_password
```

---

# 22. Create API Gateway

Create:

```text
gateway/api-gateway
```

Use Spring Cloud Gateway and Actuator.

Target:

```text
Client
   |
   v
localhost:8080
   |
   v
API Gateway
   |
   +--> profile-service
   +--> favorites-service
```

---

# 23. Why Use an API Gateway?

Without Gateway:

```text
Frontend
  |
  +--> profile-service:8081
  +--> favorites-service:8082
  +--> future order-service
  +--> future payment-service
```

With Gateway:

```text
Frontend
   |
   v
api-gateway:8080
   |
   +--> Profile
   +--> Favorites
   +--> Order
   +--> Payment
```

Benefits:

```text
Single entry point
Routing
Cross-cutting filters
Authentication integration
Rate limiting
Logging
Header manipulation
```

---

# 24. Gateway Core Concepts

The three key terms:

```text
Route
Predicate
Filter
```

## Route

Defines where a matching request should go.

## Predicate

Determines whether a request matches a route.

Examples:

```text
Path
Method
Header
Host
Query
Cookie
```

## Filter

Transforms or inspects request/response behavior.

Examples:

```text
StripPrefix
AddRequestHeader
AddResponseHeader
RewritePath
Logging
```

---

# 25. Gateway Configuration Example

```yaml
server:
  port: 8080

spring:
  cloud:
    gateway:
      routes:

        - id: profile-service
          uri: http://localhost:8081
          predicates:
            - Path=/api/profiles/**
          filters:
            - StripPrefix=1

        - id: favorites-service
          uri: http://localhost:8082
          predicates:
            - Path=/api/customers/*/favorites/**
          filters:
            - StripPrefix=1
```

Adjust the path matcher if your exact controller paths differ.

---

# 26. `StripPrefix`

Incoming:

```text
/api/profiles/123
```

With:

```text
StripPrefix=1
```

Gateway removes:

```text
/api
```

and forwards:

```text
/profiles/123
```

---

# 27. Test Through Gateway

Test:

```http
POST http://localhost:8080/api/profiles
```

and:

```http
POST http://localhost:8080/api/customers/{customerId}/favorites/{productId}
```

The integration path should be:

```text
Client -> Gateway -> Service
```

---

# 28. Gateway Logging Filter

For learning, add a simple global filter.

Conceptually:

```java
@Component
public class RequestLoggingFilter
        implements GlobalFilter {

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain) {

        String path =
                exchange.getRequest()
                        .getURI()
                        .getPath();

        return chain.filter(exchange);
    }
}
```

Use proper logging rather than `System.out` in real code.

---

# 29. Reactive Gateway Note

Spring Cloud Gateway's standard model is reactive, so you may see:

```text
Mono
Flux
ServerWebExchange
```

You do not need deep Reactor knowledge today.

Understand only:

```text
Do not put blocking business/database work inside Gateway filters.
```

---

# 30. Gateway Should Not Own Business Logic

Bad:

```text
Gateway validates favorite business rule
Gateway queries favorites database
Gateway calculates order totals
```

Good:

```text
Gateway routes and applies cross-cutting concerns
Favorites Service owns favorite business rules
```

---

# 31. Dockerize the Services

Profile Service example:

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/profile-service.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Favorites Service:

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/favorites-service.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Gateway:

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/api-gateway.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

# 32. Docker Compose Architecture

```text
Compose network
|
+-- api-gateway
+-- profile-service
+-- profile-db
+-- favorites-service
+-- favorites-db
```

Containers on the same Compose network can resolve each other using service names.

---

# 33. Critical Docker Networking Rule

Inside a container:

```text
localhost
```

means:

```text
this same container
```

It does not mean:

```text
your laptop
another container
```

This is a critical interview and debugging concept.

---

# 34. Wrong vs Correct Container URLs

Wrong inside Profile Service container:

```text
jdbc:postgresql://localhost:5432/profile_db
```

Correct:

```text
jdbc:postgresql://profile-db:5432/profile_db
```

Favorites:

```text
jdbc:postgresql://favorites-db:5432/favorites_db
```

Gateway destinations:

```text
http://profile-service:8081
http://favorites-service:8082
```

---

# 35. Docker Profile Configuration

Create:

```text
application-docker.yml
```

Profile Service:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://profile-db:5432/profile_db
```

Favorites Service:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://favorites-db:5432/favorites_db
```

Gateway uses Docker service names for route URIs.

---

# 36. Docker Compose Example

```yaml
services:

  profile-db:
    image: postgres:17
    environment:
      POSTGRES_DB: profile_db
      POSTGRES_USER: profile_user
      POSTGRES_PASSWORD: profile_password
    volumes:
      - profile-db-data:/var/lib/postgresql/data

  favorites-db:
    image: postgres:17
    environment:
      POSTGRES_DB: favorites_db
      POSTGRES_USER: favorites_user
      POSTGRES_PASSWORD: favorites_password
    volumes:
      - favorites-db-data:/var/lib/postgresql/data

  profile-service:
    build:
      context: ./services/profile-service
    environment:
      SPRING_PROFILES_ACTIVE: docker
    depends_on:
      - profile-db

  favorites-service:
    build:
      context: ./services/favorites-service
    environment:
      SPRING_PROFILES_ACTIVE: docker
    depends_on:
      - favorites-db

  api-gateway:
    build:
      context: ./gateway/api-gateway
    environment:
      SPRING_PROFILES_ACTIVE: docker
    ports:
      - "8080:8080"
    depends_on:
      - profile-service
      - favorites-service

volumes:
  profile-db-data:
  favorites-db-data:
```

---

# 37. `depends_on` Limitation

`depends_on` controls startup ordering.

It does not necessarily guarantee:

```text
PostgreSQL is ready to accept connections.
```

Later you can add:

```text
health checks
restart policies
retry behavior
```

---

# 38. Build the Stack

Package services:

```bash
mvn clean package
```

Then:

```bash
docker compose up --build
```

Verify:

```bash
docker compose ps
```

---

# 39. End-to-End Manual Flow

## Create Profile

```http
POST /api/profiles
```

Copy the returned `customerId`.

## Add Favorite

```http
POST /api/customers/{customerId}/favorites/{productId}
```

## Get Favorites

```http
GET /api/customers/{customerId}/favorites
```

## Delete Favorite

```http
DELETE /api/customers/{customerId}/favorites/{productId}
```

All requests should enter through Gateway.

---

# 40. Should Favorites Validate Customer Existence?

Possible designs:

## Option A — Do Not Validate Yet

Favorites Service stores the ID as supplied.

Advantages:

```text
No synchronous dependency
Lower coupling
Better availability
```

Disadvantage:

```text
Potential favorite records for invalid customer IDs
```

## Option B — Call Profile Service

Advantages:

```text
Immediate validation
```

Disadvantages:

```text
Runtime coupling
Latency
Cascading failures
```

For Day 07:

```text
Do not add the synchronous Profile call yet.
```

You will learn service-to-service calls later.

---

# 41. Why No Cross-Service Foreign Key?

Do not model a relational FK from:

```text
favorites_db.customer_id
```

to:

```text
profile_db.customer_id
```

when each service owns an independent database.

Cross-service integrity is usually handled through:

```text
API contracts
Events
Validation workflows
Compensating actions
```

---

# 42. Gateway Route Tests

Verify:

```text
/api/profiles/**
```

routes toward Profile Service.

Verify:

```text
/api/customers/*/favorites/**
```

routes toward Favorites Service.

Use integration/stub downstream testing as appropriate.

---

# 43. Docker Smoke Test

Run:

```bash
docker compose up --build
```

Use only:

```text
localhost:8080
```

for end-to-end API calls.

Verify:

```text
Gateway -> Profile -> profile_db
Gateway -> Favorites -> favorites_db
```

---

# 44. Failure Drill — Stop Favorites Service

Run:

```bash
docker compose stop favorites-service
```

Then call:

```http
GET /api/customers/{id}/favorites
```

Observe:

```text
Gateway response
Gateway logs
Whether Profile Service still works
```

Then restore:

```bash
docker compose start favorites-service
```

---

# 45. Failure Drill — Stop Profile DB

```bash
docker compose stop profile-db
```

Call a Profile endpoint.

Observe:

```text
DB error
Actuator health
Service logs
Gateway response
```

Favorites should remain independently available.

This demonstrates failure isolation.

---

# 46. Gateway vs Load Balancer

## Load Balancer

Distributes traffic among multiple instances of the same service.

```text
Profile 1
Profile 2
Profile 3
```

## API Gateway

Routes different API paths/capabilities.

```text
/profiles  -> Profile Service
/favorites -> Favorites Service
/orders    -> Order Service
```

A Gateway may also integrate load balancing.

---

# 47. Gateway vs Service Discovery

Today, service discovery comes from:

```text
Docker DNS / explicit service names
```

Later Kubernetes provides:

```text
Kubernetes Service DNS
```

You do not need Eureka for this local Compose architecture.

---

# 48. Week 1 Request Flow

You should be able to explain:

```text
Client
   |
   v
API Gateway
   |
   +--> Route Predicate
   +--> Gateway Filter
   |
   v
Controller
   |
   v
Service
   |
   v
Repository
   |
   v
Hibernate
   |
   v
PostgreSQL
```

---

# 49. Interview Questions — Microservices

1. Why create Favorites as a separate service?
2. What is a bounded context?
3. Why should Favorites not import Profile JPA entities?
4. What does database-per-service mean?
5. Why avoid cross-service foreign keys?
6. How is referential integrity handled across microservices?
7. How does service separation improve failure isolation?
8. What coupling would synchronous customer validation introduce?

---

# 50. Interview Questions — Gateway

1. What is an API Gateway?
2. Why use one?
3. What is a Route?
4. What is a Predicate?
5. What is a Filter?
6. What does `StripPrefix` do?
7. Gateway vs load balancer?
8. Gateway vs service discovery?
9. Should business logic live in Gateway?
10. Why should frontend call Gateway instead of internal services?
11. What happens when a downstream service is unavailable?
12. How could Gateway become a bottleneck?

---

# 51. Interview Questions — Docker

1. Image vs container?
2. What is Docker Compose?
3. What is a Docker network?
4. How do Compose services discover each other?
5. What does `localhost` mean inside a container?
6. Why can a localhost DB URL fail inside an app container?
7. Why use `profile-db` as hostname?
8. What is a Docker volume?
9. Why use named volumes for PostgreSQL?
10. What does `docker compose down -v` do?
11. What does `depends_on` guarantee?
12. What does it not guarantee?

---

# 52. Week 1 Spring Review

Be able to answer:

## Core

```text
Bean
IoC
Dependency Injection
Constructor injection
@Primary vs @Qualifier
@Component vs @Service vs @Repository
```

## MVC

```text
DispatcherServlet
HandlerMapping
@RequestBody
@PathVariable vs @RequestParam
PUT vs PATCH
```

## Validation

```text
@Valid vs @Validated
@NotNull vs @NotEmpty vs @NotBlank
@RestControllerAdvice
@ExceptionHandler
```

## JPA

```text
JPA vs Hibernate
@Entity
JpaRepository
Derived queries
JPQL
Persistence context
Dirty checking
N+1
JOIN FETCH
@EntityGraph
Cascade vs orphanRemoval
```

## Transactions

```text
@Transactional
Runtime vs checked rollback
REQUIRED vs REQUIRES_NEW
Self-invocation
Flush vs commit
Isolation
```

---

# 53. Manual Test Checklist

Favorites:

```text
POST favorite -> 201
GET favorites -> 200
GET favorite -> 200
duplicate -> 409
missing -> 404
DELETE -> 204
```

Gateway:

```text
POST profile through Gateway
GET profile through Gateway
POST favorite through Gateway
GET favorites through Gateway
DELETE favorite through Gateway
```

Docker:

```text
All containers start
Databases initialize
Gateway resolves profile-service
Gateway resolves favorites-service
Profile resolves profile-db
Favorites resolves favorites-db
```

---

# 54. Coding Checklist

```text
[ ] favorites-service created
[ ] favorites_db created
[ ] FavoriteEntity created
[ ] Unique customer/product constraint added
[ ] FavoriteRepository created
[ ] FavoriteService created
[ ] Favorites controller created
[ ] DuplicateFavoriteException created
[ ] FavoriteNotFoundException created
[ ] Global exception handling added
[ ] Favorite service tests pass
[ ] Favorite repository tests pass
[ ] Favorite controller tests pass
[ ] api-gateway created
[ ] Profile route configured
[ ] Favorites route configured
[ ] Gateway request tested
[ ] Request logging filter added/understood
[ ] Profile Service Dockerfile created
[ ] Favorites Service Dockerfile created
[ ] Gateway Dockerfile created
[ ] Docker Compose updated
[ ] Docker profile configuration added
[ ] Service names used instead of localhost
[ ] End-to-end flow works through Gateway
[ ] Favorites failure drill completed
[ ] DB failure drill completed
[ ] Week 1 interview review completed
[ ] docs/day-07.md updated
[ ] Git commit created
```

---

# 55. Scorecard

```text
Favorites Service CRUD works                      1/1
Favorites uses independent DB                     1/1
Duplicate business rule works                     1/1
Gateway routes Profile requests                   1/1
Gateway routes Favorites requests                 1/1
Services run in Docker                            1/1
Docker networking understood                      1/1
Failure isolation drill completed                 1/1
Week 1 concepts explained                         1/1
Tests + docs + commit complete                    1/1

TOTAL                                            /10
```

Target:

```text
8/10 minimum
10/10 preferred
```

---

# 56. Recommended Day Notes

Create:

```text
docs/day-07.md
```

Record:

```text
Favorites responsibility:
...

Why separate service:
...

Database ownership:
...

Gateway Profile route:
...

Gateway Favorites route:
...

Docker hostnames:
...

localhost experiment:
...

Favorites failure result:
...

Profile DB failure result:
...

Week 1 topics still unclear:
...
```

---

# 57. Topics Deliberately Deferred

Do not add today:

```text
JWT security
Redis
Kafka
Resilience4j
Service-to-service REST calls
Eureka
Config Server
Kubernetes
Distributed tracing
Rate limiting
Circuit breakers
```

Day 07 stays focused on:

```text
Microservice boundaries
Favorites Service
Database per service
API Gateway
Docker
Docker Compose networking
Week 1 integration
```

---

# Definition of Done

Day 07 is complete when you can demonstrate:

```text
                        Client
                          |
                          v
                     API Gateway
                    /           \
                   v             v
          Profile Service    Favorites Service
                |                 |
                v                 v
           profile_db         favorites_db
```

You must be able to explain:

```text
Why Favorites is a separate bounded context
Why its database is separate
Why it stores customerId rather than ProfileEntity
Why Gateway exists
What Route / Predicate / Filter mean
Why localhost fails between containers
Why Docker service names work
Why one service failing does not need to break the other
```

You should also be able to run:

```bash
docker compose up --build
```

and perform Profile/Favorites operations through:

```text
http://localhost:8080
```

If you can stop Favorites Service, observe the failure through Gateway, verify Profile Service still works, restore Favorites, and explain why the isolation exists, Day 07 is complete.
