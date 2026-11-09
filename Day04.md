# Day 04 — PostgreSQL, Spring Data JPA, and Persistence Fundamentals

## Objective

Day 04 moves the Profile Service from an in-memory repository to a real relational database.

By the end of the day, the service should persist profiles in PostgreSQL running locally in Docker.

The main goals are:

- Understand why persistence abstraction matters.
- Replace the in-memory repository with Spring Data JPA.
- Run PostgreSQL in Docker.
- Configure Spring Boot datasource properties.
- Create a JPA entity.
- Understand `@Entity`, `@Table`, `@Id`, `@GeneratedValue`, and `@Column`.
- Understand `JpaRepository`.
- Learn derived query methods.
- Learn JPQL with `@Query`.
- Understand `Optional`.
- Understand basic persistence context behavior.
- Understand entity lifecycle at a high level.
- Verify data survives application restarts.
- Write repository tests.

By the end of Day 04, these endpoints should use PostgreSQL:

```http
POST   /profiles
GET    /profiles/{customerId}
GET    /profiles?email={email}
PUT    /profiles/{customerId}
PATCH  /profiles/{customerId}
DELETE /profiles/{customerId}
```

---

# 1. Architecture Change

Day 03:

```text
ProfileController
      |
      v
ProfileService
      |
      v
ProfileRepository
      |
      v
ConcurrentHashMap
```

Day 04:

```text
ProfileController
      |
      v
ProfileService
      |
      v
Spring Data Repository
      |
      v
Hibernate / JPA
      |
      v
PostgreSQL
```

---

# 2. Required Dependencies

Add:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

Keep:

```text
Spring Web
Validation
Actuator
Spring Boot Test
```

---

# 3. JPA vs Hibernate vs Spring Data JPA

## JPA

JPA means:

```text
Jakarta Persistence API
```

It is a specification.

Examples:

```java
@Entity
@Id
EntityManager
@OneToMany
```

## Hibernate

Hibernate is an ORM framework and a common implementation of JPA.

## Spring Data JPA

Spring Data JPA sits above JPA and removes repository boilerplate.

Conceptually:

```text
Application Code
      |
      v
Spring Data JPA
      |
      v
JPA API
      |
      v
Hibernate
      |
      v
JDBC
      |
      v
PostgreSQL
```

Interview summary:

```text
JPA              -> specification
Hibernate        -> implementation
Spring Data JPA  -> repository abstraction on top of JPA
```

---

# 4. ORM

ORM means:

```text
Object Relational Mapping
```

It maps Java objects to relational rows.

For example:

```text
CustomerProfileEntity
```

maps to:

```text
customer_profile
```

---

# 5. Run PostgreSQL in Docker

Example `docker-compose.yml`:

```yaml
services:

  profile-db:
    image: postgres:17
    container_name: profile-db

    environment:
      POSTGRES_DB: profile_db
      POSTGRES_USER: profile_user
      POSTGRES_PASSWORD: profile_password

    ports:
      - "5432:5432"

    volumes:
      - profile-db-data:/var/lib/postgresql/data

volumes:
  profile-db-data:
```

Start:

```bash
docker compose up -d
```

Verify:

```bash
docker ps
```

---

# 6. Docker Concepts Introduced

## Image

Immutable template used to create containers.

Example:

```text
postgres:17
```

## Container

Running instance of an image.

Example:

```text
profile-db
```

## Port Mapping

```yaml
ports:
  - "5432:5432"
```

Meaning:

```text
Host 5432 -> Container 5432
```

## Volume

```yaml
volumes:
  - profile-db-data:/var/lib/postgresql/data
```

A named volume allows database data to survive container recreation.

---

# 7. Configure Spring Datasource

Example:

```yaml
spring:

  datasource:
    url: jdbc:postgresql://localhost:5432/profile_db
    username: profile_user
    password: profile_password

  jpa:
    hibernate:
      ddl-auto: update

    show-sql: true

    properties:
      hibernate:
        format_sql: true
```

For learning:

```text
ddl-auto: update
```

is acceptable.

For production, schema migrations should normally use:

```text
Flyway
Liquibase
```

---

# 8. Datasource and Connection Pool

A datasource represents access to database connections.

Conceptually:

```text
Spring Boot
    |
    v
DataSource
    |
    v
Connection Pool
    |
    v
PostgreSQL
```

Spring Boot commonly uses:

```text
HikariCP
```

A connection pool avoids opening a new physical database connection for every request.

---

