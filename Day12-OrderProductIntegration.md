# Day 12 — Order Service and Service-to-Service REST Communication

## Recommended Git Branch

`day-12-order-service-rest-client`

## Objective

Day 12 introduces your first business workflow that spans multiple microservices.

By the end of the day, you should have:

- `order-service`
- `order_db`
- `OrderEntity`
- `OrderItemEntity`
- service-to-service REST communication with Product Service
- a dedicated `ProductClient`
- trusted identity propagation
- downstream 404/5xx/timeout handling
- short transaction boundaries
- Gateway routing for `/api/orders/**`
- unit, repository, controller, and integration tests

The key architecture principle is:

> Order Service may depend on Product Service's API contract, but it must not depend on Product Service's database or JPA entities.

---

## 1. Target Architecture

```text
Client
  |
  v
API Gateway :8080
  |
  v
Authorizer :9001
  |
  v
Order Service :8084
  |
  +--> order_db
  |
  +--> Product Service :8083
           |
           +--> Redis
           +--> product_db
```

Order creation:

```text
POST /api/orders
        |
        v
Gateway
        |
        v
Authorizer
        |
        v
Order Controller
        |
        v
Order Service
        |
        +--> Product Service
        |      |
        |      +--> validate product exists
        |      +--> get current price
        |
        +--> persist Order + Items
        |
        v
order_db
```

---

## 2. Two-Hour Agenda

```text
00–10 min   Verify Day 11 security stack
10–20 min   Define Order bounded context/API
20–35 min   Create Order Service + order_db
35–50 min   Implement Order/OrderItem entities
50–65 min   Build ProductClient with RestClient
65–80 min   Implement create-order workflow
80–95 min   Handle 404/5xx/timeouts
95–105 min  Review transaction boundaries
105–115 min Add tests
115–120 min Docker/Gateway verification + interview review
```

---

## 3. Order Service Responsibility

Order Service owns:

```text
Order
Order Item
Order status
Order total
Customer/order association
Order creation timestamp
```

It does not own:

```text
Product catalog
Product current price source of truth
Customer profile
Payment details
Favorites
```

Order Service may store a snapshot of Product data such as:

```text
productId
productName
unitPrice
```

because historical orders should not change when Product data changes later.

---

## 4. Create `order-service`

Suggested package:

```text
com.customer.order
```

Suggested port:

```text
8084
```

Dependencies:

```text
Spring Web
Validation
Spring Data JPA
PostgreSQL Driver
Actuator
Spring Security
Spring Boot Test
```

---

## 5. Add Order Database

Docker Compose:

```yaml
order-db:
  image: postgres:17

  environment:
    POSTGRES_DB: order_db
    POSTGRES_USER: order_user
    POSTGRES_PASSWORD: order_password

  ports:
    - "5436:5432"

  volumes:
    - order-db-data:/var/lib/postgresql/data
```

Local app connection:

```text
jdbc:postgresql://localhost:5436/order_db
```

Docker connection:

```text
jdbc:postgresql://order-db:5432/order_db
```

---

## 6. Order Status

```java
public enum OrderStatus {
    CREATED,
    CONFIRMED,
    CANCELLED,
    FAILED
}
```

For Day 12, new orders start as:

```text
CREATED
```

---

## 7. Order Entity

```java
@Entity
@Table(name = "customer_order")
public class OrderEntity {

    @Id
    private UUID orderId;

    @Column(nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(
        nullable = false,
        precision = 14,
        scale = 2
    )
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(
        mappedBy = "order",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private List<OrderItemEntity> items =
            new ArrayList<>();

    protected OrderEntity() {
    }

    // constructors/getters/setters
}
```

---

## 8. Order Item Entity

```java
@Entity
@Table(name = "order_item")
public class OrderItemEntity {

    @Id
    @GeneratedValue(
        strategy = GenerationType.IDENTITY
    )
    private Long orderItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "order_id",
        nullable = false
    )
    private OrderEntity order;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private String productName;

    @Column(
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Column(
        nullable = false,
        precision = 14,
        scale = 2
    )
    private BigDecimal lineTotal;

    protected OrderItemEntity() {
    }

    // constructors/getters/setters
}
```

---

## 9. Why Store Product Name and Price?

Suppose today:

```text
Keyboard = ₹2500
```

and next month:

```text
Keyboard Pro = ₹3200
```

Historical orders must still show the value at purchase time.

So Order Service stores a snapshot.

