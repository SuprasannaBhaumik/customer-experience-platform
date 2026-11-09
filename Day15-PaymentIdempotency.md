# Day 15 — Payment Service, Idempotency, Safe Retries, and Duplicate-Request Protection

## Recommended Git Branch

`day-15-payment-idempotency`

## Objective

Day 15 introduces one of the most important reliability concepts in backend systems: **idempotency**.

You will build a new `payment-service` backed by PostgreSQL and make payment creation safe against duplicate requests caused by retries, timeouts, user double-clicks, network failures, or intermediary retries.

Target architecture:

```text
Client
  |
  | Bearer JWT
  | Idempotency-Key: abc-123
  v
API Gateway :8080
  |
  v
Authorizer :9001
  |
  v
Payment Service :8085
  |
  +--> payment_db
  |
  +--> Fake Payment Provider
```

The main result you must prove is:

```text
same authenticated subject
+
same Idempotency-Key
+
same request body
=
one logical payment
one provider execution
same response on retry
```

---

# 1. Why Day 15 Matters

Without idempotency:

```text
Client sends payment
        |
        v
Provider charges successfully
        |
        X
Response is lost
        |
        v
Client retries
        |
        v
Provider charges again
```

Possible result:

```text
double charge
duplicate payment record
reconciliation problem
customer incident
```

Idempotency solves this by making retries safe.

---

# 2. Day 15 Agenda

```text
00–10 min   Review architecture and define Payment bounded context
10–25 min   Create payment-service + payment_db
25–40 min   Implement Payment entity/repository/DTOs
40–55 min   Create provider abstraction + fake provider
55–75 min   Create idempotency table/state machine
75–95 min   Implement idempotent payment orchestration
95–105 min  Handle concurrency and duplicate races
105–115 min Add integration tests
115–120 min Run curls + interview recap
```

---

# 3. Payment Service Responsibility

Payment Service owns:

```text
payment transaction
payment status
payment amount
currency
provider reference
order association
customer association
idempotency state
```

It does not own:

```text
Product catalog
Order items
Customer profile
raw card details
CVV
```

---

# 4. Do Not Store Raw Card Details

For this refresher, store only tokenized/provider identifiers such as:

```text
paymentMethodId
providerToken
lastFourDigits
providerReference
```

Never persist:

```text
full PAN/card number
CVV
magnetic-stripe data
```

Use fake tokens such as:

```text
pm_test_123
```

for the lab.

---

# 5. Create `payment-service`

Suggested module:

```text
services/payment-service
```

Suggested package:

```text
com.customer.payment
```

Suggested port:

```text
8085
```

Dependencies:

```text
Spring Web
Spring Validation
Spring Data JPA
PostgreSQL Driver
Actuator
Spring Boot Test
Spring Security
```

---

# 6. Add `payment_db`

Docker Compose:

```yaml
payment-db:
  image: postgres:17

  environment:
    POSTGRES_DB: payment_db
    POSTGRES_USER: payment_user
    POSTGRES_PASSWORD: payment_password

  ports:
    - "5437:5432"

  volumes:
    - payment-db-data:/var/lib/postgresql/data
```

Local connection:

```text
jdbc:postgresql://localhost:5437/payment_db
```

Docker connection:

```text
jdbc:postgresql://payment-db:5432/payment_db
```

---

# 7. Payment Status

Create:

```java
public enum PaymentStatus {

    PENDING,

    PROCESSING,

    SUCCEEDED,

    FAILED,

    UNKNOWN
}
```

Meaning:

```text
PENDING
    accepted but not started

PROCESSING
    provider call is in progress

SUCCEEDED
    provider confirmed success

FAILED
    provider confirmed failure

UNKNOWN
    provider outcome is ambiguous
```

`UNKNOWN` is useful for provider timeout cases where you cannot tell whether the provider charged or not.

---

# 8. Payment Entity

```java
@Entity
@Table(
    name = "payment",
    indexes = {
        @Index(
            name = "idx_payment_order_id",
            columnList = "order_id"
        ),
        @Index(
            name = "idx_payment_customer_id",
            columnList = "customer_id"
        )
    }
)
public class PaymentEntity {

    @Id
    private UUID paymentId;

    @Column(
        name = "order_id",
        nullable = false
    )
    private UUID orderId;

    @Column(
        name = "customer_id",
        nullable = false
    )
    private UUID customerId;

    @Column(
        nullable = false,
        precision = 14,
        scale = 2
    )
    private BigDecimal amount;

    @Column(
        nullable = false,
        length = 3
    )
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(
        name = "payment_method_id",
        nullable = false
    )
    private String paymentMethodId;

    @Column(
        name = "provider_reference"
    )
    private String providerReference;

    @Column(
        name = "failure_code"
    )
    private String failureCode;

    @Column(
        name = "failure_message"
    )
    private String failureMessage;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected PaymentEntity() {
    }

    // constructors/getters/setters
}
```

---

# 9. Payment Repository

```java
public interface PaymentRepository
        extends JpaRepository<
            PaymentEntity,
            UUID
        > {

    Optional<PaymentEntity>
    findByOrderId(
        UUID orderId
    );

    List<PaymentEntity>
    findByCustomerIdOrderByCreatedAtDesc(
        UUID customerId
    );
}
```

---

# 10. Create Payment Request

```java
public record CreatePaymentRequest(

        @NotNull
        UUID orderId,

        @NotNull
        UUID customerId,

        @NotNull
        @DecimalMin("0.01")
        BigDecimal amount,

        @NotBlank
        @Size(min = 3, max = 3)
        String currency,

        @NotBlank
        String paymentMethodId
) {
}
```

