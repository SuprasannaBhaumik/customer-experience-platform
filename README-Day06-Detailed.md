# Day 06 — Transactions, Propagation, Isolation, Rollback, and Dirty Checking

## Objective

Day 06 focuses on one of the most important Spring interview topics: transaction management.

By the end of the day, you should be able to explain and demonstrate:

- `@Transactional`
- Transaction boundaries
- Spring transaction proxies
- Rollback behavior
- RuntimeException vs checked-exception behavior
- Propagation
- `REQUIRED`
- `REQUIRES_NEW`
- `SUPPORTS`
- `MANDATORY`
- Isolation levels
- Dirty checking
- Flush vs commit
- Self-invocation problems
- Read-only transactions

The goal is not simply to add `@Transactional`. The goal is to understand what Spring actually does around a transactional method and how database state changes under success and failure.

---

# 1. Day 06 Build Target

Continue from Day 05.

Create a workflow that updates multiple pieces of data atomically:

```text
Update customer profile
        +
Update profile preferences
        +
Write an audit record
```

All three operations must succeed together.

If anything fails:

```text
Everything rolls back.
```

Target flow:

```text
ProfileService.updateProfileAndPreferences()
        |
        +--> update profile
        +--> update preferences
        +--> insert audit row
        |
        +--> success -> COMMIT
        |
        +--> failure -> ROLLBACK
```

---

# 2. Why Transactions Matter

Without a transaction:

```text
Step 1 succeeds
Step 2 succeeds
Step 3 fails
```

You can end with inconsistent business state.

For example:

```text
Profile updated
Preferences updated
Audit missing
```

A transaction gives atomicity:

```text
All changes succeed
or
None are committed
```

---

# 3. ACID

Transactions are commonly described using ACID:

```text
A = Atomicity
C = Consistency
I = Isolation
D = Durability
```

## Atomicity

All operations in the transaction commit together or roll back together.

## Consistency

The transaction moves the system from one valid state to another.

## Isolation

Concurrent transactions do not interfere in invalid ways.

## Durability

Once committed, the data survives application/database restart.

---

# 4. Two-Hour Agenda

```text
00–10 min   Run Day 05 tests and verify PostgreSQL
10–20 min   Understand transaction boundary + proxy model
20–35 min   Add transactional multi-step workflow
35–50 min   Force rollback and verify DB state
50–65 min   Demonstrate dirty checking
65–80 min   Compare flush vs commit
80–95 min   REQUIRED vs REQUIRES_NEW
95–105 min  Reproduce self-invocation problem
105–113 min Review isolation levels
113–118 min Add readOnly examples
118–120 min Interview drill + scorecard
```

---

# 5. Create an Audit Entity

Create:

```text
entity/ProfileAuditEntity.java
```

Example:

```java
@Entity
@Table(name = "profile_audit")
public class ProfileAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private Instant createdAt;

    protected ProfileAuditEntity() {
    }

    public ProfileAuditEntity(
            UUID customerId,
            String action,
            Instant createdAt) {

        this.customerId = customerId;
        this.action = action;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getAction() {
        return action;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
```

Repository:

```java
public interface ProfileAuditRepository
        extends JpaRepository<ProfileAuditEntity, Long> {
}
```

---

# 6. `@Transactional`

Example:

```java
@Transactional
public ProfileResponse updateProfileAndPreferences(
        UUID customerId,
        UpdateProfileRequest request,
        List<PreferenceRequest> preferences) {

    ...
}
```

This tells Spring:

> Execute the method within a transaction.

Spring transaction behavior is typically applied through an AOP proxy/interceptor.

---

# 7. How `@Transactional` Works

Conceptually:

```text
Caller
  |
  v
Spring Proxy
  |
  +--> start/join transaction
  |
  v
Target Service Method
  |
  +--> repository operations
  |
  v
method returns
  |
  +--> commit
```

If a rollback-triggering exception escapes:

```text
Caller
  |
  v
Spring Proxy
  |
  +--> start/join transaction
  |
  v
Target Service Method
  |
  X RuntimeException
  |
  v
Proxy
  |
  +--> rollback
```

---

# 8. Transaction Proxy

Conceptually:

```text
ProfileServiceProxy
        |
        v
Real ProfileService
```

