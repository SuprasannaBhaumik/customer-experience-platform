# Day 13 — Resilience4j, Timeouts, Retries, Circuit Breakers, and Failure Handling

## Recommended Git Branch

`day-13-resilience4j`

## Objective

Day 13 makes the Day 12 `Order Service -> Product Service` call resilient.

You will protect the Product dependency against:

- transient 5xx failures
- connection failures
- slow responses
- prolonged outages
- recovery after outage

using:

- HTTP timeouts
- Resilience4j Retry
- Circuit Breaker
- backoff
- jitter
- failure classification
- safe fallback behavior

Target:

```text
Order Service
    |
    v
ProductClient
    |
    +--> timeout
    +--> retry
    +--> circuit breaker
    |
    v
Product Service
```


---

## 1. Two-Hour Agenda

```text
00–10 min   Verify Day 12 happy path
10–20 min   Classify dependency failures
20–35 min   Add Resilience4j dependencies/config
35–50 min   Add Retry
50–65 min   Add Circuit Breaker
65–80 min   Verify HTTP timeouts
80–95 min   Run failure drills
95–105 min  Tune backoff/jitter/thresholds
105–115 min Add tests
115–120 min Interview recap + scorecard
```


---

## 2. Failure Classification

### Transient

```text
503 once, then success
```

Retry may help.

### Persistent

```text
Product Service down for minutes
```

Retrying every request can make the outage worse.

### Slow dependency

```text
HTTP 200 after 15 seconds
```

Requires timeout protection.

### Business/client error

```text
404 Product Not Found
400 invalid input
401/403 security failure
```

Retry normally does not help.

The most important Day 13 skill is to classify failures before deciding whether to retry them.


---

## 3. Add Resilience4j

For Spring Boot 3:

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
```

If not already available:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

You already use Actuator in the project; keep it because it helps observe resilience state and metrics.


---

## 4. Correct Resilience Boundary

Put resilience around the remote HTTP boundary.

Good:

```text
OrderService
    |
    v
ProductClient
    |
    +--> Retry / Circuit Breaker
    |
    v
HTTP Product Service
```

Do not scatter resilience logic through controllers or repositories.

`HttpProductClient` is the correct place because it owns the downstream call.


---

## 5. Baseline ProductClient

From Day 12:

```java
@Component
public class HttpProductClient
        implements ProductClient {

    private final RestClient restClient;

    public HttpProductClient(
            RestClient productRestClient) {
        this.restClient = productRestClient;
    }

    @Override
    public ProductResponse getProduct(
            UUID productId,
            RequestIdentity identity) {

        return restClient
                .get()
                .uri("/products/{id}", productId)
                .header(
                    "X-Authenticated-User",
                    identity.subject()
                )
                .retrieve()
                .body(ProductResponse.class);
    }
}
```

Make sure the Day 12 happy path works before adding resilience.


---

## 6. Retry

Retry means:

```text
attempt fails
    |
    v
try again
```

Useful for:

```text
temporary 500/502/503/504
connection reset
short network interruption
```

Usually do not retry:

```text
400
401
403
404
422
```


---

## 7. Add `@Retry`

```java
@Retry(
    name = "productService"
)
@Override
public ProductResponse getProduct(
        UUID productId,
        RequestIdentity identity) {

    return executeCall(
        productId,
        identity
    );
}
```

The name:

```text
productService
```

must match the configuration instance.


---

## 8. Retry Configuration

Example:

```yaml
resilience4j:

  retry:

    instances:

      productService:

        max-attempts: 3

        wait-duration: 500ms

        retry-exceptions:
          - java.net.ConnectException
          - java.net.SocketTimeoutException
          - org.springframework.web.client.HttpServerErrorException

        ignore-exceptions:
          - com.customer.order.exception.ProductNotAvailableForOrderException
