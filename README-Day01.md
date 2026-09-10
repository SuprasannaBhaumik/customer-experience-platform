# Day 01 — Spring Boot Foundation, IoC, Dependency Injection, and Layered Design

## Objective

Day 01 establishes the Spring Boot foundation for the entire refresher project.

The goal is to build the first version of the `profile-service` and understand how Spring creates, manages, and injects objects.

By the end of Day 01, the application should support:

```http
POST /profiles
GET  /profiles/{customerId}
```

The focus is deliberately on Spring Core concepts rather than JPA, databases, security, Docker, or Kubernetes.

The main learning goals are:

- Understand what Spring Boot does at startup.
- Understand `@SpringBootApplication`.
- Understand IoC and Dependency Injection.
- Understand what a Spring Bean is.
- Understand `ApplicationContext`.
- Learn Spring stereotype annotations.
- Use constructor injection.
- Understand `@Autowired`.
- Understand `@Qualifier`.
- Understand `@Primary`.
- Understand what happens when multiple beans implement the same interface.
- Build Controller → Service → Repository layering.
- Use an in-memory repository.
- Write unit tests using JUnit and Mockito.

---

# 1. Day 01 Architecture

The first version of the Profile Service should look like this:

```text
HTTP Client
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
InMemoryProfileRepository
    |
    v
ConcurrentHashMap
```

Spring is responsible for creating and wiring the main application objects.

Conceptually:

```text
Spring ApplicationContext
        |
        +--> ProfileController
        |
        +--> ProfileService
        |
        +--> InMemoryProfileRepository
```

Dependencies are injected rather than manually constructed.

---

# 2. Project Setup

Recommended project configuration:

```text
Project: Maven
Language: Java
Java: 21
Spring Boot: 3.5.x

Group:
com.customer

Artifact:
profile-service

Package:
com.customer.profile
```

Recommended dependencies for Day 01:

```text
Spring Web
Validation
Spring Boot Actuator
Spring Boot Test
```

Do not add JPA or PostgreSQL on Day 01.

The goal is to understand Spring itself before introducing persistence frameworks.

---

# 3. Main Application Class

Example:

```java
package com.customer.profile;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ProfileServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                ProfileServiceApplication.class,
                args
        );
    }
}
```

The important annotation is:

```java
@SpringBootApplication
```

---

# 4. `@SpringBootApplication`

`@SpringBootApplication` is the standard annotation used on the main Spring Boot application class.

Conceptually, it combines:

```java
@Configuration
@EnableAutoConfiguration
@ComponentScan
```

These three ideas are extremely important.

---

# 5. `@Configuration`

`@Configuration` identifies a class that can define Spring configuration.

Example:

```java
@Configuration
public class AppConfig {
}
```

A configuration class can contain bean definitions.

Example:

```java
@Configuration
public class AppConfig {

    @Bean
    public PaymentProcessor paymentProcessor() {
        return new CreditCardPaymentProcessor();
    }
}
```

Spring processes the configuration and registers the returned object as a bean.

---

# 6. `@EnableAutoConfiguration`

`@EnableAutoConfiguration` tells Spring Boot to configure the application automatically based on:

```text
Classpath dependencies
Existing beans
Configuration properties
Application type
Conditional configuration
```

For example, when Spring Boot detects the web starter, it can automatically configure:

```text
Embedded Tomcat
Spring MVC infrastructure
DispatcherServlet
Jackson support
HTTP message conversion
```

This is a major reason Spring Boot requires less manual configuration than traditional Spring applications.

Interview idea:

> Spring Boot auto-configuration uses classpath conditions, configuration properties, and existing beans to decide which framework components should be configured automatically.

---

# 7. `@ComponentScan`

`@ComponentScan` tells Spring to search packages for Spring-managed components.

Typical stereotypes include:

```java
@Component
@Service
@Repository
@Controller
@RestController
```

If the main application class is in:

```text
com.customer.profile
```

Spring normally scans:

```text
com.customer.profile
com.customer.profile.controller
com.customer.profile.service
com.customer.profile.repository
com.customer.profile.config
...
```

This is why application packages should generally be underneath the root package containing the main application class.

---

# 8. What Is a Spring Bean?

A Spring Bean is an object whose lifecycle is managed by the Spring IoC container.

