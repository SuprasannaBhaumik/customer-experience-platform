# Day 09 — Product Service, Redis, and Spring Cache

## Recommended Git Branch

`day-09-product-redis-cache`

## Objective

Day 09 introduces a read-heavy Product Service and Redis caching. By the end of the day you should understand and demonstrate:

- Product Service bounded context
- PostgreSQL-backed product catalog
- Redis in Docker
- Spring Cache abstraction
- `@EnableCaching`
- `@Cacheable`
- `@CachePut`
- `@CacheEvict`
- cache-aside
- cache hit / miss
- TTL
- stale data
- cache key design
- cache invalidation
- self-invocation caveat
- Redis failure behavior

Target architecture:

```text
Client
  |
  v
API Gateway
  |
  v
Product Service
  |
  +--> Redis
  |
  +--> product_db
```

Read flow:

```text
GET product
   |
   v
Check Redis
   |
   +--> hit  -> return cached value
   |
   +--> miss -> query PostgreSQL
                  |
                  v
              cache result
                  |
                  v
               return
```

---

## 1. Why Product Service Is a Good Cache Candidate

Product data is commonly:

```text
read frequently
updated less frequently
requested repeatedly
```

That makes it a strong cache candidate.

Do not assume all data should be cached. Payment authorization, for example, has very different consistency requirements.

---

## 2. Two-Hour Agenda

```text
00–10 min   Verify Day 08 stack/config
10–20 min   Define Product API/domain
20–35 min   Create Product Service + DB
35–50 min   Implement CRUD/search
50–65 min   Add Redis
65–80 min   Add @EnableCaching + @Cacheable
80–90 min   Add @CachePut / @CacheEvict
90–100 min  Configure TTL
100–108 min Observe hit/miss
108–115 min Stale-data + Redis-down drills
115–120 min Tests + interview recap
```

---

## 3. Product Service Responsibility

Product Service owns:

```text
product catalog
SKU
name
description
price
inventory status
```

It does not own:

```text
favorites
orders
payments
customer profile
addresses
```

Suggested package:

```text
com.customer.product
```

Suggested service port:

```text
8083
```

Current layout:

```text
8080 Gateway
8081 Profile
8082 Favorites
8083 Product
```

---

## 4. Product Database

Add PostgreSQL in Docker Compose:

```yaml
product-db:
  image: postgres:17
  environment:
    POSTGRES_DB: product_db
    POSTGRES_USER: product_user
    POSTGRES_PASSWORD: product_password
  ports:
    - "5435:5432"
  volumes:
    - product-db-data:/var/lib/postgresql/data
```

Local app URL:

```text
jdbc:postgresql://localhost:5435/product_db
```

Docker app URL:

```text
jdbc:postgresql://product-db:5432/product_db
```

---

## 5. Product Entity

```java
@Entity
@Table(
    name = "product",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_product_sku",
            columnNames = "sku"
        )
    }
)
public class ProductEntity {

    @Id
    private UUID productId;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InventoryStatus inventoryStatus;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ProductEntity() {
    }

    // constructor/getters/setters
}
```

Enum:

```java
public enum InventoryStatus {
    IN_STOCK,
    LOW_STOCK,
    OUT_OF_STOCK
}
```

Prefer:

```java
@Enumerated(EnumType.STRING)
```

over `ORDINAL`, because ordinal values break easily when enum ordering changes.

---

## 6. Product Repository

```java
public interface ProductRepository
        extends JpaRepository<ProductEntity, UUID> {

    Optional<ProductEntity>
    findBySkuIgnoreCase(String sku);

    List<ProductEntity>
    findByNameContainingIgnoreCase(
            String name
    );

    boolean existsBySkuIgnoreCase(
            String sku
    );
}
```

---

## 7. Product API

Implement:

```http
POST   /products
GET    /products/{productId}
GET    /products?name={name}
PUT    /products/{productId}
PATCH  /products/{productId}
DELETE /products/{productId}
```

Optional:

```http
GET /products/sku/{sku}
```

---

## 8. Request / Response DTOs

```java
public record CreateProductRequest(

        @NotBlank
        String sku,

        @NotBlank
        String name,

        String description,

        @NotNull
        @PositiveOrZero
        BigDecimal price,

        @NotNull
        InventoryStatus inventoryStatus
) {
}
```

```java
public record ProductResponse(
        UUID productId,
        String sku,
        String name,
        String description,
        BigDecimal price,
        InventoryStatus inventoryStatus
) {
}
```

Keep JPA entities out of controller responses.

---

## 9. Basic Product Service Before Caching