This is intentional denormalization for business history.

---

## 10. Relationship Helper

```java
public void addItem(
        OrderItemEntity item) {

    items.add(item);
    item.setOrder(this);
}
```

Keep both sides of the bidirectional relationship in sync.

---

## 11. Create Order Request

```java
public record CreateOrderRequest(

        @NotNull
        UUID customerId,

        @NotEmpty
        List<OrderItemRequest> items
) {
}
```

Item:

```java
public record OrderItemRequest(

        @NotNull
        UUID productId,

        @NotNull
        @Positive
        Integer quantity
) {
}
```

Do not accept authoritative:

```text
price
productName
```

from the client.

---

## 12. Why Client Price Must Not Be Trusted

Bad:

```json
{
  "productId":"...",
  "price":1
}
```

A malicious caller could manipulate price.

Correct flow:

```text
Client sends productId + quantity
Order Service asks Product Service for price
```

---

## 13. Order Response

```java
public record OrderResponse(
        UUID orderId,
        UUID customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        List<OrderItemResponse> items,
        Instant createdAt
) {
}
```

---

## 14. Product Contract Inside Order Service

Create a local DTO:

```java
public record ProductResponse(
        UUID productId,
        String sku,
        String name,
        BigDecimal price,
        InventoryStatus inventoryStatus
) {
}
```

Do not import Product Service's JPA entity.

Do not depend on Product Service's internal model.

---

## 15. Why Not Share Product Entity/JAR?

Avoid:

```java
import com.customer.product.entity.ProductEntity;
```

because it creates:

```text
persistence coupling
deployment coupling
version coupling
ownership ambiguity
```

Order Service should know the Product API contract only.

---

## 16. Create `ProductClient`

```java
public interface ProductClient {

    ProductResponse getProduct(
            UUID productId,
            RequestIdentity identity
    );
}
```

Purpose:

```text
hide HTTP mechanics
centralize error handling
centralize timeouts
improve testability
prepare for resilience later
```

---

## 17. Product Service Configuration

Local:

```yaml
app:
  services:
    product:
      base-url: http://localhost:8083
      connect-timeout: 2s
      read-timeout: 3s
```

Docker:

```yaml
app:
  services:
    product:
      base-url: http://product-service:8083
      connect-timeout: 2s
      read-timeout: 3s
```

---

## 18. Typed Configuration

```java
@ConfigurationProperties(
    prefix = "app.services.product"
)
public record ProductServiceProperties(
        URI baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
}
```

Reuse Day 08 configuration principles.

---

## 19. Configure `RestClient`

```java
@Configuration
public class RestClientConfiguration {

    @Bean
    RestClient productRestClient(
            RestClient.Builder builder,
            ProductServiceProperties properties) {

        return builder
                .baseUrl(
                    properties
                        .baseUrl()
                        .toString()
                )
                .build();
    }
}
```

---

## 20. ProductClient Implementation

```java
@Component
public class HttpProductClient
        implements ProductClient {

    private final RestClient restClient;

    public HttpProductClient(
            RestClient productRestClient) {

        this.restClient =
            productRestClient;
    }

    @Override
    public ProductResponse getProduct(
            UUID productId,
            RequestIdentity identity) {

        return restClient
                .get()

                .uri(
                    "/products/{productId}",
                    productId
                )

                .header(
                    "X-Authenticated-User",
                    identity.subject()
                )

                .header(
                    "X-Authenticated-Scopes",
                    String.join(
                        ",",
                        identity.scopes()
                    )
                )

                .retrieve()

                .body(
                    ProductResponse.class
                );
    }
}
```

---

## 21. Trusted Identity Propagation

Current security path:

```text
Client
  |
  v
Gateway
  |
  v
Authorizer
  |
  v
Gateway adds trusted identity headers
  |
  v
Order Service
```

Order Service may propagate trusted identity to Product Service.

Example:

```text
X-Authenticated-User
X-Authenticated-Scopes
```

This is only safe if downstream services are not directly exposed to untrusted callers.

---

## 22. Create `RequestIdentity`

```java
public record RequestIdentity(
        String subject,
        Set<String> scopes,
        Set<String> roles
) {
}
```

Use this abstraction instead of passing raw header strings throughout the codebase.

---

## 23. Order Controller