# 9. Create the JPA Entity

Create:

```text
entity/CustomerProfileEntity.java
```

Example:

```java
@Entity
@Table(name = "customer_profile")
public class CustomerProfileEntity {

    @Id
    private UUID customerId;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(
        nullable = false,
        unique = true
    )
    private String email;

    protected CustomerProfileEntity() {
    }

    public CustomerProfileEntity(
            UUID customerId,
            String firstName,
            String lastName,
            String email) {

        this.customerId = customerId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
    }

    // getters/setters
}
```

---

# 10. `@Entity`

Marks a class as a JPA entity.

```java
@Entity
public class CustomerProfileEntity {
}
```

Important:

```text
@Entity does not make the object a Spring bean.
```

Entities are managed by JPA/Hibernate.

---

# 11. `@Table`

Maps an entity to a database table.

```java
@Table(name = "customer_profile")
```

Without it, Hibernate derives the table name.

---

# 12. `@Id`

Marks the primary-key field.

```java
@Id
private UUID customerId;
```

Every entity requires an identifier.

---

# 13. `@GeneratedValue`

Used when identifier generation is delegated to the database/provider.

Example:

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;
```

Common strategies:

```text
IDENTITY
SEQUENCE
TABLE
AUTO
```

For this service, UUID can still be generated in application code:

```java
UUID.randomUUID()
```

---

# 14. `@Column`

Controls column mapping.

Example:

```java
@Column(
    name = "email",
    nullable = false,
    unique = true,
    length = 255
)
private String email;
```

Useful options:

```text
name
nullable
unique
length
insertable
updatable
```

Bean Validation and DB constraints are complementary.

---

# 15. No-Args Constructor

JPA entities generally require a no-argument constructor.

```java
protected CustomerProfileEntity() {
}
```

`protected` is a common choice.

---

# 16. Entity vs DTO

Entity:

```text
Persistence model
Managed by JPA/Hibernate
Maps to DB structure
```

DTO:

```text
API contract
Not managed by JPA
Can evolve independently
```

Do not return JPA entities directly from controllers.

---

# 17. Replace In-Memory Repository

Before:

```java
public interface ProfileRepository {

    CustomerProfile save(...);

    Optional<CustomerProfile> findById(...);
}
```

Now:

```java
public interface ProfileRepository
        extends JpaRepository<
                CustomerProfileEntity,
                UUID> {
}
```

Spring Data creates the implementation dynamically.

---

# 18. `JpaRepository`

Provides operations such as:

```java
save(...)
findById(...)
findAll()
deleteById(...)
existsById(...)
count()
flush()
saveAndFlush(...)
```

Important interview question:

> Where is the implementation of my repository interface?

Answer:

> Spring Data creates a proxy-backed implementation at runtime from the repository interface.

---

# 19. Derived Query Methods

Example:

```java
Optional<CustomerProfileEntity>
findByEmailIgnoreCase(String email);
```

Other examples:

```java
List<CustomerProfileEntity>
findByLastName(String lastName);
```

```java
List<CustomerProfileEntity>
findByLastNameContainingIgnoreCase(
        String lastName
);
```

```java
boolean existsByEmailIgnoreCase(
        String email
);
```

---

# 20. Derived Query Keywords

Common keywords:

```text
And
Or
Between
LessThan
GreaterThan
Like
Containing
StartingWith
EndingWith
IgnoreCase
OrderBy
True
False
In
NotIn
IsNull
IsNotNull
```

---

# 21. Duplicate Email Check

Prefer:

```java
boolean existsByEmailIgnoreCase(
        String email
);
```

Service:

```java
if (repository.existsByEmailIgnoreCase(
        request.email())) {

    throw new DuplicateEmailException(
            request.email()
    );
}
```

---

# 22. `@Query`

Use `@Query` when a derived method would become hard to read or when explicit JPQL is clearer.

Example:

```java
@Query(
    "select p from CustomerProfileEntity p " +
    "where lower(p.lastName) like lower(concat('%', :name, '%'))"
)
List<CustomerProfileEntity> searchByLastName(
        @Param("name") String name
);
```

---

# 23. `@Param`

Binds a Java method parameter to a named JPQL parameter.

```java
@Param("name") String name
```

matches:

```text
:name
```

inside JPQL.

---

# 24. JPQL vs SQL

SQL operates on tables and columns.

```sql
SELECT *
FROM customer_profile
WHERE email = ?
```

JPQL operates on entities and entity fields.

```text
select p
from CustomerProfileEntity p
where p.email = :email
```

Summary:

```text
SQL  -> database schema
JPQL -> entity model
```

---

# 25. `Optional`

Repository methods often return:

```java
Optional<CustomerProfileEntity>
```

Preferred:

```java
CustomerProfileEntity entity =
        repository.findById(customerId)
                .orElseThrow(() ->
                        new ProfileNotFoundException(
                                customerId
                        )
                );