```java
@Service
public class ProductService {

    private final ProductRepository repository;

    public ProductService(
            ProductRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(
            UUID productId) {

        ProductEntity entity =
                repository.findById(productId)
                        .orElseThrow(() ->
                                new ProductNotFoundException(
                                        productId
                                )
                        );

        return toResponse(entity);
    }
}
```

First make DB-only behavior correct. Then add cache.

---

## 10. Add Redis to Docker Compose

```yaml
redis:
  image: redis:7
  ports:
    - "6379:6379"
```

Optional:

```yaml
volumes:
  - redis-data:/data
```

Redis is an in-memory key-value store.

Conceptually:

```text
products::<productId>
      |
      v
serialized ProductResponse
```

---

## 11. Redis Configuration

Local:

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
```

Docker:

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:redis}
      port: ${REDIS_PORT:6379}
```

Same principle as Day 08:

```text
local app -> localhost
Docker app -> service name
```

---

## 12. Enable Spring Caching

```java
@SpringBootApplication
@EnableCaching
public class ProductServiceApplication {
}
```

`@EnableCaching` turns on Spring's cache interception infrastructure.

Conceptually:

```text
Caller
  |
  v
Spring cache proxy
  |
  +--> check cache
  |
  +--> invoke method if needed
```

This is proxy-based, just like `@Transactional`.

---

## 13. `@Cacheable`

```java
@Cacheable(
    cacheNames = "products",
    key = "#productId"
)
@Transactional(readOnly = true)
public ProductResponse getProduct(
        UUID productId) {

    ProductEntity entity =
            repository.findById(productId)
                    .orElseThrow(() ->
                            new ProductNotFoundException(
                                    productId
                            )
                    );

    return toResponse(entity);
}
```

Behavior:

```text
cache hit
    -> method is skipped
    -> cached value returned

cache miss
    -> method runs
    -> DB queried
    -> result cached
    -> result returned
```

---

## 14. Cache-Aside Pattern

Spring's `@Cacheable` gives cache-aside style behavior:

```text
request
  |
  v
cache lookup
  |
  +--> hit -> return
  |
  +--> miss
         |
         v
       database
         |
         v
      put in cache
         |
         v
       return
```

PostgreSQL remains the source of truth.

---

## 15. Cache Hit / Miss Experiment

Add temporary logging:

```java
log.info(
    "Loading product {} from database",
    productId
);
```

First request:

```http
GET /products/{id}
```

Expected:

```text
service method runs
SQL appears
cache populated
```

Second identical request:

```text
service method not invoked
no product SELECT
value comes from Redis
```

This experiment is mandatory.

---

## 16. Cache Key Design

With:

```java
key = "#productId"
```

the logical key becomes something like:

```text
products::<UUID>
```

Good cache keys are:

```text
stable
unique
predictable
small
```

Avoid mutable object instances as keys.

---

## 17. `@CachePut`

Use for update when you want DB and cache updated immediately.

```java
@CachePut(
    cacheNames = "products",
    key = "#productId"
)
@Transactional
public ProductResponse updateProduct(
        UUID productId,
        UpdateProductRequest request) {

    ProductEntity entity =
            repository.findById(productId)
                    .orElseThrow(() ->
                            new ProductNotFoundException(
                                    productId
                            )
                    );

    entity.setName(request.name());
    entity.setPrice(request.price());
    entity.setInventoryStatus(
            request.inventoryStatus()
    );

    return toResponse(entity);
}
```

`@CachePut` always executes the method and stores the returned value.

---

## 18. `@Cacheable` vs `@CachePut`

```text
@Cacheable
    may skip method execution on hit

@CachePut
    always invokes method
    refreshes cache with returned value
```

---

## 19. `@CacheEvict`

Use on delete:

```java
@CacheEvict(
    cacheNames = "products",
    key = "#productId"
)
@Transactional
public void deleteProduct(
        UUID productId) {

    if (!repository.existsById(productId)) {
        throw new ProductNotFoundException(
                productId
        );
    }

    repository.deleteById(productId);
}
```

This removes stale cached data.

---

## 20. Update Strategy: Put vs Evict

Two valid strategies:

```text
@CachePut
    update DB
    immediately update cache
```

or:

```text
@CacheEvict
    update DB
    remove cache value
    next GET repopulates
```

Choose intentionally.

For Product Service, `@CachePut` is convenient if the update response exactly matches the cached representation.

---

## 21. Cache Invalidation

Caching is easy.

Invalidation is hard.

Example:

```text
DB price    = 1200
cached price = 1000
```

If the cache is not updated or evicted, clients see stale data.

Every write path must answer:

```text
Which cache entries become invalid?
```

---

## 22. TTL

TTL means:

```text
Time To Live
```

Example:

```text
10 minutes
```

After TTL expires:

```text
cache entry disappears
next request queries DB
result is cached again
```

TTL limits stale-data duration.

---

## 23. Configure TTL

Example:

```java
@Configuration
public class CacheConfiguration {

    @Bean
    RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory) {

        RedisCacheConfiguration config =
                RedisCacheConfiguration
                    .defaultCacheConfig()
                    .entryTtl(
                        Duration.ofMinutes(10)
                    );

        return RedisCacheManager
                .builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}
```

You can later configure different TTLs per cache.

---

## 24. Cache Serialization

Redis stores serialized values.

Conceptually:

```text
ProductResponse
    |
    v
serializer
    |
    v
Redis bytes/JSON-like representation
```

Know that serialization choice affects:

```text
readability
compatibility
storage size
versioning
```

Day 09 does not need advanced serializer tuning.

---

## 25. Inspect Redis

```bash
docker exec -it redis redis-cli
```

For learning:

```bash
KEYS *
```

Then:

```bash
TTL "products::<id>"
```

Delete a key:

```bash
DEL "products::<id>"
```

In production, prefer `SCAN` over `KEYS *` on large datasets.

---

## 26. Search Caching

Do not cache arbitrary search results yet.

Search cache keys can involve:

```text
query
page
size
sort
filters
```

This can create excessive key cardinality.

Day 09 should cache:

```text
GET /products/{id}
```

only.

---

## 27. Stale Data Experiment

1. GET a product to populate cache.
2. Modify the same product directly in PostgreSQL.
3. GET again.
4. Observe the old cached value.
5. Delete the Redis key.
6. GET again.
7. Observe the fresh DB value.

This proves cache staleness.

---

## 28. Why Redis Is Not Source of Truth

If Redis is emptied:

```text
Product Service should still rebuild state from PostgreSQL.
```

Redis is a performance layer.

PostgreSQL remains authoritative.

---

## 29. Redis Failure Drill

Stop Redis:

```bash
docker compose stop redis
```

Call:

```http
GET /api/products/{id}
```

Observe behavior.

Important design question:

```text
Should Product reads fail only because cache is unavailable?
```

Often you want:

```text
Redis failure -> fall back to DB
```

This is called a fail-open style cache strategy.

You do not need to fully productionize this today.

---

## 30. `CacheErrorHandler`

Spring supports custom cache error handling.

Conceptually:

```java
public class LoggingCacheErrorHandler
        implements CacheErrorHandler {
}
```

It can decide whether to:

```text
propagate Redis errors
or
log them and continue
```

Optional for Day 09.

---

## 31. Self-Invocation Problem

```java
@Service
public class ProductService {

    public ProductResponse wrapper(UUID id) {
        return getProduct(id);
    }

    @Cacheable(...)
    public ProductResponse getProduct(UUID id) {
        ...
    }
}
```

If `wrapper()` internally calls `this.getProduct()`, the cache proxy may be bypassed.

Conceptual flow:

```text
External caller
  |
  v
Spring proxy
  |
  v
wrapper()
  |
  v
this.getProduct()
```

The second call does not re-enter the proxy.

Same principle as Day 06 `@Transactional`.

---

## 32. `condition` and `unless`

Conditionally apply caching:

```java
@Cacheable(
    cacheNames = "products",
    key = "#productId",
    condition = "#productId != null"
)
```

Prevent some results being cached:

```java
@Cacheable(
    cacheNames = "products",
    key = "#productId",
    unless = "#result == null"
)
```

If missing products throw exceptions instead of returning null, negative caching is not occurring here.

---

## 33. Negative Caching

Negative caching means caching "not found" results.

Potential benefit:

```text
reduces repeated DB lookups for missing IDs
```

Risk:

```text
product created later
cached not-found result remains stale
```

Do not implement it today unless intentional.

---

## 34. Product Gateway Route

Add:

```text
/api/products/**
```

Gateway local target:

```text
http://localhost:8083
```

Docker target:

```text
http://product-service:8083
```

Use the typed configuration pattern from Day 08.

---

## 35. Docker Compose Product Service

```yaml
product-service:
  build:
    context: ./services/product-service

  environment:
    SPRING_PROFILES_ACTIVE: docker

    PRODUCT_DB_URL:
      jdbc:postgresql://product-db:5432/product_db

    PRODUCT_DB_USERNAME:
      product_user

    PRODUCT_DB_PASSWORD:
      product_password

    REDIS_HOST:
      redis

    REDIS_PORT:
      6379

  depends_on:
    - product-db
    - redis
```