```java
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(
            OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(
        HttpStatus.CREATED
    )
    public OrderResponse createOrder(

            @Valid
            @RequestBody
            CreateOrderRequest request,

            @RequestHeader(
                "X-Authenticated-User"
            )
            String user,

            @RequestHeader(
                value = "X-Authenticated-Scopes",
                required = false
            )
            String scopes) {

        RequestIdentity identity =
                RequestIdentityFactory
                    .from(
                        user,
                        scopes
                    );

        return orderService
                .createOrder(
                    request,
                    identity
                );
    }
}
```

Later you can move header extraction to a filter/interceptor/resolver.

---

## 24. Create-Order Workflow

```text
for each requested item
    |
    v
call Product Service
    |
    v
verify product exists
    |
    v
verify availability
    |
    v
copy price/name snapshot
    |
    v
calculate line total
    |
    v
build Order
    |
    v
persist
```

---

## 25. Order Service Example

```java
@Service
public class OrderService {

    private final ProductClient productClient;
    private final OrderPersistenceService persistenceService;

    public OrderService(
            ProductClient productClient,
            OrderPersistenceService persistenceService) {

        this.productClient =
            productClient;

        this.persistenceService =
            persistenceService;
    }

    public OrderResponse createOrder(
            CreateOrderRequest request,
            RequestIdentity identity) {

        ValidatedOrderDraft draft =
                buildOrderDraft(
                    request,
                    identity
                );

        return persistenceService
                .persistOrder(
                    draft
                );
    }
}
```

---

## 26. Build Validated Order Draft

Pseudo-code:

```java
private ValidatedOrderDraft buildOrderDraft(
        CreateOrderRequest request,
        RequestIdentity identity) {

    List<ValidatedOrderItem> items =
            new ArrayList<>();

    BigDecimal total =
            BigDecimal.ZERO;

    for (
        OrderItemRequest requested :
        request.items()
    ) {

        ProductResponse product =
                productClient
                    .getProduct(
                        requested.productId(),
                        identity
                    );

        if (
            product.inventoryStatus()
                == InventoryStatus.OUT_OF_STOCK
        ) {
            throw new ProductUnavailableException(
                product.productId()
            );
        }

        BigDecimal lineTotal =
                product.price()
                    .multiply(
                        BigDecimal.valueOf(
                            requested.quantity()
                        )
                    );

        items.add(
            new ValidatedOrderItem(
                product.productId(),
                product.name(),
                product.price(),
                requested.quantity(),
                lineTotal
            )
        );

        total =
            total.add(
                lineTotal
            );
    }

    return new ValidatedOrderDraft(
        UUID.randomUUID(),
        request.customerId(),
        items,
        total
    );
}
```

---

## 27. Keep Remote Calls Outside DB Transaction

Avoid:

```java
@Transactional
public OrderResponse createOrder(...) {
    productClient.getProduct(...);
    productClient.getProduct(...);
    repository.save(...);
}
```

Why?

Because the database transaction remains open while waiting on network calls.

This can cause:

```text
connection pool pressure
long-held transactions
lock contention
poor scalability
```

---

## 28. Better Transaction Pattern

```text
1. Call Product Service
2. Validate/order-calculate in memory
3. Start short transaction
4. Persist Order + Items
5. Commit
```

---

## 29. Separate Persistence Bean

```java
@Service
public class OrderPersistenceService {

    private final OrderRepository repository;

    public OrderPersistenceService(
            OrderRepository repository) {

        this.repository = repository;
    }

    @Transactional
    public OrderResponse persistOrder(
            ValidatedOrderDraft draft) {

        OrderEntity order =
                mapDraftToEntity(
                    draft
                );

        OrderEntity saved =
                repository.save(order);

        return toResponse(saved);
    }
}
```

This also avoids self-invocation problems with `@Transactional`.

---

## 30. Product 404 Handling

If Product Service returns:

```text
404
```

translate it to an Order-domain exception.

Example:

```java
public class ProductNotAvailableForOrderException
        extends RuntimeException {
}
```

Do not leak raw HTTP client exceptions to controllers.

---

## 31. `RestClient` 404 Mapping

```java
return restClient
        .get()
        .uri(
            "/products/{productId}",
            productId
        )
        .retrieve()

        .onStatus(
            status ->
                status.value() == 404,

            (request, response) -> {
                throw new ProductNotAvailableForOrderException(
                    productId
                );
            }
        )

        .body(
            ProductResponse.class
        );
```

---

## 32. Downstream 5xx Handling

Create:

```java
public class ProductServiceUnavailableException
        extends RuntimeException {
}
```

Map Product:

```text
500
502
503
504
```

to an Order dependency error.

Order Service should not pretend a 503 means:

```text
product does not exist
```

---

## 33. 404 vs 503

```text
404
    product does not exist

503
    Product Service unavailable
```

They are different failures and should stay distinct.

---

## 34. Timeouts

Every service-to-service call needs bounded waiting.

Configure:

```text
connect timeout
read/response timeout
```

Suggested learning values:

```text
connect: 2s
read: 3s
```

The exact values are not universal production defaults.

---

## 35. Failure Drill — Product Service Down

```bash
docker compose stop product-service
```

Call:

```http
POST /api/orders
```

Expected:

```text
Order not persisted
dependency failure returned
```

Restart:

```bash
docker compose start product-service
```

---

## 36. Failure Drill — Product DB Down

```bash
docker compose stop product-db
```

Product Service may return 5xx.

Order Service should translate this into a dependency error.

It must not interpret it as a missing product.

---

## 37. Failure Drill — Slow Product Service

Temporarily add a delay in Product Service or use an HTTP stub.

Verify the ProductClient times out.

The Order request should fail in bounded time.

---

## 38. Multiple Product Calls

For 10 order items, the naive version may perform:

```text
10 Product HTTP calls
```

This is acceptable for Day 12.

But it introduces the distributed equivalent of N+1.

Future improvement:

```http
POST /products/batch
```

or another batch endpoint.

---

## 39. Distributed N+1

Database N+1:

```text
1 query + N child queries
```

Distributed N+1:

```text
1 incoming request + N downstream calls
```

Same performance intuition, different boundary.

---

## 40. Product Redis Cache Helps

Product Service already has Redis.

Order Service does not need to know this.

It simply calls:

```text
Product API
```

The Product Service decides whether the response comes from:

```text
Redis
or
PostgreSQL
```

That is encapsulation.

---

## 41. Gateway Route

Add:

```text
/api/orders/**
```

Local target:

```text
http://localhost:8084
```

Docker target:

```text
http://order-service:8084
```

If using `StripPrefix=1`:

```text
/api/orders
    ->
/orders
```

---

## 42. Order Docker Configuration

```yaml
spring:

  datasource:
    url:
      ${ORDER_DB_URL:jdbc:postgresql://order-db:5432/order_db}

    username:
      ${ORDER_DB_USERNAME:order_user}

    password:
      ${ORDER_DB_PASSWORD:order_password}

app:

  services:

    product:

      base-url:
        ${PRODUCT_SERVICE_URL:http://product-service:8083}

      connect-timeout:
        ${PRODUCT_CONNECT_TIMEOUT:2s}

      read-timeout:
        ${PRODUCT_READ_TIMEOUT:3s}
```

---

## 43. Docker Compose — Order Service

```yaml
order-service:

  build:
    context: ./services/order-service

  environment:
    SPRING_PROFILES_ACTIVE: docker

    ORDER_DB_URL:
      jdbc:postgresql://order-db:5432/order_db

    ORDER_DB_USERNAME:
      order_user

    ORDER_DB_PASSWORD:
      order_password

    PRODUCT_SERVICE_URL:
      http://product-service:8083

  depends_on:
    - order-db
    - product-service
```

---

## 44. Order Repository

```java
public interface OrderRepository
        extends JpaRepository<OrderEntity, UUID> {

    List<OrderEntity>
    findByCustomerIdOrderByCreatedAtDesc(
            UUID customerId
    );
}
```

---

## 45. GET Order Endpoints

Implement:

```http
GET /orders/{orderId}
```

Optional:

```http
GET /orders?customerId={customerId}
```

These should read Order Service's own historical data.

They should not call Product Service again just to reconstruct old prices/names.

---

## 46. Order Snapshot Principle

Once an order exists:

```text
do not rebuild historical order details
from current Product state
```

Use the stored snapshot.

---

## 47. Exception Mapping

Suggested API mapping:

```text
ProductNotAvailableForOrderException
    -> 400 or 422

ProductServiceUnavailableException
    -> 503

OrderNotFoundException
    -> 404

validation failure
    -> 400
```

Keep your existing `ApiError` structure consistent.

---

## 48. OrderService Unit Tests

Mock:

```text
ProductClient
OrderPersistenceService
```

Test:

```text
line total
order total
out-of-stock rejection
product snapshot copying
multiple items
```

No HTTP should be required in these tests.

---

## 49. ProductClient Tests