```

Avoid blind:

```java
optional.get()
```

---

# 26. Create Profile with JPA

```java
public ProfileResponse createProfile(
        CreateProfileRequest request) {

    if (repository.existsByEmailIgnoreCase(
            request.email())) {

        throw new DuplicateEmailException(
                request.email()
        );
    }

    CustomerProfileEntity entity =
            new CustomerProfileEntity(
                    UUID.randomUUID(),
                    request.firstName(),
                    request.lastName(),
                    request.email()
            );

    CustomerProfileEntity saved =
            repository.save(entity);

    return toResponse(saved);
}
```

---

# 27. Update Profile

```java
public ProfileResponse updateProfile(
        UUID customerId,
        UpdateProfileRequest request) {

    CustomerProfileEntity entity =
            repository.findById(customerId)
                    .orElseThrow(() ->
                            new ProfileNotFoundException(
                                    customerId
                            )
                    );

    entity.setFirstName(request.firstName());
    entity.setLastName(request.lastName());
    entity.setEmail(request.email());

    CustomerProfileEntity saved =
            repository.save(entity);

    return toResponse(saved);
}
```

---

# 28. Delete Profile

```java
public void deleteProfile(UUID customerId) {

    if (!repository.existsById(customerId)) {
        throw new ProfileNotFoundException(
                customerId
        );
    }

    repository.deleteById(customerId);
}
```

---

# 29. Entity-to-DTO Mapping

```java
private ProfileResponse toResponse(
        CustomerProfileEntity entity) {

    return new ProfileResponse(
            entity.getCustomerId(),
            entity.getFirstName(),
            entity.getLastName(),
            entity.getEmail()
    );
}
```

This keeps persistence representation separate from API representation.

---

# 30. Persistence Context

The persistence context is the environment in which JPA tracks entity instances.

Conceptually:

```text
Database Row
    |
    v
Hibernate loads entity
    |
    v
Persistence Context
    |
    v
Managed Entity
```

While managed, Hibernate can track entity changes.

---

# 31. Entity Lifecycle States

Important states:

```text
Transient
Managed
Detached
Removed
```

## Transient

New Java object not managed by JPA.

## Managed

Entity currently tracked by the persistence context.

## Detached

Entity that was previously managed but is no longer associated with the active persistence context.

## Removed

Entity marked for deletion.

---

# 32. Dirty Checking

Hibernate can detect changes to managed entities.

Example concept:

```java
CustomerProfileEntity profile =
        repository.findById(id).orElseThrow();

profile.setFirstName("Jane");
```

Inside an appropriate transaction, Hibernate can detect the change and generate an `UPDATE`.

This is called:

```text
Dirty Checking
```

---

# 33. Flush

Flush synchronizes pending persistence-context changes with the database.

Important:

```text
flush != commit
```

A flush can occur before transaction commit.

---

# 34. `save()` vs `saveAndFlush()`

`save()`:

```text
Persists or merges the entity.
Does not necessarily force immediate synchronization.
```

`saveAndFlush()`:

```text
Saves and forces a flush immediately.
```

Do not use `saveAndFlush()` everywhere.

---

# 35. SQL Logging

Enable:

```yaml
spring:
  jpa:
    show-sql: true

    properties:
      hibernate:
        format_sql: true
```

Observe generated SQL for:

```text
POST
GET
PUT
PATCH
DELETE
```

---

# 36. Inspect PostgreSQL Directly

Open psql:

```bash
docker exec -it profile-db \
  psql -U profile_user -d profile_db
```

List tables:

```text
\\dt
```

Inspect profiles:

```sql
SELECT *
FROM customer_profile;
```

---

# 37. Persistence Test

Create a profile.

Stop Spring Boot.

Restart Spring Boot.

Call:

```http
GET /profiles/{customerId}
```

Expected:

```text
Profile still exists.
```

---

# 38. Docker Volume Experiment

Create data.

Run:

```bash
docker compose down
docker compose up -d
```

Expected:

```text
Data remains.
```

Then:

```bash
docker compose down -v
```

This removes the named volume.

Restarting PostgreSQL should result in a fresh database.

---

# 39. `@DataJpaTest`

Use:

```java
@DataJpaTest
class ProfileRepositoryTest {