Example:

```java
@Service
public class ProfileService {
}
```

Because Spring discovers the class and creates its object, that object becomes a Spring bean.

Conceptually:

```text
Spring scans class
     |
     v
Finds @Service
     |
     v
Creates ProfileService object
     |
     v
Stores/manages it in ApplicationContext
```

A normal object created manually with:

```java
new ProfileService(...)
```

is not automatically a Spring bean.

---

# 9. ApplicationContext

`ApplicationContext` is the central Spring container used in typical Spring Boot applications.

It manages Spring beans and provides services such as:

```text
Bean creation
Dependency injection
Configuration
Lifecycle management
Event publishing
Resource access
Environment/property access
```

Conceptually:

```text
ApplicationContext
    |
    +--> ProfileController bean
    +--> ProfileService bean
    +--> InMemoryProfileRepository bean
```

Interview answer:

> `ApplicationContext` is the Spring IoC container responsible for creating, configuring, wiring, and managing beans.

---

# 10. IoC — Inversion of Control

IoC means that object creation and dependency wiring are controlled by the framework rather than directly by application code.

Without IoC:

```java
ProfileRepository repository =
        new InMemoryProfileRepository();

ProfileService service =
        new ProfileService(repository);
```

The application manually controls object construction.

With Spring:

```java
@Service
public class ProfileService {

    private final ProfileRepository repository;

    public ProfileService(ProfileRepository repository) {
        this.repository = repository;
    }
}
```

Spring decides:

```text
Which implementation to create
When to create it
How to inject it
How long to keep it
```

That is inversion of control.

---

# 11. Dependency Injection

Dependency Injection is the mechanism used to supply an object's dependencies from outside the object.

Example:

```java
@Service
public class ProfileService {

    private final ProfileRepository repository;

    public ProfileService(ProfileRepository repository) {
        this.repository = repository;
    }
}
```

`ProfileService` depends on:

```java
ProfileRepository
```

but it does not create it.

Spring injects the repository implementation.

Conceptually:

```text
Spring finds ProfileService
        |
        v
Reads constructor
        |
        v
Needs ProfileRepository
        |
        v
Searches ApplicationContext
        |
        v
Finds InMemoryProfileRepository
        |
        v
Injects repository
```

---

# 12. Constructor Injection

Constructor injection is the preferred dependency injection style for most Spring services.

Example:

```java
@Service
public class ProfileService {

    private final ProfileRepository repository;

    public ProfileService(ProfileRepository repository) {
        this.repository = repository;
    }
}
```

Advantages:

```text
Dependencies are explicit
Dependencies can be final
Objects are easier to unit test
Missing dependencies fail early
Avoids hidden field injection
Improves immutability
```

---

# 13. `@Autowired`

`@Autowired` tells Spring to inject a matching dependency.

Field injection example:

```java
@Autowired
private ProfileRepository repository;
```

Constructor injection example:

```java
@Autowired
public ProfileService(ProfileRepository repository) {
    this.repository = repository;
}
```

If the class has only one constructor, modern Spring does not require `@Autowired` on that constructor.

So this is sufficient:

```java
public ProfileService(ProfileRepository repository) {
    this.repository = repository;
}
```

For interview preparation, prefer constructor injection.

---

# 14. Field Injection vs Constructor Injection

Field injection:

```java
@Service
public class ProfileService {

    @Autowired
    private ProfileRepository repository;
}
```

Constructor injection:

```java
@Service
public class ProfileService {

    private final ProfileRepository repository;

    public ProfileService(ProfileRepository repository) {
        this.repository = repository;
    }
}
```

Constructor injection is generally preferred because:

```text
Dependencies are visible
Dependencies can be immutable
Unit testing is simpler
The class cannot be instantiated without required dependencies
```

---

# 15. `@Component`

`@Component` is the generic Spring stereotype annotation.

Example:

```java
@Component
public class ProfileMapper {
}
```

Spring discovers the class through component scanning and registers it as a bean.

Use it when no more specific stereotype better describes the role.

---

# 16. `@Service`

`@Service` identifies a service-layer component.

Example:

```java
@Service
public class ProfileService {
}
```

It is specialized from `@Component`.

Its major value is semantic clarity:

```text
This class contains business/application logic.
```