Example:

```json
{
  "orderId":"11111111-1111-1111-1111-111111111111",
  "customerId":"22222222-2222-2222-2222-222222222222",
  "amount":5000.00,
  "currency":"INR",
  "paymentMethodId":"pm_test_123"
}
```

Do not let the client send:

```text
status
providerReference
failureCode
```

Those are owned by Payment Service.

---

# 11. Payment Response

```java
public record PaymentResponse(

        UUID paymentId,

        UUID orderId,

        UUID customerId,

        BigDecimal amount,

        String currency,

        PaymentStatus status,

        String providerReference,

        String failureCode,

        Instant createdAt
) {
}
```

---

# 12. API Contract

Implement:

```http
POST /payments
GET  /payments/{paymentId}
GET  /payments?orderId={orderId}
```

POST requires:

```http
Idempotency-Key: <client-generated-key>
```

Example:

```http
Idempotency-Key: payment-order-111-v1
```

---

# 13. What Is Idempotency?

Idempotency means:

```text
performing the same logical request multiple times
has the same externally visible effect
as performing it once
```

For this service:

```text
same subject
same idempotency key
same request
```

must not result in multiple provider charges.

---

# 14. Idempotency States

Create:

```java
public enum IdempotencyStatus {

    IN_PROGRESS,

    COMPLETED,

    FAILED
}
```

Meaning:

```text
IN_PROGRESS
    one request currently owns execution

COMPLETED
    operation completed and stored response exists

FAILED
    previous attempt ended in a known failure state
```

---

# 15. Idempotency Table

Create a dedicated entity.

Recommended uniqueness scope:

```text
subject + idempotency_key
```

This prevents one authenticated user from accidentally colliding with another user's key.

```java
@Entity
@Table(
    name = "idempotency_record",
    uniqueConstraints = {
        @UniqueConstraint(
            name =
                "uk_idempotency_subject_key",
            columnNames = {
                "subject",
                "idempotency_key"
            }
        )
    }
)
public class IdempotencyRecordEntity {

    @Id
    @GeneratedValue(
        strategy =
            GenerationType.IDENTITY
    )
    private Long id;

    @Column(
        nullable = false,
        length = 200
    )
    private String subject;

    @Column(
        name = "idempotency_key",
        nullable = false,
        length = 200
    )
    private String idempotencyKey;

    @Column(
        name = "request_hash",
        nullable = false,
        length = 64
    )
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IdempotencyStatus status;

    @Column(
        name = "payment_id"
    )
    private UUID paymentId;

    @Column(
        name = "response_code"
    )
    private Integer responseCode;

    @Column(
        name = "response_body",
        columnDefinition = "text"
    )
    private String responseBody;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected IdempotencyRecordEntity() {
    }

    // constructors/getters/setters
}
```

---

# 16. Why Store a Request Hash?

Suppose:

```text
Idempotency-Key: abc
amount = 1000
```

succeeds.

Then the caller sends:

```text
Idempotency-Key: abc
amount = 5000
```

This is not the same logical request.

If you only check the key, you might incorrectly return the old payment response.

Store a deterministic hash of the request payload.

---

# 17. Request Hash Generator

Use stable fields:

```text
orderId
customerId
normalized amount
currency
paymentMethodId
```

Example:

```java
@Component
public class RequestHashGenerator {

    public String hash(
            CreatePaymentRequest request) {

        String canonical =
                request.orderId()
                + "|"
                + request.customerId()
                + "|"
                + request.amount()
                    .stripTrailingZeros()
                    .toPlainString()
                + "|"
                + request.currency()
                    .toUpperCase(
                        Locale.ROOT
                    )
                + "|"
                + request.paymentMethodId();

        try {

            MessageDigest digest =
                MessageDigest.getInstance(
                    "SHA-256"
                );

            byte[] result =
                digest.digest(
                    canonical.getBytes(
                        StandardCharsets.UTF_8
                    )
                );

            return HexFormat
                .of()
                .formatHex(result);

        } catch (
            NoSuchAlgorithmException ex
        ) {

            throw new IllegalStateException(
                "SHA-256 unavailable",
                ex
            );
        }
    }
}
```

---

# 18. Idempotency Repository

```java
public interface IdempotencyRecordRepository
        extends JpaRepository<
            IdempotencyRecordEntity,
            Long
        > {

    Optional<IdempotencyRecordEntity>
    findBySubjectAndIdempotencyKey(
        String subject,
        String idempotencyKey
    );
}
```

---

# 19. Main Idempotency Algorithm

```text
POST /payments
        |
        v
validate Idempotency-Key
        |
        v
calculate request hash
        |
        v
lookup subject + key
        |
        +--> not found
        |       |
        |       v
        |   INSERT IN_PROGRESS
        |       |
        |       v
        |   execute payment
        |
        +--> found
                |
                +--> hash differs
                |       -> 409
                |
                +--> COMPLETED
                |       -> return stored response
                |
                +--> IN_PROGRESS
                |       -> 409
                |
                +--> FAILED
                        -> return/handle previous failure
```

---

# 20. Validate Idempotency Key

```java
@Component
public class IdempotencyKeyValidator {

    public void validate(
            String key) {

        if (
            key == null
            ||
            key.isBlank()
        ) {

            throw new MissingIdempotencyKeyException();
        }

        if (
            key.length() > 200
        ) {

            throw new InvalidIdempotencyKeyException(
                "Idempotency-Key is too long"
            );
        }
    }
}
```

---

# 21. Idempotency Decision

```java
public enum IdempotencyDecisionType {

    EXECUTE,

    RETURN_CACHED
}
```