    @Autowired
    private ProfileRepository repository;
}
```

`@DataJpaTest` loads a focused persistence test slice.

Typical scope:

```text
JPA entities
Repositories
Persistence configuration
EntityManager-related infrastructure
```

---

# 40. Repository Test Example

```java
@Test
void shouldFindProfileByEmailIgnoringCase() {

    UUID id = UUID.randomUUID();

    CustomerProfileEntity entity =
            new CustomerProfileEntity(
                    id,
                    "John",
                    "Doe",
                    "John@Example.com"
            );

    repository.save(entity);

    Optional<CustomerProfileEntity> result =
            repository.findByEmailIgnoreCase(
                    "john@example.com"
            );

    assertTrue(result.isPresent());

    assertEquals(
            id,
            result.get().getCustomerId()
    );
}
```

---

# 41. Test Derived Search Query

Implement:

```java
List<CustomerProfileEntity>
findByLastNameContainingIgnoreCase(
        String lastName
);
```

Insert:

```text
Mondal
Mondal Sen
Smith
```

Search:

```text
mondal
```

Expected:

```text
2 results
```

---

# 42. Database Unique Constraint

With:

```java
@Column(unique = true)
```

try inserting the same email twice.

Important lesson:

```text
Application duplicate check
+
Database unique constraint
```

is stronger than either one alone.

---

# 43. Why the Service Check Alone Is Not Enough

Imagine two simultaneous requests:

```text
Request A: does email exist? -> no
Request B: does email exist? -> no
```

Then both insert.

A database unique constraint protects against this race.

Interview lesson:

```text
Application validation improves UX.
Database constraints protect integrity.
```

---

# 44. Spring Data Repository Discovery

With:

```java
public interface ProfileRepository
        extends JpaRepository<...> {
}
```

you typically do not need to manually annotate the interface with:

```java
@Repository
```

Spring Data discovers repository interfaces and creates implementations.

---

# 45. Remove In-Memory Persistence

Remove or disable:

```text
InMemoryProfileRepository
```

Day 04 should finish with PostgreSQL as the active persistence store.

---

# 46. Recommended Package Structure

```text
com/customer/profile/
|
+-- controller/
|   +-- ProfileController.java
|
+-- dto/
|   +-- CreateProfileRequest.java
|   +-- UpdateProfileRequest.java
|   +-- PatchProfileRequest.java
|   +-- ProfileResponse.java
|   +-- ApiError.java
|   +-- ValidationErrorResponse.java
|
+-- entity/
|   +-- CustomerProfileEntity.java
|
+-- exception/
|   +-- ProfileNotFoundException.java
|   +-- DuplicateEmailException.java
|   +-- GlobalExceptionHandler.java
|
+-- repository/
|   +-- ProfileRepository.java
|
+-- service/
    +-- ProfileService.java
```

---

# 47. Day 04 Interview Questions

## JPA / Hibernate

1. What is JPA?
2. What is Hibernate?
3. JPA vs Hibernate?
4. What is ORM?
5. What does `@Entity` do?
6. What does `@Table` do?
7. What does `@Id` do?
8. What does `@GeneratedValue` do?
9. What does `@Column` do?
10. Why does a JPA entity need a no-arg constructor?
11. Is an entity a Spring bean?
12. What is a persistence context?
13. What is dirty checking?
14. What is flush?
15. Is flush the same as commit?
16. What are transient, managed, detached, and removed states?

## Spring Data JPA

17. What is `JpaRepository`?
18. Who implements the repository interface?
19. How do derived query methods work?
20. What does `findByEmailIgnoreCase` mean?
21. When would you use `@Query`?
22. What is JPQL?
23. JPQL vs SQL?
24. What does `@Param` do?
25. `save()` vs `saveAndFlush()`?
26. Why use `Optional` for `findById()`?

## Database

27. Why keep a unique DB constraint if the service checks duplicates?
28. Why use connection pooling?
29. What is HikariCP?
30. What happens if the pool is exhausted?
31. Why use Docker volumes?
32. What does `docker compose down -v` do?
33. Why avoid relying on `ddl-auto=update` in production?

## Architecture

34. Why not return JPA entities directly?
35. DTO vs entity?
36. Why keep persistence details out of controllers?
37. Why replace in-memory persistence now?
38. Why retain a repository abstraction?

## Testing

39. What does `@DataJpaTest` load?
40. How does it differ from `@SpringBootTest`?
41. What should repository tests verify?
42. Why test derived queries?
43. Why test database constraints?

---

# 48. Manual Test Cases

## Create

```http
POST /profiles
```

Expected:

```text
201 Created
```

Verify the row in PostgreSQL.

## Get by ID

```http
GET /profiles/{customerId}
```

Expected:

```text
200 OK
```

## Get by Email

```http
GET /profiles?email=sayantani@example.com
```

Expected:

```text
200 OK
```

## Update

```http
PUT /profiles/{customerId}
```

Verify the database row changed.

## Patch

```http
PATCH /profiles/{customerId}
```

Verify only supplied fields changed.

## Delete

```http
DELETE /profiles/{customerId}
```

Expected:

```text
204 No Content
```

Verify the row is gone.

## Restart Application

Create a profile, restart Spring Boot, and retrieve it again.

Expected:

```text
Data remains.
```

## Restart PostgreSQL Container

Run:

```bash
docker compose down
docker compose up -d
```

Expected:

```text
Data remains because the named volume remains.
```

---

# 49. Day 04 Coding Checklist

```text
[ ] Spring Data JPA dependency added