The proxy wraps the call with transaction logic:

```text
before method -> begin transaction
after success -> commit
after failure -> rollback
```

This same proxy idea later appears with:

```text
@Cacheable
@Async
@Retry
@PreAuthorize
```

---

# 9. Recommended Transaction Boundary

Put transaction boundaries in the service layer.

Good:

```java
@Service
public class ProfileService {

    @Transactional
    public void updateProfileAndPreferences(...) {
        ...
    }
}
```

Avoid using controllers as the primary business transaction boundary.

Preferred responsibility split:

```text
Controller  -> HTTP
Service     -> business workflow + transaction boundary
Repository  -> persistence
```

---

# 10. Multi-Step Transaction Example

```java
@Transactional
public ProfileResponse updateProfileAndPreferences(
        UUID customerId,
        UpdateProfileRequest request,
        List<PreferenceRequest> preferenceRequests) {

    CustomerProfileEntity profile =
            profileRepository.findById(customerId)
                    .orElseThrow(() ->
                            new ProfileNotFoundException(customerId)
                    );

    profile.setFirstName(request.firstName());
    profile.setLastName(request.lastName());
    profile.setEmail(request.email());

    profile.getPreferences().clear();

    for (PreferenceRequest pref : preferenceRequests) {
        profile.addPreference(
                new ProfilePreferenceEntity(
                        pref.key(),
                        pref.value()
                )
        );
    }

    auditRepository.save(
            new ProfileAuditEntity(
                    customerId,
                    "PROFILE_UPDATED",
                    Instant.now()
            )
    );

    return toResponse(profile);
}
```

Notice that if `profile` is already managed, an explicit:

```java
profileRepository.save(profile);
```

may not be necessary because of dirty checking.

---

# 11. Dirty Checking

Dirty checking means Hibernate tracks changes to managed entities.

Example:

```java
@Transactional
public void renameProfile(
        UUID customerId,
        String firstName) {

    CustomerProfileEntity profile =
            profileRepository.findById(customerId)
                    .orElseThrow();

    profile.setFirstName(firstName);
}
```

There is no `save()` call.

Hibernate can still generate:

```sql
UPDATE customer_profile ...
```

at flush/commit time.

---

# 12. Why Dirty Checking Works

After:

```java
profileRepository.findById(id)
```

the entity is managed in the persistence context.

Hibernate tracks the original state.

Example:

```text
Original firstName = John
New firstName      = Jane
```

At flush, Hibernate detects the difference and generates an update.

---

# 13. Dirty Checking Experiment

Create:

```java
@Transactional
public void renameProfileWithoutSave(
        UUID customerId,
        String firstName) {

    CustomerProfileEntity profile =
            profileRepository.findById(customerId)
                    .orElseThrow();

    profile.setFirstName(firstName);

    // no save()
}
```

Call it.

Then query PostgreSQL directly.

Expected:

```text
The name changed.
```

Also inspect SQL logs.

---

# 14. Rollback Experiment

Create:

```java
@Transactional
public void updateAndFail(UUID customerId) {

    CustomerProfileEntity profile =
            profileRepository.findById(customerId)
                    .orElseThrow();

    profile.setFirstName("SHOULD_ROLLBACK");

    auditRepository.save(
            new ProfileAuditEntity(
                    customerId,
                    "ROLLBACK_TEST",
                    Instant.now()
            )
    );

    throw new RuntimeException(
            "Simulated failure"
    );
}
```

After the call fails, verify:

```text
Profile name unchanged
Audit row not inserted
```

This proves rollback.

---

# 15. Default Rollback Rules

By default, Spring rolls back for:

```text
RuntimeException
Error
```

Checked exceptions do not automatically trigger rollback by default.

This is a common interview question.

---

# 16. RuntimeException Example

```java
@Transactional
public void runtimeFailure() {

    // DB changes

    throw new IllegalStateException(
            "fail"
    );
}
```

Expected:

```text
ROLLBACK
```

---

# 17. Checked Exception Behavior

```java
@Transactional
public void checkedFailure()
        throws Exception {

    // DB changes

    throw new Exception("checked");
}
```

Default behavior can be:

```text
COMMIT
```

because checked exceptions are not rollback triggers by default.