```

Important:

```text
max-attempts: 3
```

means:

```text
attempt 1
attempt 2
attempt 3
```

not three retries after the first call.


---

## 9. Backoff and Jitter

Fixed retry:

```text
500ms
500ms
500ms
```

can cause synchronized retry storms.

Exponential backoff:

```text
500ms
1s
2s
```

Jitter adds randomness:

```text
420ms
1.1s
1.8s
```

This helps prevent many callers from retrying at the exact same instant.

Know both concepts even if your initial Day 13 config remains simple.


---

## 10. Retry Storm

If Product fails and:

```text
1000 requests/sec
```

each request makes:

```text
3 attempts
```

you may create:

```text
3000 downstream attempts/sec
```

during an outage.

Therefore retries need:

```text
small attempt count
timeouts
backoff
jitter
circuit breaker
```


---

## 11. Circuit Breaker

A circuit breaker prevents repeated calls to a failing dependency.

States:

```text
CLOSED
OPEN
HALF_OPEN
```

### CLOSED

Calls are allowed and outcomes recorded.

### OPEN

Calls are rejected immediately.

### HALF_OPEN

A limited number of probe calls test whether the dependency has recovered.


---

## 12. Circuit State Transitions

```text
CLOSED
   |
   | failure rate too high
   v
OPEN
   |
   | wait duration elapsed
   v
HALF_OPEN
   |
   +--> probes succeed -> CLOSED
   |
   +--> probes fail -> OPEN
```

This is one of the most important Day 13 interview topics.


---

## 13. Add `@CircuitBreaker`

```java
@CircuitBreaker(
    name = "productService",
    fallbackMethod = "productFallback"
)
@Override
public ProductResponse getProduct(
        UUID productId,
        RequestIdentity identity) {

    return executeCall(
        productId,
        identity
    );
}
```


---

## 14. Combine Retry + Circuit Breaker

```java
@Retry(
    name = "productService"
)
@CircuitBreaker(
    name = "productService",
    fallbackMethod = "productFallback"
)
public ProductResponse getProduct(
        UUID productId,
        RequestIdentity identity) {

    return executeCall(
        productId,
        identity
    );
}
```

The conceptual goal is:

```text
small number of retries for transient failures
+
stop calling when dependency remains unhealthy
```

Interceptor/annotation ordering can affect exact runtime behavior, so inspect logs and metrics rather than assuming.


---

## 15. Circuit Breaker Configuration

```yaml
resilience4j:

  circuitbreaker:

    instances:

      productService:

        sliding-window-type: COUNT_BASED

        sliding-window-size: 10

        minimum-number-of-calls: 5

        failure-rate-threshold: 50

        wait-duration-in-open-state: 10s

        permitted-number-of-calls-in-half-open-state: 3

        automatic-transition-from-open-to-half-open-enabled: true

        slow-call-duration-threshold: 2s

        slow-call-rate-threshold: 50
```


---

## 16. Understand Each Circuit Setting

```text
sliding-window-size: 10
```

Evaluate recent 10 calls.

```text
minimum-number-of-calls: 5
```

Do not calculate health before enough calls exist.

```text
failure-rate-threshold: 50
```

Open when at least half of recorded calls fail.

```text
wait-duration-in-open-state: 10s
```

Stay OPEN before trying recovery.

```text
permitted-number-of-calls-in-half-open-state: 3
```

Allow three recovery probes.


---

## 17. Example Transition

Suppose:

```text
Call 1 -> fail
Call 2 -> fail
Call 3 -> success
Call 4 -> fail
Call 5 -> fail
```

Failure rate:

```text
4 / 5 = 80%
```

Threshold:

```text
50%
```

The circuit can transition:

```text
CLOSED -> OPEN
```


---

## 18. Slow Calls

A dependency may be technically successful but operationally unhealthy.

Example:

```text
HTTP 200 after 8 seconds
```

Use:

```yaml
slow-call-duration-threshold: 2s
slow-call-rate-threshold: 50
```

so excessive slow responses can contribute to circuit health.


---

## 19. Timeout

Timeout answers:

```text
How long am I willing to wait for Product?
```

Keep the Day 12 HTTP client timeout configuration:

```yaml
app:

  services:

    product:

      connect-timeout: 2s

      read-timeout: 3s