Interview idea:

> `@Service` and `@Component` both create component-scanned beans, but `@Service` communicates the service-layer role more clearly.

---

# 17. `@Repository`

`@Repository` identifies a persistence/data-access component.

Example:

```java
@Repository
public class InMemoryProfileRepository
        implements ProfileRepository {
}
```

Like `@Service`, it is a specialized `@Component`.

In persistence scenarios, `@Repository` also participates in Spring's persistence exception translation semantics.

For Day 01, its main purpose is:

```text
Register InMemoryProfileRepository as a Spring bean
Communicate that the class belongs to the repository layer
```

---

# 18. `@Controller`

`@Controller` identifies a Spring MVC controller.

Example:

```java
@Controller
public class ProfilePageController {
}
```

It is commonly used when controller methods return views.

For REST APIs, use:

```java
@RestController
```

instead.

---

# 19. `@RestController`

`@RestController` identifies a REST controller.

Conceptually:

```text
@RestController
=
@Controller
+
@ResponseBody
```

Example:

```java
@RestController
@RequestMapping("/profiles")
public class ProfileController {
}
```

This annotation is introduced on Day 01 because the Profile Service exposes HTTP APIs.

The deeper Spring MVC behavior is covered on Day 02.

---

# 20. Stereotype Summary

```text
@Component
    Generic Spring-managed component

@Service
    Service/business logic component

@Repository
    Persistence/data-access component

@Controller
    MVC controller

@RestController
    REST API controller
```

All of these participate in component scanning.

---

# 21. `@Bean`

`@Bean` registers the object returned from a method as a Spring bean.

Example:

```java
@Configuration
public class AppConfig {

    @Bean
    public ProfileRepository profileRepository() {
        return new InMemoryProfileRepository();
    }
}
```

This is an alternative to annotating the target class directly with:

```java
@Repository
```

Use `@Bean` commonly when:

```text
You do not control the source class
You want explicit configuration
You need custom construction logic
You are configuring third-party libraries
```

---

# 22. `@Component` vs `@Bean`

`@Component` is placed on the class itself:

```java
@Component
public class MyComponent {
}
```

`@Bean` is placed on a configuration method:

```java
@Configuration
public class Config {

    @Bean
    public MyComponent myComponent() {
        return new MyComponent();
    }
}
```

Interview summary:

```text
@Component
    Component scanning discovers the class.

@Bean
    Configuration explicitly defines the object.
```

---

# 23. `@Qualifier`

`@Qualifier` is used when more than one bean can satisfy the same dependency type.

Suppose:

```java
public interface ProfileRepository {
}
```

has two implementations:

```java
@Repository("memoryProfileRepository")
public class InMemoryProfileRepository
        implements ProfileRepository {
}
```

and:

```java
@Repository("databaseProfileRepository")
public class DatabaseProfileRepository
        implements ProfileRepository {
}
```

Spring now sees:

```text
ProfileRepository
   |
   +--> memoryProfileRepository
   |
   +--> databaseProfileRepository
```

This dependency is ambiguous:

```java
public ProfileService(ProfileRepository repository) {
}
```

Use:

```java
public ProfileService(
        @Qualifier("memoryProfileRepository")
        ProfileRepository repository) {

    this.repository = repository;
}
```

Field-injection form:

```java
@Autowired
@Qualifier("memoryProfileRepository")
private ProfileRepository repository;
```

---

# 24. Default Bean Names

If no explicit bean name is provided:

```java
@Repository
public class InMemoryProfileRepository {
}
```

the default bean name is typically:

```text
inMemoryProfileRepository
```

So:

```java
@Qualifier("inMemoryProfileRepository")
```

can target it.

An explicit name can also be declared:

```java
@Repository("memoryProfileRepository")
```

Then use:

```java
@Qualifier("memoryProfileRepository")
```

---

# 25. `@Primary`

`@Primary` identifies the preferred bean when multiple beans match the same dependency type.

Example:

```java
@Repository
@Primary
public class InMemoryProfileRepository
        implements ProfileRepository {
}
```

Then:

```java
public ProfileService(ProfileRepository repository) {
    this.repository = repository;
}
```

will receive the primary repository.

Use `@Qualifier` when you want the injection point to explicitly choose a bean.

Use `@Primary` when one implementation should act as the default.