---

# 18. `rollbackFor`

Force rollback for checked exceptions:

```java
@Transactional(
    rollbackFor = Exception.class
)
public void checkedFailure()
        throws Exception {

    ...
}
```

Now:

```text
checked exception -> rollback
```

---

# 19. `noRollbackFor`

Example:

```java
@Transactional(
    noRollbackFor = SomeBusinessException.class
)
```

This tells Spring not to roll back for that exception type.

Use intentionally.

---

# 20. Rollback Summary

```text
RuntimeException -> rollback
Error            -> rollback
Checked Exception -> usually no rollback by default
```

Override using:

```text
rollbackFor
noRollbackFor
```

---

# 21. Flush vs Commit

These are not the same.

## Flush

Synchronizes pending persistence-context changes to the database.

## Commit

Completes the transaction.

Important:

```text
flush != commit
```

A flushed change can still be rolled back.

---

# 22. Flush Experiment

Example:

```java
@Transactional
public void updateFlushAndFail(
        UUID customerId) {

    CustomerProfileEntity profile =
            profileRepository.findById(customerId)
                    .orElseThrow();

    profile.setFirstName("FLUSHED");

    profileRepository.flush();

    throw new RuntimeException(
            "Rollback after flush"
    );
}
```

You may see SQL executed.

But after rollback:

```text
Final DB value remains unchanged.
```

---

# 23. `@PersistenceContext`

Example:

```java
@PersistenceContext
private EntityManager entityManager;
```

This injects a transaction-aware `EntityManager`.

For business code, prefer repositories unless direct EntityManager access is genuinely needed.

For Day 06, it is useful mainly for understanding JPA internals.

---

# 24. Read-Only Transactions

Example:

```java
@Transactional(readOnly = true)
public ProfileResponse getProfile(
        UUID customerId) {

    ...
}
```

This communicates read-only intent.

Possible benefits depend on the provider/database.

Do not treat `readOnly=true` as a hard security rule that physically prevents writes.

---

# 25. Good Read-Only Candidates

Examples:

```text
getProfile(...)
searchProfiles(...)
getProfileDetails(...)
```

---

# 26. Transaction Propagation

Propagation answers:

> What happens when one transactional method calls another?

Important modes:

```text
REQUIRED
REQUIRES_NEW
SUPPORTS
MANDATORY
NOT_SUPPORTED
NEVER
NESTED
```

Deeply understand:

```text
REQUIRED
REQUIRES_NEW
SUPPORTS
MANDATORY
```

---

# 27. `Propagation.REQUIRED`

Default propagation.

```java
@Transactional(
    propagation = Propagation.REQUIRED
)
```

Behavior:

```text
Existing transaction -> join it
No transaction       -> create one
```

---

# 28. REQUIRED Example

Outer:

```java
@Transactional
public void updateProfile(...) {

    preferenceService.updatePreferences(...);
}
```

Inner:

```java
@Transactional(
    propagation = Propagation.REQUIRED
)
public void updatePreferences(...) {
}
```

Both participate in one transaction.

If the outer method rolls back:

```text
Preference changes also roll back.
```

---

# 29. `Propagation.REQUIRES_NEW`

```java
@Transactional(
    propagation = Propagation.REQUIRES_NEW
)
```

Behavior:

```text
Suspend outer transaction
Start new transaction
Execute inner method
Commit/rollback inner transaction
Resume outer transaction
```

---

# 30. REQUIRES_NEW Use Case

Create:

```java
@Service
public class AuditService {

    @Transactional(
        propagation = Propagation.REQUIRES_NEW
    )
    public void writeAudit(
            UUID customerId,
            String action) {

        auditRepository.save(
                new ProfileAuditEntity(
                        customerId,
                        action,
                        Instant.now()
                )
        );
    }
}
```

Outer:

```java
@Transactional
public void updateProfileAndFail(
        UUID customerId) {

    CustomerProfileEntity profile =
            profileRepository.findById(customerId)
                    .orElseThrow();

    profile.setFirstName("FAIL");

    auditService.writeAudit(
            customerId,
            "ATTEMPTED_UPDATE"
    );

    throw new RuntimeException();
}
```

Expected:

```text
Profile update -> rolled back
Audit row      -> committed
```