---

## 36. Product Docker Configuration

```yaml
spring:

  datasource:
    url: ${PRODUCT_DB_URL}
    username: ${PRODUCT_DB_USERNAME}
    password: ${PRODUCT_DB_PASSWORD}

  data:
    redis:
      host: ${REDIS_HOST:redis}
      port: ${REDIS_PORT:6379}
```

Local:

```yaml
spring:

  datasource:
    url: ${PRODUCT_DB_URL:jdbc:postgresql://localhost:5435/product_db}
    username: ${PRODUCT_DB_USERNAME:product_user}
    password: ${PRODUCT_DB_PASSWORD:product_password}

  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
```

---

## 37. Controller Tests

Cover:

```text
POST -> 201
GET -> 200
PUT -> 200
PATCH -> 200
DELETE -> 204
missing -> 404
duplicate SKU -> 409
invalid price -> 400
```

---

## 38. Repository Tests

Test:

```text
findBySkuIgnoreCase
findByNameContainingIgnoreCase
unique SKU constraint
```

Caching does not replace persistence tests.

---

## 39. Cache Behavior Test

Plain Mockito unit tests do not automatically apply Spring cache proxies.

Use a Spring-context test for cache interception.

Conceptual test:

```text
call getProduct(id)
call getProduct(id) again
verify repository.findById(id) called once
```

This proves the second call was cached.

---

## 40. Cache Update Test

Flow:

```text
GET -> cache old product
PUT -> update DB and cache
GET -> new value returned
```

Verify stale old value is not returned.

---

## 41. Cache Evict Test

Flow:

```text
GET -> cached
DELETE -> DB delete + cache evict
GET -> 404
```

---

## 42. Cache Metrics to Know

In production, watch:

```text
cache hit ratio
cache miss ratio
eviction count
Redis memory
Redis latency
error rate
```

Hit ratio:

```text
hits / (hits + misses)
```

---

## 43. Cache Stampede

Occurs when a popular key expires and many requests miss simultaneously.

Then:

```text
many requests -> DB at once
```

Possible mitigations:

```text
locking
request coalescing
TTL jitter
pre-warming
refresh-ahead
```

Know the concept; no need to implement today.

---

## 44. Cache Penetration

Repeated requests for nonexistent keys can bypass the cache repeatedly and hit DB.

Possible mitigations:

```text
negative caching
rate limiting
input validation
```

---

## 45. Cache Avalanche

Many cache entries expire together.

Then many requests hit DB simultaneously.

Mitigations:

```text
TTL jitter
staggered expiry
pre-warming
```

---

## 46. Common Mistakes

### Cache everything

Only cache when:

```text
read frequency is high
data is expensive to retrieve
stale data is tolerable
```

### No invalidation

Writes must update/evict related keys.

### Very long TTL

Price/inventory may become stale.

### Cache as source of truth

PostgreSQL is authoritative.

### Poor key naming

Prefer:

```text
products::<id>
```

not ambiguous keys like:

```text
123
```

---

## 47. Manual Test Checklist

### Cache Miss / Hit

1. Start Redis.
2. Create product.
3. GET once.
4. Observe SQL.
5. GET again.
6. Confirm no second product SELECT.
7. Inspect Redis key.

### Update

1. GET to cache.
2. PUT new price.
3. GET again.
4. Confirm new value.

### Delete

1. GET to cache.
2. DELETE.
3. GET again.
4. Expect 404.

### TTL

1. Cache product.
2. Check TTL.
3. Let it expire or temporarily shorten TTL.
4. GET again.
5. Confirm DB access.

### Stale Data

1. GET to cache.
2. Change DB directly.
3. GET and see stale value.
4. Delete cache key.
5. GET and see fresh value.

### Redis Down

```bash
docker compose stop redis
```

Observe Product read behavior, then:

```bash
docker compose start redis
```

---

## 48. Interview Questions

### Redis

1. What is Redis?
2. Why is Redis fast?
3. What is cache-aside?
4. What is a cache hit?
5. What is a cache miss?
6. What is TTL?
7. Why does stale data occur?
8. Why is invalidation difficult?
9. What is cache stampede?
10. What is cache penetration?
11. What is cache avalanche?
12. Why is Redis not source of truth here?

### Spring Cache

13. What does `@EnableCaching` do?
14. How does `@Cacheable` work?
15. When is the method skipped?
16. What does `@CachePut` do?
17. `@Cacheable` vs `@CachePut`?
18. What does `@CacheEvict` do?
19. What is a cache key?
20. What do `condition` and `unless` do?
21. Why can self-invocation bypass caching?
22. What should happen if Redis is down?