---

# 26. `@Qualifier` vs `@Primary`

Example with `@Primary`:

```java
@Repository
@Primary
public class InMemoryProfileRepository
        implements ProfileRepository {
}
```

Injection:

```java
public ProfileService(ProfileRepository repository) {
    this.repository = repository;
}
```

Example with `@Qualifier`:

```java
public ProfileService(
        @Qualifier("memoryProfileRepository")
        ProfileRepository repository) {

    this.repository = repository;
}
```

General distinction:

```text
@Primary
    Defines a default candidate.

@Qualifier
    Explicitly identifies the desired candidate.
```

---

# 27. Multiple Bean Failure Scenario

If Spring finds multiple beans of the same required type and cannot determine which one to inject, application startup fails.

Conceptually:

```text
ProfileRepository
   |
   +--> InMemoryProfileRepository
   |
   +--> AnotherProfileRepository
```

Then:

```java
public ProfileService(ProfileRepository repository) {
}
```

is ambiguous.

The failure commonly results in:

```text
NoUniqueBeanDefinitionException
```

Solutions include:

```java
@Primary
```

or:

```java
@Qualifier
```

---

# 28. Missing Bean Failure Scenario

Suppose:

```java
public class InMemoryProfileRepository
        implements ProfileRepository {
}
```

has no Spring stereotype and is not registered through `@Bean`.

Spring cannot inject it into:

```java
@Service
public class ProfileService {

    public ProfileService(ProfileRepository repository) {
    }
}
```

Startup will fail because there is no bean of type:

```java
ProfileRepository
```

This experiment is useful for understanding what component scanning actually does.

---

# 29. ProfileRepository Interface

Day 01 repository contract:

```java
public interface ProfileRepository {

    CustomerProfile save(CustomerProfile profile);

    Optional<CustomerProfile> findById(UUID customerId);
}
```

Why use an interface?

```text
Service depends on abstraction
Implementation can change
Testing becomes easier
Multiple implementations become possible
Reduces coupling
```

The service should depend on:

```java
ProfileRepository
```

rather than:

```java
InMemoryProfileRepository
```

---

# 30. InMemoryProfileRepository

Example:

```java
@Repository
public class InMemoryProfileRepository
        implements ProfileRepository {

    private final Map<UUID, CustomerProfile> database =
            new ConcurrentHashMap<>();

    @Override
    public CustomerProfile save(CustomerProfile profile) {
        database.put(profile.getCustomerId(), profile);
        return profile;
    }

    @Override
    public Optional<CustomerProfile> findById(UUID customerId) {
        return Optional.ofNullable(
                database.get(customerId)
        );
    }
}
```

Day 01 uses an in-memory implementation deliberately.

The purpose is to learn:

```text
Spring Bean creation
Dependency injection
Layering
Repository abstraction
Unit testing
```

without introducing JPA/Hibernate yet.

---

# 31. Why `ConcurrentHashMap`?

Example:

```java
private final Map<UUID, CustomerProfile> database =
        new ConcurrentHashMap<>();
```

`ConcurrentHashMap` provides thread-safe operations for concurrent access.

A web application may serve multiple HTTP requests concurrently.

Day 01 does not deeply cover Java concurrency, but using a concurrent structure is safer than a plain mutable `HashMap` for this simple in-memory repository.

---

# 32. CustomerProfile Model

Example:

```java
public class CustomerProfile {

    private UUID customerId;
    private String firstName;
    private String lastName;
    private String email;

    public CustomerProfile(
            UUID customerId,
            String firstName,
            String lastName,
            String email) {

        this.customerId = customerId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }
}
```

Important:

There is no:

```java
@Entity
```

on Day 01.

This is a plain application/domain model.

JPA starts later.

---

# 33. Request DTO

Example:

```java
public record CreateProfileRequest(
        String firstName,
        String lastName,
        String email
) {
}
```

A record works well for simple immutable request DTOs.

The controller receives this object from the HTTP request body.

---

# 34. Response DTO

Example:

```java
public record ProfileResponse(
        UUID customerId,
        String firstName,
        String lastName,
        String email
) {
}
```

Keep response DTOs separate from internal models.

Even if fields are identical today, they may evolve independently later.

---

# 35. Why Not Return Internal Models Directly?