Test contract behavior:

```text
200 -> ProductResponse

404 ->
ProductNotAvailableForOrderException

5xx ->
ProductServiceUnavailableException

timeout ->
dependency exception
```

An HTTP stub server is usually easier than deeply mocking the fluent `RestClient` chain.

---

## 50. Repository Tests

Use:

```java
@DataJpaTest
```

Test:

```text
Order saved
OrderItems cascade saved
customer query works
relationship mapping works
```

---

## 51. Controller Tests

Test:

```text
POST /orders -> 201
invalid quantity -> 400
Product dependency unavailable -> 503
GET missing order -> 404
trusted identity missing -> expected security/error behavior
```

---

## 52. End-to-End Happy Path

1. Start full stack.
2. Obtain access token from your custom OIDC IdP.
3. Create/identify an existing Product.
4. Call:

```bash
curl -i   -X POST   http://localhost:8080/api/orders   -H "Authorization: Bearer $TOKEN"   -H "Content-Type: application/json"   -d '{
    "customerId":"<customer-id>",
    "items":[
      {
        "productId":"<product-id>",
        "quantity":2
      }
    ]
  }'
```

Expected flow:

```text
Gateway
-> Authorizer
-> Order Service
-> Product Service
-> order_db
```

Expected status:

```text
201 Created
```

---

## 53. Example Response

```json
{
  "orderId": "...",
  "customerId": "...",
  "status": "CREATED",
  "totalAmount": 5000.00,
  "items": [
    {
      "productId": "...",
      "productName": "Keyboard",
      "unitPrice": 2500.00,
      "quantity": 2,
      "lineTotal": 5000.00
    }
  ],
  "createdAt": "..."
}
```

---

## 54. Verify PostgreSQL

```bash
docker exec -it order-db   psql   -U order_user   -d order_db
```

Then:

```sql
SELECT * FROM customer_order;
SELECT * FROM order_item;
```

Verify:

```text
order exists
items exist
price snapshot matches Product response
```

---

## 55. Security Flow End-to-End

```text
Client
  |
  | Bearer JWT
  v
Gateway
  |
  v
Authorizer
  |
  | ALLOW
  v
Gateway
  |
  | trusted headers
  v
Order Service
  |
  | trusted internal identity
  v
Product Service
```

Document the trust boundary clearly.

If Product Service trusts internal headers, it should not be publicly reachable.

---

## 56. Should Order Forward Original JWT?

Two options:

### Current centralized-authorizer model

```text
Order forwards trusted identity headers
```

### Defense-in-depth model

```text
Order forwards original JWT
Product validates again
```

For continuity with your existing design, use trusted headers for Day 12.

Document the trade-off.

---

## 57. Common Mistakes

### Sharing Product JPA Entity

Wrong:

```java
import com.customer.product.entity.ProductEntity;
```

### Calling Product DB directly

Wrong:

```text
Order Service -> product_db
```

### Trusting client-supplied price

Always obtain authoritative pricing from Product Service or a trusted pricing source.

### Remote call inside long DB transaction

Keep network work outside the persistence transaction.

### No timeout

Every remote dependency needs bounded waiting.

### Treating 404 and 503 the same

They represent different failures.

### Re-querying Product on historical order GET

Use stored snapshot.

### Leaking low-level HTTP exceptions

Translate infrastructure failures into application/domain exceptions.

---

## 58. Interview Questions — Service-to-Service REST

1. Why does Order Service call Product Service?
2. Why not query Product DB directly?
3. Why should JPA entities not cross service boundaries?
4. Why use `ProductClient` abstraction?
5. What is `RestClient`?
6. RestClient vs RestTemplate?
7. When would you prefer WebClient?
8. What should happen on downstream 404?
9. What should happen on downstream 5xx?
10. Why are timeouts mandatory?
11. What is distributed N+1?
12. When might a batch endpoint help?

---

## 59. Interview Questions — Transactions

13. Why is a remote HTTP call inside a DB transaction risky?
14. What happens to DB connection usage?
15. How do you shorten the transaction boundary?
16. Why use another Spring bean for persistence?
17. What self-invocation problem does that avoid?
18. Does Order's DB transaction include Product Service?
19. Can Product be rolled back if Order DB persistence fails?
20. Why does this lead to Saga/Outbox concepts later?

---

## 60. Interview Questions — Data Ownership