---

# 31. REQUIRED vs REQUIRES_NEW

```text
REQUIRED
--------
Join existing transaction.
One rollback generally affects the whole unit.

REQUIRES_NEW
------------
Create an independent transaction.
Inner commit can survive outer rollback.
```

---

# 32. REQUIRES_NEW Caveat

A new transaction may need another DB connection.

Overuse can contribute to:

```text
Connection pool pressure
Connection pool exhaustion
```

This is an excellent senior-level interview point.

---

# 33. `Propagation.SUPPORTS`

Behavior:

```text
Existing transaction -> join
No transaction       -> run non-transactionally
```

Example:

```java
@Transactional(
    propagation = Propagation.SUPPORTS
)
public ProfileResponse getProfile(...) {
}
```

---

# 34. `Propagation.MANDATORY`

Behavior:

```text
Existing transaction required.
No transaction -> exception.
```

Useful when a method should only execute inside an existing business transaction.

---

# 35. Other Propagation Modes

## NOT_SUPPORTED

```text
Suspend existing transaction.
Run without one.
```

## NEVER

```text
Fail if a transaction exists.
```

## NESTED

Uses nested/savepoint semantics where supported.

Do not confuse:

```text
NESTED
```

with:

```text
REQUIRES_NEW
```

---

# 36. Why Use Separate Services for Propagation Experiments?

Spring transaction behavior depends on proxy interception.

A call between two Spring beans can cross a proxy.

A direct call inside the same object may not.

Therefore create:

```text
ProfileService
AuditService
PreferenceService
```

for clear propagation experiments.

---

# 37. Self-Invocation Problem

Consider:

```java
@Service
public class ProfileService {

    public void outer() {
        inner();
    }

    @Transactional
    public void inner() {
        ...
    }
}
```

When `outer()` calls:

```java
this.inner()
```

the call does not re-enter the Spring proxy.

The transaction advice may be bypassed.

---

# 38. Self-Invocation Flow

```text
External caller
   |
   v
Spring Proxy
   |
   v
outer()
   |
   v
this.inner()
```

The internal call goes directly to the target object.

It does not go:

```text
back through Spring Proxy
```

So `@Transactional` on `inner()` may not take effect as expected.

---

# 39. Self-Invocation Experiment

Create:

```java
public void outerSelfInvocation(
        UUID customerId) {

    innerTransactionalUpdate(customerId);
}
```

and:

```java
@Transactional
public void innerTransactionalUpdate(
        UUID customerId) {

    ...
    throw new RuntimeException();
}
```

Call the outer method from a controller or test.

Observe behavior.

Then move `innerTransactionalUpdate` into another Spring service bean and repeat.

---

# 40. Preferred Fix for Self-Invocation

Refactor transactional behavior into another Spring-managed service.

Conceptually:

```text
ProfileService
   |
   v
TransactionalWorkerService proxy
   |
   v
transactional method
```

Avoid proxy self-injection unless there is a compelling reason.

---

# 41. Isolation Levels

Isolation controls how concurrent transactions see each other's changes.

Common levels:

```text
DEFAULT
READ_UNCOMMITTED
READ_COMMITTED
REPEATABLE_READ
SERIALIZABLE
```

Example:

```java
@Transactional(
    isolation = Isolation.READ_COMMITTED
)
```

---

# 42. `Isolation.DEFAULT`

Uses the database's configured default.

For PostgreSQL, the usual default is:

```text
READ COMMITTED
```

---

# 43. READ_UNCOMMITTED

Conceptually allows dirty reads.

A dirty read means:

```text
Transaction A reads data
that Transaction B has not committed.
```

PostgreSQL effectively behaves as READ COMMITTED even if READ UNCOMMITTED is requested.

Know the SQL-standard concept.

---

# 44. READ_COMMITTED

Prevents dirty reads.

But repeated reads in the same transaction may see different committed values.

Possible anomaly:

```text
Non-repeatable read
```

---

# 45. REPEATABLE_READ

Repeated reads are based on a stable transaction snapshot according to DB implementation.

Helps prevent:

```text
Non-repeatable reads
```

---

# 46. SERIALIZABLE

Strongest common isolation level.

Transactions behave as though serialized.

Trade-offs:

```text
Lower concurrency
More contention
Possible serialization failures
Potential retries
```

---

# 47. Concurrency Anomalies

Know these:

```text
Dirty Read
Non-repeatable Read
Phantom Read
Lost Update
```

## Dirty Read

Read uncommitted data from another transaction.

## Non-repeatable Read

Read the same row twice and receive different committed values.

## Phantom Read

Repeat a query and observe new/deleted rows matching the predicate.

## Lost Update

Two concurrent operations overwrite each other's changes.

---

# 48. Isolation Summary

```text
READ_UNCOMMITTED
    weakest conceptually

READ_COMMITTED
    prevents dirty reads

REPEATABLE_READ
    stabilizes repeated reads

SERIALIZABLE
    strongest, but most expensive for concurrency
```

Exact details depend on database implementation.

---

# 49. Transaction Timeout

Example:

```java
@Transactional(timeout = 5)
```

This limits transaction duration where supported.

Avoid long-running DB transactions.

---

# 50. Exception Swallowing Problem

Bad:

```java
@Transactional
public void updateProfile(...) {

    try {
        ...
        throw new RuntimeException();
    } catch (Exception ex) {
        log.error("failed", ex);
    }
}
```

If the exception is swallowed and the method returns normally, Spring may commit.

Important rule:

> Rollback-triggering exceptions generally need to propagate beyond the transactional interceptor unless rollback is explicitly marked.

---

# 51. Explicit Rollback-Only

Advanced option:

```java
TransactionAspectSupport
    .currentTransactionStatus()
    .setRollbackOnly();
```

Prefer clear exception propagation where possible.

---

# 52. Checked Exception Experiment

Create:

```java
public class ProfileUpdateCheckedException
        extends Exception {
}
```

Method:

```java
@Transactional
public void checkedFailure(...)
        throws ProfileUpdateCheckedException {

    ...
    throw new ProfileUpdateCheckedException();
}
```

Observe DB state.

Then change to:

```java
@Transactional(
    rollbackFor =
        ProfileUpdateCheckedException.class
)
```

Repeat and compare.

---

# 53. REQUIRED Experiment

Create:

```java
@Service
public class PreferenceService {

    @Transactional(
        propagation = Propagation.REQUIRED
    )
    public void updatePreferences(...) {
        ...
    }
}
```

Call from transactional ProfileService.

Then throw an exception in the outer method.

Expected:

```text
Profile changes rollback
Preference changes rollback
```

---

# 54. REQUIRES_NEW Experiment

Create:

```java
@Service
public class AuditService {

    @Transactional(
        propagation = Propagation.REQUIRES_NEW
    )
    public void recordAttempt(...) {
        ...
    }
}
```

Call from outer transaction.

Then throw in the outer method.

Expected:

```text
Outer changes rollback
Audit remains
```

---

# 55. Transaction Testing Strategy

Use integration tests for real transaction behavior.

Mockito cannot prove:

```text
database rollback
transaction commit
dirty checking
flush behavior
```

For these, test against a real DB behavior.

---

# 56. Integration Test — Runtime Rollback

Example structure:

```java
@SpringBootTest
class ProfileTransactionIntegrationTest {

    @Autowired
    ProfileService profileService;

    @Autowired
    ProfileRepository profileRepository;

    @Autowired
    ProfileAuditRepository auditRepository;

    @Test
    void shouldRollbackEverythingOnRuntimeFailure() {

        UUID id = createProfile();

        assertThrows(
            RuntimeException.class,
            () -> profileService.updateAndFail(id)
        );

        CustomerProfileEntity profile =
                profileRepository
                        .findById(id)
                        .orElseThrow();

        assertNotEquals(
            "SHOULD_ROLLBACK",
            profile.getFirstName()
        );

        assertEquals(
            0,
            auditRepository.count()
        );
    }
}
```

---

# 57. Integration Test — REQUIRES_NEW

Verify both:

```text
outer profile update rolled back
audit record committed
```

This proves separate transaction boundaries.

---

# 58. Integration Test — Dirty Checking

Call:

```java
renameProfileWithoutSave(...)
```

Then reload the entity from the repository.

Verify the new name was persisted.

---

# 59. Read-Only Example