Returning `CustomerProfile` directly from the controller may seem simpler:

```java
@GetMapping("/{id}")
public CustomerProfile getProfile(...) {
}
```

But it creates tight coupling between:

```text
Internal model
and
External API contract
```

Problems can include:

```text
Accidental field exposure
Harder API versioning
Persistence details leaking into APIs
Refactoring becoming more difficult
```

Use DTOs instead.

---

# 36. ProfileService

Example:

```java
@Service
public class ProfileService {

    private final ProfileRepository repository;

    public ProfileService(
            ProfileRepository repository) {

        this.repository = repository;
    }

    public ProfileResponse createProfile(
            CreateProfileRequest request) {

        UUID customerId = UUID.randomUUID();

        CustomerProfile profile =
                new CustomerProfile(
                        customerId,
                        request.firstName(),
                        request.lastName(),
                        request.email()
                );

        repository.save(profile);

        return toResponse(profile);
    }

    public ProfileResponse getProfile(
            UUID customerId) {

        CustomerProfile profile =
                repository.findById(customerId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Profile not found"
                                )
                        );

        return toResponse(profile);
    }

    private ProfileResponse toResponse(
            CustomerProfile profile) {

        return new ProfileResponse(
                profile.getCustomerId(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getEmail()
        );
    }
}
```

Responsibilities:

```text
Business/application logic
Interaction with repository
Creation of customer ID
Mapping internal data to response DTO
```

The controller should not create repository objects or implement business logic.

---

# 37. ProfileController

Example:

```java
@RestController
@RequestMapping("/profiles")
public class ProfileController {

    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProfileResponse createProfile(
            @RequestBody CreateProfileRequest request) {

        return service.createProfile(request);
    }

    @GetMapping("/{customerId}")
    public ProfileResponse getProfile(
            @PathVariable UUID customerId) {

        return service.getProfile(customerId);
    }
}
```

Day 01 only requires basic understanding of:

```java
@RestController
@RequestMapping
@PostMapping
@GetMapping
@RequestBody
@PathVariable
@ResponseStatus
```

Spring MVC internals are covered more deeply on Day 02.

---

# 38. Layered Design

Recommended structure:

```text
Controller
   |
   v
Service
   |
   v
Repository
```

Responsibilities:

## Controller

```text
Receives HTTP requests
Maps request data
Returns HTTP responses
Delegates to service
```

## Service

```text
Business/application logic
Coordinates operations
Defines transaction boundaries later
```

## Repository

```text
Data access
Storage/retrieval
Persistence abstraction
```

Avoid:

```text
Controller -> Repository
```

for business flows.

Prefer:

```text
Controller -> Service -> Repository
```

---

# 39. Package Structure

Recommended:

```text
src/main/java/com/customer/profile/
|
+-- ProfileServiceApplication.java
|
+-- controller/
|   +-- ProfileController.java
|
+-- dto/
|   +-- CreateProfileRequest.java
|   +-- ProfileResponse.java
|
+-- model/
|   +-- CustomerProfile.java
|
+-- repository/
|   +-- ProfileRepository.java
|   +-- InMemoryProfileRepository.java
|
+-- service/
    +-- ProfileService.java
```

Keep all packages below:

```text
com.customer.profile
```

so component scanning discovers them naturally.

---

# 40. Unit Testing with JUnit and Mockito

Day 01 service tests should not start the Spring context.

Example:

```java
@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private ProfileRepository repository;

    @InjectMocks
    private ProfileService service;
}
```

These are plain unit tests.

---

# 41. `@ExtendWith(MockitoExtension.class)`

JUnit 5 uses extensions to integrate external behavior.

Example:

```java
@ExtendWith(MockitoExtension.class)
```

enables Mockito annotation processing for the test class.

This allows:

```java
@Mock
@InjectMocks
```

to work.

---

# 42. `@Mock`

Example:

```java
@Mock
private ProfileRepository repository;
```

Mockito creates a test double implementing the required type.

It is not a Spring bean.

It exists only for the unit test.

You control its behavior using:

```java
when(...)
```

---

# 43. `@InjectMocks`

Example:

```java
@InjectMocks
private ProfileService service;
```

Mockito creates the object under test and injects compatible mocks into it.

Conceptually:

```text
Mock ProfileRepository
        |
        v
ProfileService
```

This makes constructor-injected classes easy to test.

---

# 44. Mockito Stubbing

Example:

```java
when(repository.findById(id))
        .thenReturn(Optional.of(profile));
```

This tells the mock:

> When `findById(id)` is called, return this profile.

Another example:

```java
when(repository.save(any(CustomerProfile.class)))
        .thenAnswer(invocation ->
                invocation.getArgument(0));
```

---

# 45. Mockito Verification

Example:

```java
verify(repository, times(1))
        .save(any(CustomerProfile.class));
```

This verifies that the repository's `save` method was invoked exactly once.

Use verification when interaction itself is important to the behavior being tested.

---

# 46. Day 01 Unit Tests

Minimum recommended tests:

```text
shouldCreateProfile
shouldReturnExistingProfile
shouldThrowWhenProfileDoesNotExist
```

Example:

```java
@Test
void shouldCreateProfile() {

    CreateProfileRequest request =
            new CreateProfileRequest(
                    "John",
                    "Doe",
                    "john@example.com"
            );

    when(repository.save(any(CustomerProfile.class)))
            .thenAnswer(invocation ->
                    invocation.getArgument(0));

    ProfileResponse response =
            service.createProfile(request);

    assertNotNull(response.customerId());
    assertEquals(
            "John",
            response.firstName()
    );

    verify(repository)
            .save(any(CustomerProfile.class));
}
```

---

# 47. Mockito Test vs Spring Test

Day 01 service unit test:

```text
JUnit + Mockito
```

does not normally start:

```text
ApplicationContext
Tomcat
DispatcherServlet
Database
```

This makes it:

```text
Fast
Isolated
Focused
```

Later days introduce Spring slice tests and full integration tests.

---

# 48. Actuator Health Check

If Actuator is included, verify:

```http
GET /actuator/health
```

Expected:

```json
{
  "status": "UP"
}
```

This proves the application started successfully.

Actuator becomes much more important later when Docker and Kubernetes health probes are introduced.

---

# 49. Day 01 Experiments

Do not only read annotations. Break the application and observe Spring's behavior.

## Experiment 1 — Remove `@Repository`

Remove:

```java
@Repository
```

from:

```java
InMemoryProfileRepository
```

Restart.

Expected result:

```text
Spring cannot find a ProfileRepository bean.
Application startup fails.
```

Lesson:

```text
Component scanning and bean registration matter.
```

Restore `@Repository`.

---

## Experiment 2 — Add a Second Repository

Create:

```java
@Repository
public class AnotherProfileRepository
        implements ProfileRepository {
    ...
}
```

Now start the application.

Expected:

```text
Spring finds two ProfileRepository beans.
Injection becomes ambiguous.
```

Likely result:

```text
NoUniqueBeanDefinitionException
```

---

## Experiment 3 — Resolve with `@Primary`

Add:

```java
@Primary
```

to one implementation.

Example:

```java
@Repository
@Primary
public class InMemoryProfileRepository
        implements ProfileRepository {
}
```

Restart.

Spring now chooses it as the default.

---

## Experiment 4 — Resolve with `@Qualifier`

Remove `@Primary`.

Name one bean:

```java
@Repository("memoryProfileRepository")
public class InMemoryProfileRepository
        implements ProfileRepository {
}
```

Inject it explicitly:

```java
public ProfileService(
        @Qualifier("memoryProfileRepository")
        ProfileRepository repository) {

    this.repository = repository;
}
```

Restart.

The application should work.

---

# 50. Core Day 01 Interview Questions

You should be able to answer these without opening your notes.

## Spring Boot

1. What does `@SpringBootApplication` do?
2. What is auto-configuration?
3. What is component scanning?
4. Why should packages normally be under the main application package?
5. What does Spring Boot configure automatically for a web application?

## Spring Core

6. What is IoC?
7. What is Dependency Injection?
8. What is a Spring Bean?
9. What is `ApplicationContext`?
10. Who creates `ProfileService`?
11. Who creates `InMemoryProfileRepository`?
12. Why does `ProfileService` not call `new InMemoryProfileRepository()`?

## Stereotypes

13. What is `@Component`?
14. `@Component` vs `@Service`?
15. `@Component` vs `@Repository`?
16. What does `@RestController` mean?
17. Why use semantic stereotype annotations?