21. Why store Product price in OrderItem?
22. Is this data duplication wrong?
23. Why should historical orders not depend on current Product state?
24. Which service owns Product price?
25. Which service owns Order status?
26. What does DB-per-service protect?

---

## 61. Interview Questions — Security

27. How does trusted identity reach Order Service?
28. What did the Authorizer validate?
29. Why can Order forward trusted identity headers?
30. What happens if downstream service is publicly accessible?
31. Would forwarding/revalidating JWT improve defense in depth?

---

## 62. Testing Requirements

Minimum:

```text
OrderService
- calculates line totals
- calculates total amount
- rejects out-of-stock products
- copies product snapshot

ProductClient
- 200 -> success
- 404 -> domain exception
- 5xx -> dependency exception
- timeout -> dependency exception

Repository
- order + items persist
- customer query works

Controller
- valid POST -> 201
- invalid request -> 400
- Product unavailable -> 503
- missing order -> 404
```

End-to-end:

```text
Gateway + Authorizer + Order + Product + order_db
```

---

## 63. Coding Checklist

```text
[ ] order-service created

[ ] order_db added

[ ] OrderStatus created

[ ] OrderEntity created

[ ] OrderItemEntity created

[ ] relationship mapping added

[ ] helper method added

[ ] CreateOrderRequest created

[ ] OrderResponse created

[ ] Product contract DTO created locally

[ ] ProductClient interface created

[ ] RestClient config created

[ ] HttpProductClient created

[ ] ProductServiceProperties created

[ ] RequestIdentity created

[ ] Product 404 translated

[ ] Product 5xx translated

[ ] timeout configured

[ ] order total calculation implemented

[ ] snapshot persisted

[ ] transaction kept short

[ ] Gateway order route added

[ ] Docker Compose updated

[ ] repository tests pass

[ ] service tests pass

[ ] ProductClient tests pass

[ ] controller tests pass

[ ] Product-down drill completed

[ ] end-to-end order creation works

[ ] docs/day-12.md updated

[ ] Git commit completed
```

---

## 64. Scorecard

```text
Order Service core flow works                 1/1
Order DB works                                1/1
ProductClient works                           1/1
Product snapshot correct                      1/1
404/5xx handling correct                      1/1
Timeout demonstrated                          1/1
Transaction boundary correct                  1/1
Identity propagation works                    1/1
Tests/end-to-end pass                         1/1
Docs + interview recap + commit               1/1

TOTAL                                        /10
```

Target:

```text
8/10 minimum
10/10 preferred
```

---

## 65. Recommended Day Notes

Create:

```text
docs/day-12.md
```

Record:

```text
Order responsibility:
...

Order DB:
...

Product URL local:
...

Product URL docker:
...

ProductClient contract:
...

Timeout values:
...

404 behavior:
...

5xx behavior:
...

Transaction boundary:
...

Trusted identity propagation:
...

End-to-end result:
...

Still unclear:
...
```

---

## 66. Topics Deliberately Deferred

Do not go deep into:

```text
Resilience4j
retry policies
circuit breakers
bulkheads
Kafka
Saga
Outbox
Payment orchestration
Inventory reservation
distributed transaction
compensation
```

These come later.

Day 12 is focused on:

```text
Order Service
service-to-service REST
RestClient
contracts
timeouts
downstream error handling
transaction boundaries
identity propagation
```

---

# Day 12 Definition of Done

Day 12 is complete when you can demonstrate:

```text
Client
  |
  | Bearer JWT
  v
Gateway
  |
  v
Authorizer
  |
  | ALLOW
  v
Gateway
  |
  | trusted identity
  v
Order Service
  |
  +--> ProductClient
  |       |
  |       v
  |    Product Service
  |       |
  |       +--> Redis / product_db
  |
  +--> build order snapshot
  |
  +--> short DB transaction
  |
  v
order_db
```

You must demonstrate:

```text
valid product -> order created
missing product -> rejected
Product 5xx -> dependency error
Product timeout -> bounded failure
Product down -> no partial order persisted
```

You should be able to explain without notes:

```text
why Order calls Product API instead of Product DB
why Order stores Product price snapshot
why client price is untrusted
why remote calls should not live inside long DB transactions
why timeouts are mandatory
why 404 and 503 are different
why JPA entities should not cross service boundaries
how trusted identity reaches Product through Order
```

If you can create an order end-to-end through Gateway, prove Product Service supplies authoritative Product data, prove no order persists when Product validation fails, and clearly explain the transaction boundary, Day 12 is complete.