```java
public record IdempotencyDecision(

        IdempotencyDecisionType type,

        PaymentResponse cachedResponse
) {

    public static IdempotencyDecision
    execute() {

        return new IdempotencyDecision(
            IdempotencyDecisionType.EXECUTE,
            null
        );
    }

    public static IdempotencyDecision
    cached(
            PaymentResponse response) {

        return new IdempotencyDecision(
            IdempotencyDecisionType
                .RETURN_CACHED,
            response
        );
    }
}
```

---

# 22. Idempotency Service — Begin

```java
@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository
        repository;

    private final ObjectMapper objectMapper;

    public IdempotencyService(
            IdempotencyRecordRepository
                repository,
            ObjectMapper objectMapper) {

        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public IdempotencyDecision begin(
            String subject,
            String key,
            String requestHash) {

        Optional<IdempotencyRecordEntity>
        existing =
            repository
                .findBySubjectAndIdempotencyKey(
                    subject,
                    key
                );

        if (existing.isPresent()) {

            return evaluateExisting(
                existing.get(),
                requestHash
            );
        }

        IdempotencyRecordEntity record =
            new IdempotencyRecordEntity(
                subject,
                key,
                requestHash,
                IdempotencyStatus.IN_PROGRESS,
                Instant.now()
            );

        try {

            repository.saveAndFlush(
                record
            );

            return IdempotencyDecision
                .execute();

        } catch (
            DataIntegrityViolationException ex
        ) {

            IdempotencyRecordEntity winner =
                repository
                    .findBySubjectAndIdempotencyKey(
                        subject,
                        key
                    )
                    .orElseThrow(
                        () -> ex
                    );

            return evaluateExisting(
                winner,
                requestHash
            );
        }
    }
}
```

---

# 23. Evaluate Existing Record

```java
private IdempotencyDecision evaluateExisting(
        IdempotencyRecordEntity record,
        String requestHash) {

    if (
        !record
            .getRequestHash()
            .equals(requestHash)
    ) {

        throw new IdempotencyKeyConflictException(
            record.getIdempotencyKey()
        );
    }

    if (
        record.getStatus()
            == IdempotencyStatus.COMPLETED
    ) {

        return IdempotencyDecision
            .cached(
                deserialize(
                    record.getResponseBody()
                )
            );
    }

    if (
        record.getStatus()
            == IdempotencyStatus.IN_PROGRESS
    ) {

        throw new PaymentAlreadyInProgressException(
            record.getIdempotencyKey()
        );
    }

    throw new PreviousPaymentAttemptFailedException(
        record.getIdempotencyKey()
    );
}
```

---

# 24. Why `saveAndFlush()`?

Two concurrent requests may both see:

```text
no record
```

Then both attempt INSERT.

The unique constraint decides the winner.

`saveAndFlush()` forces SQL execution immediately so the loser gets:

```text
DataIntegrityViolationException
```

inside the idempotency reservation step.

---

# 25. Concurrency Race

```text
Request A                  Request B
    |                          |
    v                          v
SELECT key                 SELECT key
none                       none
    |                          |
    v                          v
INSERT                     INSERT
    |                          |
 success                    unique violation
    |                          |
    v                          v
owns execution             reload existing record
```

This is why:

```text
SELECT then INSERT
```

alone is not sufficient.

Database uniqueness is your correctness boundary.

---

# 26. Payment Provider Boundary

Create:

```java
public interface PaymentProviderClient {

    ProviderPaymentResult charge(
        ProviderPaymentRequest request
    );
}
```

This separates:

```text
Payment business logic
```

from:

```text
provider-specific HTTP/SDK logic
```

---

# 27. Provider Request

```java
public record ProviderPaymentRequest(

        UUID paymentId,

        BigDecimal amount,

        String currency,

        String paymentMethodId,

        String idempotencyKey
) {
}
```

Include the idempotency key so a real provider integration can use provider-side idempotency too.

---

# 28. Provider Result

```java
public record ProviderPaymentResult(

        ProviderPaymentStatus status,

        String providerReference,

        String failureCode,

        String failureMessage
) {
}
```

```java
public enum ProviderPaymentStatus {

    SUCCEEDED,

    DECLINED,

    UNKNOWN
}
```

---

# 29. Fake Provider

```java
@Component
@Profile({
    "local",
    "docker",
    "test"
})
public class FakePaymentProviderClient
        implements PaymentProviderClient {

    private final ConcurrentMap<
        String,
        ProviderPaymentResult
    > results =
        new ConcurrentHashMap<>();

    @Override
    public ProviderPaymentResult charge(
            ProviderPaymentRequest request) {

        return results.computeIfAbsent(
            request.idempotencyKey(),
            key ->
                executeCharge(request)
        );
    }

    private ProviderPaymentResult executeCharge(
            ProviderPaymentRequest request) {

        if (
            "pm_fail".equals(
                request.paymentMethodId()
            )
        ) {

            return new ProviderPaymentResult(
                ProviderPaymentStatus.DECLINED,
                null,
                "DECLINED",
                "Payment declined"
            );
        }

        if (
            "pm_unknown".equals(
                request.paymentMethodId()
            )
        ) {

            return new ProviderPaymentResult(
                ProviderPaymentStatus.UNKNOWN,
                null,
                "UNKNOWN",
                "Provider result uncertain"
            );
        }

        return new ProviderPaymentResult(
            ProviderPaymentStatus.SUCCEEDED,
            "provider_"
                + UUID.randomUUID(),
            null,
            null
        );
    }
}
```

The fake provider itself is idempotent by key.

That lets you demonstrate layered idempotency.