[ ] PostgreSQL JDBC driver added

[ ] PostgreSQL Docker container starts

[ ] Named Docker volume configured

[ ] Datasource configured

[ ] CustomerProfileEntity created

[ ] @Entity added

[ ] @Table added

[ ] @Id added

[ ] @Column constraints added

[ ] No-arg constructor exists

[ ] ProfileRepository extends JpaRepository

[ ] InMemoryProfileRepository removed/disabled

[ ] findByEmailIgnoreCase implemented

[ ] existsByEmailIgnoreCase implemented

[ ] One extra derived query implemented

[ ] One @Query example implemented

[ ] @Param understood/used

[ ] Existing REST endpoints use JPA repository

[ ] Data survives app restart

[ ] Data survives container restart

[ ] SQL logging reviewed

[ ] Table inspected directly in PostgreSQL

[ ] @DataJpaTest added

[ ] Derived query test passes

[ ] Unique constraint behavior observed
```

---

# 50. Day 04 Scorecard

```text
PostgreSQL starts in Docker                    1/1

Spring Boot connects successfully             1/1

JPA entity mapped correctly                    1/1

JpaRepository replaces in-memory storage       1/1

CRUD endpoints persist real data               1/1

Derived query works                            1/1

JPQL @Query works                              1/1

Repository tests pass                          1/1

Volume/persistence experiment completed        1/1

Can explain JPA/Hibernate basics               1/1

TOTAL                                         /10
```

Target:

```text
8/10 minimum
10/10 preferred
```

---

# 51. Topics Deliberately Deferred

Do not go deep into these yet:

```text
@OneToMany
@ManyToOne
@OneToOne
@ManyToMany
FetchType.LAZY
FetchType.EAGER
N+1
@EntityGraph
CascadeType
orphanRemoval
Transaction propagation
Transaction isolation
Flyway
Liquibase
Testcontainers
```

Day 04 should remain focused on:

```text
PostgreSQL
Docker database setup
Basic entity mapping
Spring Data JPA
Derived queries
JPQL
Persistence context fundamentals
Repository testing
```

---

# 52. Recommended Git Branch and Commit

Suggested branch:

```text
day-04-jpa-postgresql
```

Suggested commit:

```text
day-04: replace in-memory profile persistence with PostgreSQL and Spring Data JPA
```

---

# Day 04 Definition of Done

Day 04 is complete when persistence follows:

```text
POST /profiles
      |
      v
ProfileController
      |
      v
ProfileService
      |
      v
ProfileRepository
      |
      v
Spring Data JPA Proxy
      |
      v
Hibernate
      |
      v
JDBC
      |
      v
PostgreSQL
```

You should be able to explain:

```text
What JPA is
What Hibernate is
What Spring Data JPA adds
What @Entity means
What @Id means
What @Column means
How JpaRepository is implemented
How derived queries work
What JPQL is
What persistence context means
What dirty checking means at a high level
Why DTOs and entities remain separate
Why database constraints still matter
Why Docker volumes preserve data
```

If you can create a profile, inspect it directly in PostgreSQL, restart the application, restart PostgreSQL, retrieve the same profile again, run repository tests, and explain the path from repository method to generated SQL, Day 04 is complete.