```java
@Transactional(readOnly = true)
public List<ProfileResponse> getProfiles() {
    ...
}
```

Use it for read paths.

Treat it as intent/optimization metadata, not a hard write-prevention feature.

---

# 60. Transaction Logging

For local learning:

```yaml
logging:
  level:
    org.springframework.transaction: TRACE
    org.hibernate.SQL: DEBUG
```

Optionally:

```yaml
logging:
  level:
    org.hibernate.orm.jdbc.bind: TRACE
```

Be careful with bind-value logging because sensitive data can appear in logs.

---

# 61. What to Observe

Look for evidence of:

```text
Creating transaction
Participating in existing transaction
Suspending transaction
Creating new transaction
Committing transaction
Rolling back transaction
SQL UPDATE
SQL INSERT
SQL DELETE
```

For `REQUIRES_NEW`, look for:

```text
Suspend outer
Start inner
Commit inner
Resume outer
```

---

# 62. Common Mistakes

## `@Transactional` on a private helper

Avoid relying on private self-invoked methods as transaction boundaries.

## Self-invocation

`this.transactionalMethod()` may bypass proxy advice.

## Huge transaction

Do not keep a DB transaction open around long-running work.

## Remote call inside a transaction

Avoid:

```text
Begin DB transaction
Call remote service for 20 seconds
Commit DB
```

This holds connections and possibly locks for too long.

## Swallowing exceptions

If a rollback-triggering exception is swallowed, Spring may commit.

---

# 63. Local vs Distributed Transactions

A Spring DB transaction is generally local to:

```text
one process
one transaction manager/resource scope
```

It does not automatically make:

```text
Service A DB
+
Service B DB
+
Kafka
```

one atomic unit.

This leads to later patterns:

```text
Outbox
Saga
Eventual consistency
```

---

# 64. Repository Transaction vs Service Transaction

Repository methods may already have transaction semantics.

But only a service-layer transaction can group several repository operations into one business transaction.

Example:

```text
save profile
save preferences
save audit
```

should be grouped by one service-level `@Transactional` boundary.

---

# 65. Interview Questions

1. What is a transaction?
2. What does ACID mean?
3. What does `@Transactional` do?
4. Where should transaction boundaries usually live?
5. How does Spring implement `@Transactional`?
6. What role does the proxy play?
7. When does commit happen?
8. When does rollback happen?
9. What exceptions roll back by default?
10. Do checked exceptions roll back by default?
11. What does `rollbackFor` do?
12. What does `noRollbackFor` do?
13. What happens if you catch and swallow a RuntimeException?
14. What is dirty checking?
15. Why can a managed entity update without `save()`?
16. What is flush?
17. Flush vs commit?
18. Can SQL execute before commit?
19. Can a flushed update still roll back?
20. What is propagation?
21. What does REQUIRED do?
22. What does REQUIRES_NEW do?
23. REQUIRED vs REQUIRES_NEW?
24. What does SUPPORTS do?
25. What does MANDATORY do?
26. Why can REQUIRES_NEW increase connection usage?
27. Why can self-invocation bypass `@Transactional`?
28. How would you fix self-invocation?
29. What is isolation?
30. What is a dirty read?
31. What is a non-repeatable read?
32. What is a phantom read?
33. What is a lost update?
34. READ_COMMITTED vs REPEATABLE_READ?
35. What does SERIALIZABLE do?
36. Why not always use SERIALIZABLE?
37. Does Spring transaction automatically span microservices?
38. Why are long remote calls inside DB transactions dangerous?

---

# 66. Manual Experiments

## Experiment 1 — RuntimeException Rollback

1. Update profile.
2. Insert audit.
3. Throw RuntimeException.
4. Verify both rolled back.

## Experiment 2 — Checked Exception

1. Update profile.
2. Throw checked exception.
3. Observe default behavior.
4. Add `rollbackFor`.
5. Repeat.

## Experiment 3 — Dirty Checking

1. Load entity.
2. Modify field.
3. Do not call save.
4. Commit.
5. Verify DB update.

## Experiment 4 — Flush Then Rollback

1. Modify entity.
2. Call `flush()`.
3. Observe SQL.
4. Throw RuntimeException.
5. Verify final DB state unchanged.

## Experiment 5 — REQUIRED