```

Resilience4j does not replace proper network timeouts.


---

## 20. Why Timeout Must Exist Before Retry

Without timeout:

```text
attempt 1 waits 30s
attempt 2 waits 30s
attempt 3 waits 30s
```

One request can now take:

```text
90+ seconds
```

Rule:

> Never add retry to an unbounded network call.


---

## 21. Fallback

A fallback is alternate behavior after failure.

Safe Day 13 example:

```java
private ProductResponse productFallback(
        UUID productId,
        RequestIdentity identity,
        Throwable throwable) {

    throw new ProductServiceUnavailableException(
        productId,
        throwable
    );
}
```

Fallback does not have to manufacture successful data.


---

## 22. Dangerous Fallback

Never do this for pricing:

```java
return new ProductResponse(
    productId,
    "UNKNOWN",
    "Unknown Product",
    BigDecimal.ZERO,
    InventoryStatus.IN_STOCK
);
```

That could create an Order with a fake price.

For Order creation, correctness is more important than pretending Product Service is available.


---

## 23. Safe vs Unsafe Fallback

Potentially safe:

```text
Recommendation Service down
-> return no recommendations
```

Potentially unsafe:

```text
Pricing unavailable
-> assume zero price
```

Fallback is a business decision, not just a technical annotation.


---

## 24. Suggested ProductClient

```java
@Component
public class HttpProductClient
        implements ProductClient {

    private final RestClient restClient;

    public HttpProductClient(
            RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    @Retry(
        name = "productService"
    )
    @CircuitBreaker(
        name = "productService",
        fallbackMethod = "productFallback"
    )
    public ProductResponse getProduct(
            UUID productId,
            RequestIdentity identity) {

        return restClient
                .get()
                .uri("/products/{id}", productId)
                .header(
                    "X-Authenticated-User",
                    identity.subject()
                )
                .retrieve()
                .onStatus(
                    status -> status.value() == 404,
                    (request, response) -> {
                        throw new ProductNotAvailableForOrderException(
                            productId
                        );
                    }
                )
                .body(ProductResponse.class);
    }

    private ProductResponse productFallback(
            UUID productId,
            RequestIdentity identity,
            Throwable throwable) {

        throw new ProductServiceUnavailableException(
            productId,
            throwable
        );
    }
}
```


---

## 25. Self-Invocation Warning

Same proxy rule as:

```text
@Transactional
@Cacheable
@PreAuthorize
```

If the annotated method is called internally through:

```java
this.getProduct(...)
```

the resilience advice may be bypassed.

Keep:

```text
OrderService
    |
    v
HttpProductClient Spring bean
```

as separate beans.


---

## 26. Exception Classification

Recommended intent:

```text
ProductNotAvailableForOrderException
    non-retryable business failure

ProductServiceUnavailableException
    dependency failure

ProductClientTimeoutException
    dependency failure
```

Do not mark every exception retryable.


---

## 27. 404 Should Not Trip the Circuit

A Product 404 means:

```text
Product Service worked
and correctly reported missing data
```

That is not a dependency-health failure.

Configure it as ignored/non-recorded where needed.

Expected:

```text
one call
no retry
circuit remains healthy
```


---

## 28. 401/403 Should Not Be Retried

A repeated call using the same invalid security context is unlikely to succeed.

Investigate:

```text
trusted header propagation
scope/role mapping
gateway/authorizer configuration
```

instead of retrying.


---

## 29. Retry and Idempotency

GET Product is a good retry candidate because GET should be idempotent.

Be much more careful with:

```text
POST payment
POST order creation
```

because retries may create duplicates.

Day 15 will address this using:

```text
Idempotency-Key
```


---

## 30. Retry Budget

Think about total request latency.

Bad:

```text
3 attempts
x 3s timeout
= up to ~9s plus waits
```

if your total Order API latency budget is only 5s.

Design together:

```text
individual timeout
retry count
retry wait
outer request budget
```


---

## 31. Failure Drill — Healthy Product

Expected:

```text
1 Product call
no retry
circuit CLOSED
Order created
```

Record baseline logs and latency.


---

## 32. Failure Drill — Transient 500

Make Product return:

```text
first call -> 500
second call -> 200
```

Expected:

```text
retry occurs
Order succeeds
circuit remains CLOSED
```

Verify actual call count.


---

## 33. Failure Drill — Product Always 500

Expected:

```text
retry attempts
failures accumulate
circuit eventually OPEN
```

After opening, later requests should fail fast.


---

## 34. Failure Drill — Product Down

```bash
docker compose stop product-service
```

Create Orders repeatedly.

Observe:

```text
connection failure
bounded retries
circuit OPEN
future calls rejected quickly
```

Restart:

```bash
docker compose start product-service
```


---

## 35. Failure Drill — Recovery

After Product restarts:

```text
circuit may still be OPEN
```

Wait for:

```text
wait-duration-in-open-state
```

Then observe:

```text
OPEN -> HALF_OPEN
```

Successful probes should cause:

```text
HALF_OPEN -> CLOSED
```


---

## 36. Failure Drill — Slow Product

Temporarily delay Product GET for local learning:

```java
Thread.sleep(5000);
```

With a 3-second read timeout, Order should fail before Product responds.

Observe:

```text
timeout
retry behavior
circuit slow/failure recording
```

Remove the delay afterward.


---

## 37. Failure Drill — Product 404

Use an invalid Product ID.

Expected:

```text
Product 404
-> ProductNotAvailableForOrderException
-> no retry
-> circuit remains healthy
```

This validates failure classification.


---

## 38. Actuator for Resilience

For local learning, expose relevant endpoints:

```yaml
management:

  endpoints:

    web:

      exposure:

        include:
          - health
          - info
          - metrics
          - circuitbreakers
          - circuitbreakerevents
          - retries
          - retryevents
```

Do not expose operational endpoints publicly without appropriate security.


---

## 39. Metrics to Observe

Watch:

```text
successful calls
failed calls
slow calls
not-permitted calls
retry attempts
retry success
retry failure
```

These metrics are how real systems tune resilience thresholds.


---

## 40. Circuit Events

Important events:

```text
SUCCESS
ERROR
NOT_PERMITTED
STATE_TRANSITION
```

Especially observe:

```text
CLOSED -> OPEN
OPEN -> HALF_OPEN
HALF_OPEN -> CLOSED
```


---

## 41. Call Not Permitted

When the circuit is OPEN:

```text
Product Service is not called
```

The call fails immediately.

That is the whole point of:

```text
fail fast
```

Translate the infrastructure exception into:

```text
ProductServiceUnavailableException
```

and return a stable Order API error.


---

## 42. Suggested Error Contract

```json
{
  "status": 503,
  "code": "PRODUCT_SERVICE_UNAVAILABLE",
  "message": "Product information is temporarily unavailable"
}
```

Do not expose Resilience4j internal exception names to API consumers.


---

## 43. Retry at One Layer

Avoid:

```text
Gateway retries
+
Order retries
+
Product retries
```

unless deliberately designed.

Example:

```text
Gateway: 3 attempts
Order:   3 attempts
```

can produce up to:

```text
9 downstream attempts
```

For Day 13, keep Product dependency retry inside Order's `ProductClient`.


---

## 44. Retry Amplification

If the client, gateway, and service all retry independently, one user request may explode into many network calls.

This is retry amplification.

A senior engineer should always ask:

```text
Which layer owns retries?
```


---

## 45. Timeout Hierarchy

Useful principle:

```text
outer timeout
>
downstream total retry budget
```

Example:

```text
Gateway overall budget: 6s
Order -> Product total budget: 4s
individual Product attempt: ~1–1.5s
```

Exact values depend on your environment.


---

## 46. Pattern Comparison

```text
Timeout
    limits how long a call may wait

Retry
    decides whether to try again

Circuit Breaker
    decides whether dependency should be called at all
```

They solve different problems.


---

## 47. Bulkhead — Concept Only

Bulkhead limits concurrent resource consumption.

If Product becomes very slow, bulkhead isolation can prevent Product calls from consuming every Order thread/connection.

Do not implement deeply today.

Know why the pattern exists.


---

## 48. Rate Limiter — Concept Only

Rate Limiter answers:

```text
How many requests are allowed per time window?
```

Circuit Breaker answers:

```text
Is this dependency healthy enough to call?
```

They are different patterns.


---

## 49. TimeLimiter — Concept

Resilience4j has a `TimeLimiter`, especially useful with asynchronous/future-based execution.

For your synchronous `RestClient` path, Day 13 should primarily use proper HTTP connect/read timeouts.

Do not force an async abstraction into the design only to use another annotation.


---

## 50. Testing Strategy

You need Spring-context/integration tests because plain Mockito alone does not prove proxy-based:

```text
@Retry
@CircuitBreaker
```

behavior.

Use a stub HTTP server or controlled Product test endpoint where practical.


---

## 51. Test — Retry Succeeds

Stub:

```text
call 1 -> 500
call 2 -> 200
```

Assert:

```text
Order/ProductClient succeeds
Product stub received 2 calls
```


---

## 52. Test — 404 Not Retried

Stub:

```text
404
```

Assert:

```text
ProductNotAvailableForOrderException
Product stub called exactly once
```


---

## 53. Test — Circuit Opens

Stub:

```text
always 500
```

Call enough times to cross threshold.

Assert:

```text
CircuitBreaker state == OPEN
```

Then call again and verify the HTTP stub is not reached.


---

## 54. Test — HALF_OPEN Recovery

After OPEN:

1. wait for the configured open-state duration
2. make Product healthy
3. trigger permitted probe calls
4. assert transition to CLOSED


---

## 55. Test — Timeout

Stub Product to delay beyond read timeout.

Assert:

```text
bounded failure occurs
```

Avoid ultra-precise millisecond assertions; use reasonable tolerance.


---

## 56. Test — No Partial Order

When Product fails:

```text
createOrder(...)
```

must not persist an Order.

Assert:

```text
OrderRepository count unchanged
```

This preserves your Day 12 invariant.


---

## 57. Docker Impact

No new infrastructure container is required.

Resilience4j runs inside:

```text
order-service
```

Your existing stack remains:

```text
api-gateway
identity-provider
authorizer-service
profile-service
favorites-service
product-service
order-service
PostgreSQL containers
Redis
```


---

## 58. Suggested Configuration Summary

```yaml
resilience4j:

  retry:

    instances:

      productService:
        max-attempts: 3
        wait-duration: 500ms

  circuitbreaker:

    instances:

      productService:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        minimum-number-of-calls: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
        slow-call-duration-threshold: 2s
        slow-call-rate-threshold: 50
```

Add retryable/ignored exception classification intentionally.


---

## 59. Production Tuning Principle

Do not blindly reuse learning values in production.

Tune based on:

```text
latency percentiles
error rates
dependency SLO
traffic volume
request latency budget
```

Resilience config is operational configuration, not magic constants.


---

## 60. Common Mistakes

### Retrying every exception

Do not retry deterministic 4xx failures.

### Infinite retries

Always bound attempts and time.

### Fake-price fallback

Never sacrifice pricing correctness just to return success.

### Circuit opens after one failure

Use `minimum-number-of-calls`.

### Circuit takes too long to open

Avoid huge thresholds that keep hammering a dead dependency.

### Testing only happy path

A resilience implementation must be deliberately failure-tested.

### Retrying non-idempotent writes

Do not copy the Product GET retry policy blindly to Payment/Order POST calls.


---

## 61. Interview Questions

1. What is resilience in microservices?
2. What is a timeout?
3. Why are network timeouts mandatory?
4. What is retry?
5. When should you retry?
6. When should you not retry?
7. What is exponential backoff?
8. What is jitter?
9. What is a retry storm?
10. What is retry amplification?
11. What is a circuit breaker?
12. Explain CLOSED, OPEN, HALF_OPEN.
13. Why does OPEN fail fast?
14. What is failure-rate threshold?
15. What is `minimum-number-of-calls`?
16. What is a sliding window?
17. What is a slow-call threshold?
18. Why should 404 not open the circuit?
19. Why can 503 be retried?
20. Why does circuit breaker not replace timeout?
21. Why does retry not replace circuit breaker?
22. Why place resilience around ProductClient?
23. How do retries interact with idempotency?
24. What is a safe fallback?
25. What is an unsafe fallback?
26. How would you tune thresholds in production?
27. What metrics would you monitor?
28. What is a bulkhead?
29. Rate limiter vs circuit breaker?
30. Why externalize resilience configuration?


---

## 62. Manual Test Matrix

```text
Scenario                 Expected
------------------------------------------------------------
Product healthy          one call, success, CLOSED

1 transient 500          retry, then success

Product always 500       retries, circuit eventually OPEN

Product stopped          bounded retry, OPEN

Circuit OPEN             fail fast, no Product HTTP call

Product recovers         OPEN -> HALF_OPEN -> CLOSED

Product slow             timeout

Product 404              no retry, business rejection
```


---

## 63. Coding Checklist

```text
[ ] Resilience4j dependency added
[ ] AOP support verified
[ ] ProductClient used as resilience boundary
[ ] Retry configured
[ ] Circuit Breaker configured
[ ] HTTP connect/read timeout retained
[ ] Retryable exceptions classified
[ ] 404 ignored from retry/circuit health
[ ] 401/403 not retried
[ ] @Retry added
[ ] @CircuitBreaker added
[ ] fallback implemented safely
[ ] CLOSED observed
[ ] OPEN observed
[ ] HALF_OPEN observed
[ ] recovery to CLOSED observed
[ ] slow-call drill completed
[ ] Product-down drill completed
[ ] Order not persisted on Product failure
[ ] Actuator endpoints inspected
[ ] resilience metrics/logs inspected
[ ] retry success test passes
[ ] 404 no-retry test passes
[ ] circuit-open test passes
[ ] recovery test passes
[ ] timeout test passes
[ ] docs/day-13.md updated
[ ] Git commit completed
```


---

## 64. Scorecard

```text
Retry configured correctly                  1/1
Failure classification correct              1/1
Circuit Breaker configured                  1/1
OPEN demonstrated                           1/1
HALF_OPEN recovery demonstrated             1/1
Timeout demonstrated                        1/1
Fallback is business-safe                   1/1
Order persistence remains correct           1/1
Tests + metrics/logs complete               1/1
Docs + interview recap + commit             1/1

TOTAL                                      /10
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
docs/day-13.md
```

Record:

```text
Product connect timeout:
...

Product read timeout:
...

Max attempts:
...

Retry wait:
...

Retryable exceptions:
...

Ignored exceptions:
...

Circuit window:
...

Minimum calls:
...

Failure threshold:
...

OPEN duration:
...

HALF_OPEN probe count:
...

Transient 500 result:
...

Product-down result:
...

Recovery result:
...

404 result:
...

Fallback behavior:
...

Still unclear:
...
```


---

## 66. Topics Deliberately Deferred

Do not go deep today into:

```text
Bulkhead implementation
RateLimiter implementation
distributed rate limiting
service-mesh retry
Gateway retry
hedged requests
adaptive concurrency
chaos-engineering platforms
```

Day 13 stays focused on:

```text
timeout
retry
backoff
jitter
circuit breaker
fallback
failure classification
recovery
```


---

# Day 13 Definition of Done

Day 13 is complete when you can demonstrate:

```text
Order Service
    |
    v
ProductClient
    |
    +--> timeout
    +--> retry
    +--> circuit breaker
    |
    v
Product Service
```

You must prove:

```text
healthy Product
    -> success

transient failure
    -> retry succeeds

persistent failure
    -> circuit opens

OPEN circuit
    -> fail fast

Product recovers
    -> HALF_OPEN probes
    -> CLOSED

slow Product
    -> timeout

404 Product
    -> no retry
```

You should be able to explain without notes:

```text
timeout vs retry vs circuit breaker
CLOSED / OPEN / HALF_OPEN
backoff
jitter
retry storm
retry amplification
failure classification
safe vs unsafe fallback
retry + idempotency
```

If you can stop Product Service, watch bounded retries occur, observe the circuit transition to OPEN, restart Product, observe HALF_OPEN recovery to CLOSED, and prove that no invalid Order is persisted, Day 13 is complete.