---

# 30. Payment Persistence Service

Keep DB transactions short.

```java
@Service
public class PaymentPersistenceService {

    private final PaymentRepository repository;

    public PaymentPersistenceService(
            PaymentRepository repository) {

        this.repository = repository;
    }

    @Transactional
    public PaymentEntity createProcessing(
            CreatePaymentRequest request) {

        Instant now =
            Instant.now();

        PaymentEntity payment =
            new PaymentEntity(
                UUID.randomUUID(),
                request.orderId(),
                request.customerId(),
                request.amount(),
                request.currency()
                    .toUpperCase(
                        Locale.ROOT
                    ),
                PaymentStatus.PROCESSING,
                request.paymentMethodId(),
                now,
                now
            );

        return repository.save(
            payment
        );
    }

    @Transactional
    public PaymentResponse markSucceeded(
            UUID paymentId,
            String providerReference) {

        PaymentEntity payment =
            get(paymentId);

        payment.setStatus(
            PaymentStatus.SUCCEEDED
        );

        payment.setProviderReference(
            providerReference
        );

        payment.setUpdatedAt(
            Instant.now()
        );

        return toResponse(payment);
    }

    @Transactional
    public PaymentResponse markFailed(
            UUID paymentId,
            String failureCode,
            String failureMessage) {

        PaymentEntity payment =
            get(paymentId);

        payment.setStatus(
            PaymentStatus.FAILED
        );

        payment.setFailureCode(
            failureCode
        );

        payment.setFailureMessage(
            failureMessage
        );

        payment.setUpdatedAt(
            Instant.now()
        );

        return toResponse(payment);
    }

    @Transactional
    public PaymentResponse markUnknown(
            UUID paymentId,
            String failureMessage) {

        PaymentEntity payment =
            get(paymentId);

        payment.setStatus(
            PaymentStatus.UNKNOWN
        );

        payment.setFailureCode(
            "UNKNOWN"
        );

        payment.setFailureMessage(
            failureMessage
        );

        payment.setUpdatedAt(
            Instant.now()
        );

        return toResponse(payment);
    }

    private PaymentEntity get(
            UUID paymentId) {

        return repository
            .findById(paymentId)
            .orElseThrow(
                () ->
                    new PaymentNotFoundException(
                        paymentId
                    )
            );
    }

    private PaymentResponse toResponse(
            PaymentEntity payment) {

        return new PaymentResponse(
            payment.getPaymentId(),
            payment.getOrderId(),
            payment.getCustomerId(),
            payment.getAmount(),
            payment.getCurrency(),
            payment.getStatus(),
            payment.getProviderReference(),
            payment.getFailureCode(),
            payment.getCreatedAt()
        );
    }
}
```

---

# 31. Why Provider Call Must Be Outside DB Transaction

Bad:

```java
@Transactional
public PaymentResponse charge(...) {

    save PROCESSING

    call provider

    update SUCCEEDED
}
```

This holds a PostgreSQL connection/transaction while waiting on a remote dependency.

Better:

```text
short transaction
    save PROCESSING
commit

call provider
outside transaction

short transaction
    update final status
commit
```

---

# 32. Payment Execution Service

```java
@Service
public class PaymentExecutionService {

    private final PaymentPersistenceService
        persistenceService;

    private final PaymentProviderClient
        providerClient;

    public PaymentExecutionService(
            PaymentPersistenceService
                persistenceService,
            PaymentProviderClient
                providerClient) {

        this.persistenceService =
            persistenceService;

        this.providerClient =
            providerClient;
    }

    public PaymentResponse execute(
            String idempotencyKey,
            CreatePaymentRequest request) {

        PaymentEntity payment =
            persistenceService
                .createProcessing(
                    request
                );

        ProviderPaymentResult result =
            providerClient
                .charge(
                    new ProviderPaymentRequest(
                        payment.getPaymentId(),
                        payment.getAmount(),
                        payment.getCurrency(),
                        payment.getPaymentMethodId(),
                        idempotencyKey
                    )
                );

        return switch (
            result.status()
        ) {

            case SUCCEEDED ->
                persistenceService
                    .markSucceeded(
                        payment.getPaymentId(),
                        result.providerReference()
                    );

            case DECLINED ->
                persistenceService
                    .markFailed(
                        payment.getPaymentId(),
                        result.failureCode(),
                        result.failureMessage()
                    );

            case UNKNOWN ->
                persistenceService
                    .markUnknown(
                        payment.getPaymentId(),
                        result.failureMessage()
                    );
        };
    }
}
```

---

# 33. Payment Application Service

This orchestrates idempotency + payment execution.

```java
@Service
public class PaymentApplicationService {

    private final IdempotencyKeyValidator
        keyValidator;

    private final RequestHashGenerator
        hashGenerator;

    private final IdempotencyService
        idempotencyService;

    private final PaymentExecutionService
        paymentExecutionService;

    public PaymentApplicationService(
            IdempotencyKeyValidator
                keyValidator,
            RequestHashGenerator
                hashGenerator,
            IdempotencyService
                idempotencyService,
            PaymentExecutionService
                paymentExecutionService) {

        this.keyValidator =
            keyValidator;

        this.hashGenerator =
            hashGenerator;

        this.idempotencyService =
            idempotencyService;

        this.paymentExecutionService =
            paymentExecutionService;
    }

    public PaymentResponse createPayment(
            String subject,
            String idempotencyKey,
            CreatePaymentRequest request) {

        keyValidator.validate(
            idempotencyKey
        );

        String requestHash =
            hashGenerator
                .hash(request);

        IdempotencyDecision decision =
            idempotencyService
                .begin(
                    subject,
                    idempotencyKey,
                    requestHash
                );

        if (
            decision.type()
                == IdempotencyDecisionType
                    .RETURN_CACHED
        ) {

            return decision
                .cachedResponse();
        }

        PaymentResponse response;

        try {

            response =
                paymentExecutionService
                    .execute(
                        idempotencyKey,
                        request
                    );

        } catch (
            RuntimeException ex
        ) {

            idempotencyService
                .fail(
                    subject,
                    idempotencyKey
                );

            throw ex;
        }

        idempotencyService
            .complete(
                subject,
                idempotencyKey,
                response
            );

        return response;
    }
}
```