## Injection

18. What does `@Autowired` do?
19. Is `@Autowired` required on a single constructor?
20. Why prefer constructor injection?
21. What problems can field injection create?
22. What happens if two beans implement the same interface?
23. What does `@Primary` do?
24. What does `@Qualifier` do?
25. `@Primary` vs `@Qualifier`?

## Bean Registration

26. `@Component` vs `@Bean`?
27. When would you use `@Bean`?
28. What happens when `@Repository` is removed?
29. How does Spring discover beans?

## Architecture

30. Why should service depend on `ProfileRepository` instead of `InMemoryProfileRepository`?
31. Why use an interface?
32. Why have Controller → Service → Repository?
33. Why not expose internal models directly?
34. Why use DTOs?

## Testing

35. What does `@Mock` do?
36. Is a Mockito mock a Spring bean?
37. What does `@InjectMocks` do?
38. What does `@ExtendWith(MockitoExtension.class)` do?
39. Does this unit test start Spring?
40. Why is constructor injection useful for testing?

---

# 51. Day 01 Coding Checklist

```text
[ ] Spring Boot project starts

[ ] /actuator/health returns UP

[ ] POST /profiles works

[ ] GET /profiles/{customerId} works

[ ] ProfileController exists

[ ] ProfileService exists

[ ] ProfileRepository interface exists

[ ] InMemoryProfileRepository exists

[ ] Repository uses ConcurrentHashMap

[ ] CustomerProfile model exists

[ ] CreateProfileRequest DTO exists

[ ] ProfileResponse DTO exists

[ ] Constructor injection is used

[ ] No service manually creates repository implementation

[ ] At least 3 unit tests pass

[ ] @Repository removal experiment completed

[ ] Multiple bean ambiguity experiment completed

[ ] @Primary experiment completed

[ ] @Qualifier experiment completed

[ ] Can explain IoC

[ ] Can explain Dependency Injection

[ ] Can explain ApplicationContext

[ ] Can explain stereotypes

[ ] Can explain @Primary vs @Qualifier
```

---

# 52. Day 01 Scorecard

```text
Application starts                         1/1

POST /profiles works                       1/1

GET /profiles/{id} works                   1/1

Controller -> Service -> Repository        1/1

3+ unit tests pass                         1/1

@Repository removal experiment completed   1/1

@Primary experiment completed              1/1

@Qualifier experiment completed            1/1

Can explain DI/ApplicationContext          1/1

Can explain bean discovery/layering        1/1

TOTAL                                     /10
```

Target:

```text
8/10 minimum
10/10 preferred
```

---

# 53. Topics Deliberately Deferred

Do not add these on Day 01:

```text
Spring Data JPA
Hibernate
PostgreSQL
Docker
Kafka
Redis
JWT
Spring Security
Spring Cloud Gateway
Kubernetes
Resilience4j
MapStruct
Complex validation
Global exception handling
```

Day 01 should stay focused on:

```text
Spring Boot startup
Beans
IoC
Dependency Injection
ApplicationContext
Stereotypes
Constructor injection
@Qualifier
@Primary
Layered architecture
Mockito unit testing
```

---

# 54. Recommended Git Branch and Commit

Suggested branch:

```text
day-01-spring-core-di
```

Suggested commit:

```text
day-01: create profile service and Spring dependency injection foundation
```

---

# Day 01 Definition of Done

Day 01 is complete when you can demonstrate the following dependency graph:

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
InMemoryProfileRepository
```

and explain how Spring creates each bean and injects its dependencies.

You should also be able to explain this startup flow:

```text
ProfileServiceApplication
        |
        v
@SpringBootApplication
        |
        +--> configuration
        +--> auto-configuration
        +--> component scanning
        |
        v
ApplicationContext
        |
        +--> ProfileController
        +--> ProfileService
        +--> InMemoryProfileRepository
```

Finally, you should be able to intentionally create and fix both of these dependency-injection failures:

```text
No matching bean
```

and:

```text
Multiple matching beans
```

using:

```java
@Repository
@Primary
@Qualifier
```

If you can build the service, pass the tests, break bean injection deliberately, fix it, and explain why the fix works, Day 01 is complete.