### Architecture

23. Why is Product a separate service?
24. Why is Product a good cache candidate?
25. Why not cache payment authorization similarly?
26. Why keep PostgreSQL authoritative?
27. How would you handle highly volatile inventory?
28. How would you monitor cache effectiveness?

---

## 49. Coding Checklist

```text
[ ] product-service created
[ ] product_db added
[ ] ProductEntity created
[ ] ProductRepository created
[ ] ProductService created
[ ] ProductController created
[ ] validation added
[ ] product exceptions added
[ ] Redis container added
[ ] local Redis config added
[ ] Docker Redis config added
[ ] @EnableCaching enabled
[ ] @Cacheable added
[ ] @CachePut added
[ ] @CacheEvict added
[ ] TTL configured
[ ] Redis key inspected
[ ] cache miss observed
[ ] cache hit observed
[ ] stale-data experiment completed
[ ] Redis failure drill completed
[ ] Gateway route added
[ ] Product Dockerfile added
[ ] Compose updated
[ ] repository tests pass
[ ] controller tests pass
[ ] cache test passes
[ ] docs/day-09.md updated
[ ] Git commit completed
```

---

## 50. Scorecard

```text
Product CRUD works                         1/1
Product DB works                           1/1
Redis integrated                           1/1
@Cacheable works                           1/1
@CachePut/@CacheEvict works                1/1
TTL demonstrated                           1/1
Stale cache understood                     1/1
Redis failure drill completed              1/1
Tests/interview concepts complete          1/1
Docs + commit complete                     1/1

TOTAL                                     /10
```

Target: **8/10 minimum, 10/10 preferred.**

---

## 51. Recommended Day Notes

Create:

```text
docs/day-09.md
```

Record:

```text
Product responsibility:
...

Product DB:
...

Redis host local:
...

Redis host Docker:
...

Cache name:
products

Cache key:
...

TTL:
...

First GET SQL:
...

Second GET SQL:
...

Stale-data result:
...

Redis-down result:
...

Self-invocation explanation:
...

Still unclear:
...
```

---

## 52. Topics Deliberately Deferred

Do not go deep yet into:

```text
Redis Cluster
Redis Sentinel
distributed locks
Lua scripts
Pub/Sub
Redis Streams
write-through cache
refresh-ahead
near cache
Hibernate second-level cache
```

Day 09 is focused on:

```text
Product Service
Redis basics
Spring Cache
cache-aside
TTL
invalidation
failure behavior
```

---

# Day 09 Definition of Done

Day 09 is complete when this works:

```text
GET /api/products/{id}
        |
        v
API Gateway
        |
        v
Product Service
        |
        v
Spring Cache Interceptor
        |
        +--> Redis hit -> return
        |
        +--> Redis miss
                |
                v
             PostgreSQL
                |
                v
             cache result
                |
                v
              return
```

You must demonstrate:

```text
first GET -> DB
second GET -> Redis
update -> cache refreshed or invalidated
delete -> cache removed
TTL -> key expires
stale data -> reproduced and corrected
Redis down -> behavior observed and explained
```

You should be able to explain without notes:

```text
cache-aside
@Cacheable
@CachePut
@CacheEvict
TTL
stale data
cache invalidation
self-invocation
cache stampede
Redis failure strategy
why PostgreSQL remains source of truth
```

If you can prove the second read avoids PostgreSQL, deliberately create stale cached data, correct it, and explain what happens when Redis is unavailable, Day 09 is complete.



## Redis Docker commands

Find the container:

```bash
docker ps --filter "name=redis"
```

Open an interactive `redis-cli` shell inside the container:

```bash
docker exec -it redis redis-cli
```

Inside `redis-cli`:

```text
KEYS *                                    # list all cached keys (fine for dev; avoid in prod)
SCAN 0                                    # safer, non-blocking alternative to KEYS
GET "products::<productId>"               # view the cached JSON value
TTL "products::<productId>"               # seconds until expiry; -1 = never expires, -2 = key doesn't exist
TYPE "products::<productId>"
DEL "products::<productId>"               # manually evict a stale entry
FLUSHALL                                  # clear the entire cache (careful)
```

One-shot equivalents (no need to enter the shell):

```bash
docker exec redis redis-cli KEYS '*'
docker exec redis redis-cli GET 'products::<productId>'
docker exec redis redis-cli TTL 'products::<productId>'
docker exec redis redis-cli DEL 'products::<productId>'
docker exec redis redis-cli FLUSHALL
```