---

# 34. Complete Idempotency Record

```java
@Transactional
public void complete(
        String subject,
        String key,
        PaymentResponse response) {

    IdempotencyRecordEntity record =
        repository
            .findBySubjectAndIdempotencyKey(
                subject,
                key
            )
            .orElseThrow();

    record.setStatus(
        IdempotencyStatus.COMPLETED
    );

    record.setPaymentId(
        response.paymentId()
    );

    record.setResponseCode(
        response.status()
            == PaymentStatus.SUCCEEDED
            ? 201
            : 422
    );

    record.setResponseBody(
        serialize(response)
    );

    record.setUpdatedAt(
        Instant.now()
    );
}
```

---

# 35. Mark Idempotency Failure

```java
@Transactional
public void fail(
        String subject,
        String key) {

    repository
        .findBySubjectAndIdempotencyKey(
            subject,
            key
        )
        .ifPresent(
            record -> {

                record.setStatus(
                    IdempotencyStatus.FAILED
                );

                record.setUpdatedAt(
                    Instant.now()
                );
            }
        );
}
```

Be careful: not every failure is safe to classify as "definitely failed".

Provider timeout may be ambiguous.

For a real system, you would distinguish:

```text
FAILED
UNKNOWN
REQUIRES_RECONCILIATION
```

more explicitly.

---

# 36. Payment Controller

```java
@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentApplicationService
        paymentApplicationService;

    public PaymentController(
            PaymentApplicationService
                paymentApplicationService) {

        this.paymentApplicationService =
            paymentApplicationService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse>
    createPayment(

            @RequestHeader(
                "Idempotency-Key"
            )
            String idempotencyKey,

            @RequestHeader(
                "X-Authenticated-User"
            )
            String subject,

            @Valid
            @RequestBody
            CreatePaymentRequest request) {

        PaymentResponse response =
            paymentApplicationService
                .createPayment(
                    subject,
                    idempotencyKey,
                    request
                );

        HttpStatus status =
            response.status()
                == PaymentStatus.SUCCEEDED
                ? HttpStatus.CREATED
                : HttpStatus.UNPROCESSABLE_ENTITY;

        return ResponseEntity
            .status(status)
            .body(response);
    }
}
```

---

# 37. Why Store the Previous Response?

Suppose:

```text
provider succeeded
DB updated
idempotency COMPLETED
response was lost
```

Client retries.

You want:

```text
same paymentId
same status
same providerReference
```

without calling the provider again.

Therefore store either:

```text
response body
```

or enough state to reconstruct the exact logical response.

---

# 38. Same Key + Same Request

Expected behavior:

```text
request 1
    -> provider called
    -> payment created
    -> response stored

request 2
same key/body/subject
    -> cached response returned
    -> provider NOT called
```

This is the most important Day 15 scenario.

---

# 39. Same Key + Different Request

Example:

First:

```text
key = abc
amount = 1000
```

Then:

```text
key = abc
amount = 5000
```

Expected:

```text
409 Conflict
```

Example error:

```json
{
  "status":409,
  "code":"IDEMPOTENCY_KEY_CONFLICT",
  "message":"Idempotency-Key was already used with a different request"
}
```

---

# 40. IN_PROGRESS Duplicate

If request B arrives while request A still owns execution:

```text
same subject
same key
same hash
status = IN_PROGRESS
```

For Day 15, return:

```text
409 Conflict
```

Example:

```json
{
  "status":409,
  "code":"PAYMENT_IN_PROGRESS",
  "message":"A request with this Idempotency-Key is already processing"
}
```

---

# 41. FAILED Duplicate

For Day 15, use the conservative policy:

```text
previous FAILED
-> do not silently re-execute provider
```

Return an error requiring a deliberate new idempotency key.

Why?

Because financial retries should be explicit when the prior result may be ambiguous.

---

# 42. Provider Timeout Is Different from Decline

Provider decline:

```text
provider explicitly says "not charged"
```

Provider timeout:

```text
you do not know whether provider charged
```

This is an **uncertain outcome**.

Do not treat them the same.

---

# 43. Provider Success but DB Update Fails

Sequence:

```text
Provider says SUCCESS
        |
        X
DB markSucceeded fails
```

Now:

```text
provider may have charged
local DB may still say PROCESSING
```

A local transaction cannot roll back the external provider.

This requires later concepts such as:

```text
provider reconciliation
provider idempotency
webhooks
recovery jobs
eventual consistency
```

Recognizing this limitation is part of Day 15.

---

# 44. Do Not Retry Provider Calls Blindly

Day 13 taught:

```text
GET Product
```

can often be retried.

Payment POST is different.

If provider timed out, blindly retrying can double-charge unless:

```text
provider supports idempotency
```

and you send the same provider-side idempotency key.

---

# 45. GET vs Payment POST

```text
GET Product
    naturally idempotent

POST Payment
    not naturally idempotent

POST Payment + Idempotency-Key
    made logically idempotent
```