1. Outer transaction calls REQUIRED inner service.
2. Inner writes data.
3. Outer fails.
4. Verify everything rolled back.

## Experiment 6 — REQUIRES_NEW

1. Outer modifies profile.
2. Inner REQUIRES_NEW writes audit.
3. Outer fails.
4. Verify profile rolled back.
5. Verify audit remained.

## Experiment 7 — Self Invocation

1. Put `@Transactional` on inner method.
2. Call from same class.
3. Observe.
4. Move to separate bean.
5. Compare.

---

# 67. Coding Checklist

```text
[ ] Audit entity created

[ ] Audit repository created

[ ] Multi-step transactional workflow implemented

[ ] @Transactional added at service boundary

[ ] RuntimeException rollback proven

[ ] Checked-exception default behavior observed

[ ] rollbackFor experiment completed

[ ] Dirty checking demonstrated

[ ] update without save() demonstrated

[ ] flush vs commit experiment completed

[ ] readOnly transaction added

[ ] REQUIRED example implemented

[ ] REQUIRES_NEW example implemented

[ ] REQUIRES_NEW survives outer rollback

[ ] SUPPORTS understood

[ ] MANDATORY understood

[ ] Self-invocation problem reproduced

[ ] Self-invocation fixed by refactoring to another bean

[ ] Isolation levels reviewed

[ ] Dirty/non-repeatable/phantom/lost-update concepts understood

[ ] Transaction logs inspected

[ ] Integration tests added

[ ] docs/day-06.md updated

[ ] Code committed
```

---

# 68. Scorecard

```text
Transactional workflow works                    1/1

Runtime rollback proven                         1/1

Checked exception behavior proven               1/1

Dirty checking demonstrated                     1/1

Flush vs commit understood                      1/1

REQUIRED demonstrated                           1/1

REQUIRES_NEW demonstrated                       1/1

Self-invocation reproduced/fixed                1/1

Isolation concepts explained                    1/1

Tests + docs + commit complete                  1/1

TOTAL                                          /10
```

Target:

```text
8/10 minimum
10/10 preferred
```

---

# 69. Recommended Day Notes

Create:

```text
docs/day-06.md
```

Record:

```text
Transaction use case:
...

Runtime rollback:
...

Checked exception result:
...

Dirty checking SQL:
...

Flush vs commit:
...

REQUIRED:
...

REQUIRES_NEW:
...

Self-invocation:
...

Isolation summary:
...

Still unclear:
...
```

---

# 70. Topics Deliberately Deferred

Do not go deep today into:

```text
Distributed transactions
XA / 2PC
Saga orchestration
Outbox implementation
Optimistic locking
Pessimistic locking
Deadlock handling
Serialization retry strategy
```

Day 06 is focused on:

```text
Local Spring transactions
Rollback
Propagation
Isolation
Dirty checking
Proxy behavior
Self-invocation
```

---

# Day 06 Definition of Done

Day 06 is complete when you can demonstrate:

```text
Controller
   |
   v
Spring Proxy
   |
   +--> Begin Transaction
   |
   v
ProfileService
   |
   +--> Update Profile
   +--> Update Preferences
   +--> Write Audit
   |
   +--> success -> Commit
   |
   +--> failure -> Rollback
```

You must also demonstrate:

```text
REQUIRED
    -> joins the outer transaction

REQUIRES_NEW
    -> creates an independent inner transaction
```

And explain this:

```text
same bean:
outer()
  |
  v
this.innerTransactionalMethod()
  |
  X
transaction advice may be bypassed
```

You should be able to answer, without notes:

```text
How does @Transactional work?
Why does RuntimeException roll back?
Why may checked exceptions commit?
What does rollbackFor do?
What is dirty checking?
Why can an entity update without save()?
What is flush?
Flush vs commit?
REQUIRED vs REQUIRES_NEW?
Why can self-invocation break transaction advice?
What is isolation?
What are dirty/non-repeatable/phantom reads?
Why does a Spring transaction not automatically span microservices?
```

If you can intentionally break the workflow, prove rollback in PostgreSQL, prove REQUIRES_NEW survives outer rollback, reproduce self-invocation behavior, and explain the proxy mechanism, Day 06 is complete.