This is a strong interview comparison.

---

# 46. Gateway Route

Add:

```text
/api/payments/**
```

Local:

```text
http://localhost:8085
```

Docker:

```text
http://payment-service:8085
```

With:

```text
StripPrefix=1
```

the downstream path becomes:

```text
/payments/**
```

---

# 47. Authorizer Rules

Add:

```text
POST /api/payments
    payment.write

GET /api/payments/**
    payment.read
```

Add scopes to the OIDC client:

```java
.scope("payment.read")
.scope("payment.write")
```

Then request a fresh token with the required scope.

---

# 48. Docker Configuration

`application-docker.yml`:

```yaml
spring:

  datasource:

    url:
      ${PAYMENT_DB_URL:jdbc:postgresql://payment-db:5432/payment_db}

    username:
      ${PAYMENT_DB_USERNAME:payment_user}

    password:
      ${PAYMENT_DB_PASSWORD:payment_password}
```

Compose:

```yaml
payment-service:

  build:
    context: ./services/payment-service

  environment:

    SPRING_PROFILES_ACTIVE:
      docker

    PAYMENT_DB_URL:
      jdbc:postgresql://payment-db:5432/payment_db

    PAYMENT_DB_USERNAME:
      payment_user

    PAYMENT_DB_PASSWORD:
      payment_password

  depends_on:
    - payment-db
```

---

# 49. Error Mapping

Recommended mappings:

```text
MissingIdempotencyKeyException
    -> 400

InvalidIdempotencyKeyException
    -> 400

IdempotencyKeyConflictException
    -> 409

PaymentAlreadyInProgressException
    -> 409

PreviousPaymentAttemptFailedException
    -> 409

PaymentNotFoundException
    -> 404

ProviderUnavailableException
    -> 503
```

---

# 50. Example API Error

```json
{
  "timestamp":"2026-10-26T10:00:00Z",
  "status":409,
  "code":"IDEMPOTENCY_KEY_CONFLICT",
  "message":"The Idempotency-Key was already used with different request data",
  "path":"/payments"
}
```

---

# 51. Unit Test — Request Hash

```java
@Test
void shouldGenerateSameHashForSameRequest() {

    CreatePaymentRequest request =
        sampleRequest();

    assertThat(
        generator.hash(request)
    )
    .isEqualTo(
        generator.hash(request)
    );
}
```

Different amount:

```java
@Test
void shouldChangeHashWhenAmountChanges() {

    CreatePaymentRequest first =
        requestWithAmount(
            "1000.00"
        );

    CreatePaymentRequest second =
        requestWithAmount(
            "5000.00"
        );

    assertThat(
        generator.hash(first)
    )
    .isNotEqualTo(
        generator.hash(second)
    );
}
```

---

# 52. Unit Test — Existing COMPLETED

Arrange:

```text
subject = user-1
key = abc
same hash
status = COMPLETED
stored response exists
```

Assert:

```text
RETURN_CACHED
```

Payment provider must never be involved.

---

# 53. Unit Test — Same Key Different Hash

Assert:

```text
IdempotencyKeyConflictException
```

---

# 54. Unit Test — IN_PROGRESS

Assert:

```text
PaymentAlreadyInProgressException
```

---

# 55. Unit Test — Provider Success

Mock provider:

```text
SUCCEEDED
providerReference = provider_123
```

Assert:

```text
Payment status = SUCCEEDED
providerReference saved
```

---

# 56. Unit Test — Provider Decline

Mock provider:

```text
DECLINED
```

Assert:

```text
Payment status = FAILED
failureCode saved
```

---

# 57. Integration Test — Same Request Twice

Mandatory test:

```text
POST #1
subject=user-1
key=abc
body=A

POST #2
subject=user-1
key=abc
body=A
```

Assert:

```text
same paymentId
same response
payment rows = 1
idempotency rows = 1
provider executions = 1
```

---

# 58. Integration Test — Different Payload Same Key

```text
POST #1
key=abc
amount=1000

POST #2
key=abc
amount=5000
```

Assert:

```text
409
one payment
provider called once
```

---

# 59. Integration Test — Missing Key

Assert:

```text
400
payment table unchanged
provider not called
```

---

# 60. Integration Test — Concurrent Duplicate

Run two calls concurrently with:

```text
same subject
same key
same request
```

Expected:

```text
only one INSERT wins
one logical provider execution
other request receives IN_PROGRESS/cached behavior
```

This test is valuable because it proves the database unique constraint protects race conditions.

Use PostgreSQL Testcontainers from Day 14.

---

# 61. Persistence Test — Unique Constraint

Create two records with identical:

```text
subject
idempotency_key
```

and force flush.

Expected:

```text
DataIntegrityViolationException
```

This proves the database, not only Java code, enforces uniqueness.

---

# 62. Manual Test — Obtain Token

Request a token containing:

```text
payment.write
```

Then export:

```bash
export TOKEN="<access-token>"
```

---

# 63. Manual Test — First Payment

```bash
curl -i \
  -X POST \
  http://localhost:8080/api/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: payment-order-111-v1" \
  -H "Content-Type: application/json" \
  -d '{
    "orderId":"11111111-1111-1111-1111-111111111111",
    "customerId":"22222222-2222-2222-2222-222222222222",
    "amount":5000.00,
    "currency":"INR",
    "paymentMethodId":"pm_test_123"
  }'
```

Expected:

```text
201 Created
```

Example:

```json
{
  "paymentId":"...",
  "orderId":"11111111-1111-1111-1111-111111111111",
  "customerId":"22222222-2222-2222-2222-222222222222",
  "amount":5000.00,
  "currency":"INR",
  "status":"SUCCEEDED",
  "providerReference":"provider_..."
}
```

---

# 64. Manual Test — Same Request Again

Run the exact same curl again.

Expected:

```text
same paymentId
same logical result
no second provider call
```

This is your primary Day 15 proof.

---

# 65. Manual Test — Same Key Different Amount

Keep:

```text
Idempotency-Key: payment-order-111-v1
```

Change:

```text
amount = 6000.00
```

Expected:

```text
409 Conflict
```

---

# 66. Manual Test — New Key Same Payload

Use:

```text
Idempotency-Key: payment-order-111-v2
```

with the same body.

Expected:

```text
new logical operation
new paymentId
```

The idempotency key defines request identity, not the body alone.

---

# 67. Manual Test — Declined Provider

Use:

```text
paymentMethodId = pm_fail
```

Expected:

```text
FAILED
```

Recommended API response:

```text
422 Unprocessable Entity
```

because the request was syntactically valid but the business payment was declined.

---

# 68. Manual Test — Unknown Outcome

Use:

```text
paymentMethodId = pm_unknown
```

Expected:

```text
status = UNKNOWN
```

This models a provider result that cannot safely be classified as succeeded or failed.

---

# 69. Inspect Database

```bash
docker exec -it payment-db \
  psql \
  -U payment_user \
  -d payment_db
```

Payments:

```sql
SELECT
    payment_id,
    order_id,
    customer_id,
    amount,
    currency,
    status,
    provider_reference,
    failure_code
FROM payment;
```

Idempotency:

```sql
SELECT
    subject,
    idempotency_key,
    request_hash,
    status,
    payment_id,
    response_code
FROM idempotency_record;
```

---

# 70. Expected Database Result After Duplicate Retry

After sending the same request twice:

```text
payment row count
    = 1

idempotency row count
    = 1
```

If you see:

```text
2 payment rows
```

your idempotency flow is broken.

---

# 71. Idempotency Lifetime

Real systems usually expire idempotency records after a retention period.

Examples:

```text
24 hours
7 days
30 days
```

For Day 15:

```text
do not implement cleanup
```

but keep:

```text
createdAt
updatedAt
```

so cleanup can be added later.

---

# 72. Do Not Use an In-Memory Map as Final Storage

Bad production design:

```java
ConcurrentHashMap<
    String,
    PaymentResponse
>
```

Problems:

```text
lost on restart
not shared between replicas
not durable
not safe across pods
```

Persist idempotency in a shared durable store.

For this project:

```text
PostgreSQL
```

---

# 73. Why Database Constraint Is Mandatory

Application logic:

```text
findByKey()
```

is not enough under concurrency.

Correctness requires:

```text
unique DB constraint
```

because the database arbitrates simultaneous writes atomically.

---

# 74. Security of Idempotency Records

Do not allow user A to reuse user B's idempotency key and receive user B's response.

That is why we scope uniqueness to:

```text
subject + key
```

and always query using both.

---

# 75. Optional Improvement — Include Client ID

A stronger multi-client model may scope keys by:

```text
clientId
+
subject
+
idempotencyKey
```

For Day 15, `subject + key` is sufficient.

---

# 76. Failure Scenario — Response Lost

```text
Payment succeeded
        |
        v
Idempotency COMPLETED
        |
        X
HTTP response lost
```

Retry:

```text
same key
```

returns stored response.

Provider remains untouched.

This is the exact scenario idempotency is designed to solve.

---

# 77. Failure Scenario — App Crashes After Provider Success

Harder scenario:

```text
provider success
        |
        X
application crashes before DB finalization
```

Possible local state:

```text
PROCESSING
```

Provider state:

```text
SUCCEEDED
```

This needs:

```text
reconciliation
provider lookup
webhook
recovery process
```

A local transaction cannot solve it.

---

# 78. Failure Scenario — DB Fails Before Provider

If idempotency reservation or `PROCESSING` persistence fails:

```text
do not call provider
```

Fail early.

This prevents money movement when you cannot durably record the attempt.

---

# 79. Failure Scenario — Provider Timeout

A timeout means:

```text
outcome unknown
```

not automatically:

```text
failed
```

Use:

```text
UNKNOWN
```

and avoid blind retry unless provider-side idempotency/reconciliation exists.

---

# 80. Common Mistakes

### No request hash

Same key can be accidentally reused for a different payment.

### No unique constraint

Concurrent requests can both execute.

### Idempotency only in memory

Breaks with restart/multiple instances.

### Blind retry after provider timeout

Can double-charge.

### Long DB transaction around provider

Consumes DB resources and still cannot roll back the provider.

### Trusting client status

Payment status must be server-owned.

### Returning a new payment on retry

Breaks idempotency.

### Storing raw card data

Avoid entirely in this refresher.

---

# 81. Interview Questions — Idempotency

1. What is idempotency?
2. Why is POST Payment not naturally idempotent?
3. What is an Idempotency-Key?
4. Who should generate it?
5. Why store the key?
6. Why store a request hash?
7. What should same key + same body do?
8. What should same key + different body do?
9. Why is a unique DB constraint required?
10. Why is SELECT-then-INSERT unsafe?
11. Why persist the previous response?
12. How would you expire idempotency records?
13. Why scope keys by authenticated subject?
14. How does idempotency work across multiple service replicas?

---

# 82. Interview Questions — Payments

15. Why not store raw card details?
16. What is a provider token?
17. Why store provider reference?
18. What is an uncertain payment outcome?
19. Why is provider timeout dangerous?
20. Why can't a DB transaction roll back an external provider?
21. What happens if provider succeeds but DB finalization fails?
22. What is reconciliation?
23. Why should provider retries use provider-side idempotency?
24. GET Product retry vs POST Payment retry?

---

# 83. Interview Questions — Transactions

25. Why persist PROCESSING before the provider call?
26. Why call provider outside a long DB transaction?
27. Why are status updates separate short transactions?
28. What happens if the process crashes between provider and DB update?
29. What would a recovery job do?
30. How does eventual consistency relate to payments?

---

# 84. Testing Requirements

Minimum:

```text
Request hash
- same request -> same hash
- changed request -> different hash

Idempotency
- new key -> EXECUTE
- COMPLETED -> cached
- same key different hash -> 409
- IN_PROGRESS -> 409
- DB unique constraint -> enforced

Payment execution
- provider success -> SUCCEEDED
- provider decline -> FAILED
- provider ambiguous -> UNKNOWN

Integration
- duplicate retry -> one payment
- duplicate retry -> one provider execution
- same key different payload -> conflict
- missing key -> 400
- concurrent duplicate -> one logical execution
```

---

# 85. Coding Checklist

```text
[ ] payment-service created

[ ] payment_db added

[ ] PaymentStatus created

[ ] PaymentEntity created

[ ] PaymentRepository created

[ ] CreatePaymentRequest created

[ ] PaymentResponse created

[ ] IdempotencyStatus created

[ ] IdempotencyRecordEntity created

[ ] subject + key unique constraint added

[ ] RequestHashGenerator created

[ ] IdempotencyKeyValidator created

[ ] IdempotencyRecordRepository created

[ ] IdempotencyDecision created

[ ] IdempotencyService.begin implemented

[ ] concurrent insert race handled

[ ] PaymentProviderClient created

[ ] FakePaymentProviderClient created

[ ] provider-side fake idempotency implemented

[ ] PaymentPersistenceService created

[ ] PaymentExecutionService created

[ ] PaymentApplicationService created

[ ] COMPLETED replay implemented

[ ] same-key/different-hash rejected

[ ] IN_PROGRESS behavior implemented

[ ] FAILED behavior documented

[ ] UNKNOWN payment behavior implemented

[ ] PaymentController created

[ ] exception mappings added

[ ] Gateway payment route added

[ ] Authorizer payment rules added

[ ] OIDC payment scopes added

[ ] Docker Compose updated

[ ] PostgreSQL Testcontainer tests added

[ ] concurrency test added

[ ] provider-call-count assertion added

[ ] manual duplicate curl passes

[ ] DB row count verified

[ ] docs/day-15.md updated

[ ] Git commit completed
```

---

# 86. Scorecard

```text
Payment Service works                           1/1

Payment DB schema correct                       1/1

Idempotency reservation works                   1/1

Same key + same request replays safely          1/1

Same key + different request rejected           1/1

Concurrent duplicate protected                  1/1

Provider abstraction/idempotency works          1/1

Transaction/failure model understood            1/1

Integration tests pass                          1/1

Docs + interview recap + commit                 1/1

TOTAL                                          /10
```

Target:

```text
8/10 minimum
10/10 preferred
```

---

# 87. Recommended Day Notes

Create:

```text
docs/day-15.md
```

Record:

```text
Payment service port:
8085

Payment DB:
payment_db

Idempotency scope:
subject + key

Hash fields:
...

First request result:
...

Duplicate result:
...

Different-body result:
...

Concurrent duplicate result:
...

Provider success result:
...

Provider decline result:
...

Unknown-outcome result:
...

Provider call count:
...

Transaction boundary:
...

Crash-after-provider scenario:
...

Still unclear:
...
```

---

# 88. Topics Deliberately Deferred

Do not go deep today into:

```text
real Stripe/Razorpay integration
PCI DSS implementation
refunds
chargebacks
webhooks
3DS
reconciliation jobs
financial ledger
Saga
Kafka payment events
Outbox
```

Day 15 is focused on:

```text
Payment Service
provider abstraction
idempotency
safe retries
duplicate protection
concurrency
transaction boundaries
uncertain outcomes
```

---

# Day 15 Definition of Done

Day 15 is complete when this flow works:

```text
POST /api/payments
Authorization: Bearer ...
Idempotency-Key: abc-123
        |
        v
Gateway
        |
        v
Authorizer
        |
        v
Payment Service
        |
        +--> validate key
        |
        +--> hash request
        |
        +--> reserve IN_PROGRESS
        |
        +--> save PROCESSING payment
        |
        +--> call provider once
        |
        +--> save SUCCEEDED/FAILED/UNKNOWN
        |
        +--> mark idempotency COMPLETED
        |
        v
Client
```

Retrying:

```text
same subject
same Idempotency-Key
same body
```

must return:

```text
same paymentId
same logical response
no second provider execution
```

You must demonstrate:

```text
first request -> executes once

same key + same body
-> cached response
-> one payment row
-> one provider call

same key + different body
-> 409

missing key
-> 400

concurrent same key
-> one logical execution

provider decline
-> FAILED

provider ambiguous result
-> UNKNOWN
```

You should be able to explain without notes:

```text
what idempotency means
why Payment POST needs it
why request hashing matters
why DB uniqueness matters
why provider timeout is ambiguous
why provider-side idempotency matters
why external provider calls cannot be rolled back by a local DB transaction
why PROCESSING is persisted before provider call
why raw card data should not be stored
why duplicate retries return the original response
```

If you can send the exact same payment request twice with the same Idempotency-Key and prove there is exactly one provider execution, one payment row, one idempotency row, and the same payment response is returned, Day 15 is complete.
