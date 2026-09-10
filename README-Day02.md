# Day 02 — Spring MVC and REST API Design

## Objective

Day 02 focuses on turning the basic Profile Service from Day 01 into a proper REST API using Spring MVC.

By the end of this day, the service should support:

```http
POST   /profiles
GET    /profiles/{customerId}
GET    /profiles?email={email}
PUT    /profiles/{customerId}
PATCH  /profiles/{customerId}
DELETE /profiles/{customerId}
```

The main learning goals are:

- Understand how Spring MVC processes an HTTP request.
- Understand the role of `DispatcherServlet`.
- Learn REST controller annotations.
- Learn how path variables, query parameters, headers, and request bodies are mapped.
- Understand JSON serialization and deserialization.
- Use proper HTTP status codes.
- Understand `PUT` vs `PATCH`.
- Understand `ResponseEntity` vs `@ResponseStatus`.
- Add controller tests using `MockMvc`.
- Distinguish controller tests from service unit tests.

---

# 1. Spring MVC Request Flow

A typical request flows like this:

```text
Client
  |
  v
HTTP Request
  |
  v
Embedded Tomcat
  |
  v
DispatcherServlet
  |
  v
HandlerMapping
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
In-memory datastore
```

The response then travels back through the stack.

---

# 2. DispatcherServlet

`DispatcherServlet` is the front controller of Spring MVC.

It receives incoming HTTP requests and coordinates request handling.

Conceptually:

```text
Request
  |
  v
DispatcherServlet
  |
  +--> Find matching controller method
  |
  +--> Resolve arguments
  |
  +--> Invoke controller
  |
  +--> Handle returned value
  |
  +--> Convert Java object to HTTP response
```

Example:

```http
GET /profiles/123
```

Spring determines:

- Which controller handles `/profiles`.
- Which method handles `GET`.
- Which path value maps to `customerId`.
- Whether the path value can be converted to the expected Java type.
- How the returned Java object should be serialized.

---

# 3. `@RestController`

Example:

```java
@RestController
@RequestMapping("/profiles")
public class ProfileController {
}
```

`@RestController` marks a class as a Spring MVC controller whose return values are written directly to the HTTP response body.

Conceptually:

```text
@RestController
=
@Controller
+
@ResponseBody
```

Use it for REST APIs.

---

# 4. `@Controller`

`@Controller` is a Spring MVC stereotype.

Example:

```java
@Controller
public class PageController {
}
```

It is commonly used when returning views/templates.

If a method should return JSON directly, it can additionally use:

```java
@ResponseBody
```

For REST APIs, `@RestController` is usually clearer.

---

# 5. `@RequestMapping`

Defines a common request path.

Example:

```java
@RestController
@RequestMapping("/profiles")
public class ProfileController {
}
```

All endpoints in the controller now begin with:

```text
/profiles
```

Example:

```java
@GetMapping("/{customerId}")
```

becomes:

```http
GET /profiles/{customerId}
```

---

# 6. `@GetMapping`

Handles HTTP `GET` requests.

Example:

```java
@GetMapping("/{customerId}")
public ProfileResponse getProfile(
        @PathVariable UUID customerId) {

    return service.getProfile(customerId);
}
```

Typical uses:

```text
GET /profiles/{id}
GET /profiles?email=...
GET /orders
GET /products
```

GET should normally retrieve data without changing application state.

---

# 7. `@PostMapping`

Handles HTTP `POST` requests.

Example:

```java
@PostMapping
public ResponseEntity<ProfileResponse> createProfile(
        @RequestBody CreateProfileRequest request) {

    ProfileResponse response =
            service.createProfile(request);

    return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response);
}
```

Typical use:

```text
Create a new resource.
```

A successful create commonly returns:

```text
201 Created
```

POST is generally not idempotent.

---

# 8. `@PutMapping`

Handles HTTP `PUT` requests.

Example:

```java
@PutMapping("/{customerId}")
public ProfileResponse updateProfile(
        @PathVariable UUID customerId,
        @RequestBody UpdateProfileRequest request) {

    return service.updateProfile(customerId, request);
}
```

PUT generally represents complete replacement or complete update semantics.

Example:

```http
PUT /profiles/123
```

Body:

```json
{
  "firstName": "Sayantani",
  "lastName": "Mondal",
  "email": "sayantani@example.com"
}
```

Key interview idea:

```text
PUT   -> complete update / replacement semantics
PATCH -> partial update
```

PUT is generally considered idempotent.

---

# 9. `@PatchMapping`

Handles HTTP `PATCH` requests.

PATCH is typically used for partial modification.

Example:

```java
@PatchMapping("/{customerId}")
public ProfileResponse patchProfile(
        @PathVariable UUID customerId,
        @RequestBody PatchProfileRequest request) {

    return service.patchProfile(customerId, request);
}
```

Request:

```http
PATCH /profiles/123
```

Body:

```json
{
  "firstName": "Sayantani"
}
```

Only `firstName` should change.

Example implementation:

```java
if (request.firstName() != null) {
    profile.setFirstName(request.firstName());
}

if (request.lastName() != null) {
    profile.setLastName(request.lastName());
}

if (request.email() != null) {
    profile.setEmail(request.email());
}
```

---

# 10. `@DeleteMapping`

Handles HTTP `DELETE` requests.

Example:

```java
@DeleteMapping("/{customerId}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void deleteProfile(
        @PathVariable UUID customerId) {

    service.deleteProfile(customerId);
}
```

A successful delete with no response body commonly returns:

```text
204 No Content
```

---

# 11. `@RequestBody`

Maps the HTTP request body into a Java object.

Example:

```java
@PostMapping
public ProfileResponse createProfile(
        @RequestBody CreateProfileRequest request) {
    ...
}
```

JSON request:

```json
{
  "firstName": "Sayantani",
  "lastName": "Mondal",
  "email": "sayantani@example.com"
}
```

Spring converts this into:

```java
CreateProfileRequest
```

Flow:

```text
JSON Request
   |
   v
@RequestBody
   |
   v
HttpMessageConverter
   |
   v
Jackson
   |
   v
CreateProfileRequest
```

---

# 12. `@PathVariable`

Maps a value from the URL path to a controller method parameter.

Example:

```java
@GetMapping("/{customerId}")
public ProfileResponse getProfile(
        @PathVariable UUID customerId) {
    ...
}
```

Request:

```http
GET /profiles/550e8400-e29b-41d4-a716-446655440000
```

Spring extracts the path value and converts it to:

```java
UUID customerId
```

Use `@PathVariable` when the value identifies a resource in the URI.

---

# 13. `@RequestParam`

Maps a query-string parameter to a controller method parameter.

Example:

```java
@GetMapping
public ProfileResponse getProfileByEmail(
        @RequestParam String email) {

    return service.getProfileByEmail(email);
}
```

Request:

```http
GET /profiles?email=sayantani@example.com
```

Use `@RequestParam` for:

- Filtering
- Searching
- Pagination
- Sorting
- Optional query parameters

Examples:

```http
GET /products?category=books
GET /orders?status=CREATED
GET /profiles?page=0&size=20
```

---

# 14. `@PathVariable` vs `@RequestParam`

Use:

```java
@PathVariable
```

for:

```http
GET /profiles/123
```

Use:

```java
@RequestParam
```

for:

```http
GET /profiles?email=user@example.com
```

Typical interpretation:

```text
/profiles/123
```

means:

```text
The profile identified by 123
```

while:

```text
/profiles?email=user@example.com
```

means:

```text
Search/filter profiles using an email value
```

---

# 15. `@RequestHeader`

Maps an HTTP request header to a Java parameter.

Example:

```java
@GetMapping("/{customerId}")
public ProfileResponse getProfile(
        @PathVariable UUID customerId,
        @RequestHeader(
            value = "X-Request-Source",
            required = false
        ) String requestSource) {

    return service.getProfile(customerId);
}
```

Headers are commonly used for:

```text
Authorization
Content-Type
Accept
Correlation-Id
Idempotency-Key
Tracing information
```

---

# 16. `@ResponseStatus`

Declares a fixed HTTP response status.

Example:

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public ProfileResponse createProfile(
        @RequestBody CreateProfileRequest request) {

    return service.createProfile(request);
}
```

Returns:

```text
201 Created
```

Another example:

```java
@DeleteMapping("/{customerId}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void deleteProfile(
        @PathVariable UUID customerId) {
    ...
}
```

Returns:

```text
204 No Content
```

Use `@ResponseStatus` when the status is fixed.

---

# 17. `ResponseEntity`

`ResponseEntity` gives explicit control over:

```text
HTTP status
HTTP headers
Response body
```

Example:

```java
@PostMapping
public ResponseEntity<ProfileResponse> createProfile(
        @RequestBody CreateProfileRequest request) {

    ProfileResponse response =
            service.createProfile(request);

    return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response);
}
```

It can also include headers:

```java
return ResponseEntity
        .status(HttpStatus.CREATED)
        .header(
            "X-Customer-Id",
            response.customerId().toString()
        )
        .body(response);
```

Use it when status or headers need dynamic control.

---

# 18. `ResponseEntity` vs `@ResponseStatus`

Use `@ResponseStatus` when:

```text
The status is fixed.
```

Use `ResponseEntity` when:

```text
Status, headers, or body need programmatic control.
```

---

# 19. HTTP Status Codes Used on Day 02

## `200 OK`

Successful request with a response body.

Examples:

```http
GET /profiles/{id}
PUT /profiles/{id}
PATCH /profiles/{id}
```

## `201 Created`

Successful creation.

Example:

```http
POST /profiles
```

## `204 No Content`

Successful operation with no response body.

Example:

```http
DELETE /profiles/{id}
```

## `400 Bad Request`

Invalid client input.

Validation is covered in Day 03.

## `404 Not Found`

Requested resource does not exist.

Centralized exception handling is covered in Day 03.

## `409 Conflict`

The request conflicts with existing resource state.

Example:

```text
Creating another profile with an email that must be unique.
```

---

# 20. DTOs

DTO means:

```text
Data Transfer Object
```

Use separate request and response models.

Example:

```java
public record CreateProfileRequest(
        String firstName,
        String lastName,
        String email
) {
}
```

Response:

```java
public record ProfileResponse(
        UUID customerId,
        String firstName,
        String lastName,
        String email
) {
}
```

Update:

```java
public record UpdateProfileRequest(
        String firstName,
        String lastName,
        String email
) {
}
```

Partial update:

```java
public record PatchProfileRequest(
        String firstName,
        String lastName,
        String email
) {
}
```

Benefits:

- Keep REST contracts separate from internal models.
- Prevent accidental field exposure.
- Allow API-specific validation.
- Make refactoring safer.
- Reduce coupling.

---

# 21. Layer Responsibilities

Recommended flow:

```text
Controller
    |
    | HTTP concerns
    v
Service
    |
    | Business logic
    v
Repository
    |
    | Persistence concerns
    v
Datastore
```

The controller should not contain repository logic.

The service should not contain HTTP-specific concerns.

The repository should not contain business workflow logic.

---

# 22. DTO Mapping

Example:

```java
private ProfileResponse toResponse(
        CustomerProfile profile) {

    return new ProfileResponse(
            profile.getCustomerId(),
            profile.getFirstName(),
            profile.getLastName(),
            profile.getEmail()
    );
}
```

Do not expose your internal model directly just because it currently has the same fields as your API response.

---

# 23. GET Profile by Email

Repository:

```java
Optional<CustomerProfile> findByEmail(String email);
```

In-memory implementation:

```java
@Override
public Optional<CustomerProfile> findByEmail(String email) {

    return database.values()
            .stream()
            .filter(profile ->
                    profile.getEmail()
                           .equalsIgnoreCase(email))
            .findFirst();
}
```

Service:

```java
public ProfileResponse getProfileByEmail(
        String email) {

    CustomerProfile profile =
            repository.findByEmail(email)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Profile not found"
                            )
                    );

    return toResponse(profile);
}
```

Controller:

```java
@GetMapping
public ProfileResponse getProfileByEmail(
        @RequestParam String email) {

    return service.getProfileByEmail(email);
}
```

---

# 24. PUT Profile

Service example:

```java
public ProfileResponse updateProfile(
        UUID customerId,
        UpdateProfileRequest request) {

    CustomerProfile profile =
            repository.findById(customerId)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Profile not found"
                            )
                    );

    profile.setFirstName(request.firstName());
    profile.setLastName(request.lastName());
    profile.setEmail(request.email());

    repository.save(profile);

    return toResponse(profile);
}
```

Controller:

```java
@PutMapping("/{customerId}")
public ProfileResponse updateProfile(
        @PathVariable UUID customerId,
        @RequestBody UpdateProfileRequest request) {

    return service.updateProfile(
            customerId,
            request
    );
}
```

---

# 25. PATCH Profile

Service example:

```java
public ProfileResponse patchProfile(
        UUID customerId,
        PatchProfileRequest request) {

    CustomerProfile profile =
            repository.findById(customerId)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Profile not found"
                            )
                    );

    if (request.firstName() != null) {
        profile.setFirstName(request.firstName());
    }

    if (request.lastName() != null) {
        profile.setLastName(request.lastName());
    }

    if (request.email() != null) {
        profile.setEmail(request.email());
    }

    repository.save(profile);

    return toResponse(profile);
}
```

---

# 26. DELETE Profile

Repository:

```java
void deleteById(UUID customerId);
```

Implementation:

```java
@Override
public void deleteById(UUID customerId) {
    database.remove(customerId);
}
```

Service:

```java
public void deleteProfile(UUID customerId) {

    repository.findById(customerId)
            .orElseThrow(() ->
                    new RuntimeException(
                            "Profile not found"
                    )
            );

    repository.deleteById(customerId);
}
```

Controller:

```java
@DeleteMapping("/{customerId}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void deleteProfile(
        @PathVariable UUID customerId) {

    service.deleteProfile(customerId);
}
```

---

# 27. Jackson

Jackson is commonly used by Spring Boot for JSON serialization and deserialization.

It handles:

```text
JSON -> Java
```

and:

```text
Java -> JSON
```

Example:

```json
{
  "firstName": "Sayantani",
  "lastName": "Mondal",
  "email": "sayantani@example.com"
}
```

becomes:

```java
CreateProfileRequest
```

A:

```java
ProfileResponse
```

becomes JSON in the HTTP response.

---

# 28. `HttpMessageConverter`

Spring MVC uses `HttpMessageConverter` implementations to convert HTTP request and response bodies.

For JSON:

```text
HTTP JSON
   |
   v
HttpMessageConverter
   |
   v
Jackson
   |
   v
Java Object
```

And:

```text
Java Object
   |
   v
HttpMessageConverter
   |
   v
Jackson
   |
   v
HTTP JSON
```

Interview answer:

> Spring MVC uses `HttpMessageConverter` implementations for request and response body conversion. With Jackson on the classpath, Spring Boot auto-configures JSON serialization and deserialization support.

---

# 29. `HandlerMapping`

`HandlerMapping` determines which controller handler should process an incoming request.

For:

```http
GET /profiles/123
```

Spring can match:

```java
@RequestMapping("/profiles")
```

plus:

```java
@GetMapping("/{customerId}")
```

to locate the controller method.

---

# 30. Service Unit Testing

Typical setup:

```java
@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private ProfileRepository repository;

    @InjectMocks
    private ProfileService service;
}
```

`@Mock` is a Mockito annotation.

It creates a mock object, not a Spring bean.

`@InjectMocks` asks Mockito to create the class under test and inject its mocked collaborators.

This type of test typically does not start:

```text
Spring ApplicationContext
Tomcat
DispatcherServlet
Database
```

---

# 31. `@WebMvcTest`

`@WebMvcTest` is used for focused Spring MVC testing.

Example:

```java
@WebMvcTest(ProfileController.class)
class ProfileControllerTest {
}
```

It focuses on the web layer rather than loading the entire application.

Use it to test:

```text
URL mappings
HTTP methods
HTTP status codes
request deserialization
response serialization
controller behavior
JSON output
```

---

# 32. `MockMvc`

`MockMvc` lets you exercise Spring MVC request handling without starting a real HTTP server.

Example:

```java
@Autowired
private MockMvc mockMvc;
```

Representative test:

```java
mockMvc.perform(
        post("/profiles")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  "firstName": "John",
                  "lastName": "Doe",
                  "email": "john@example.com"
                }
                """
            )
    )
    .andExpect(status().isCreated())
    .andExpect(
        jsonPath("$.firstName")
            .value("John")
    );
```

This verifies:

```text
Controller mapping
Request JSON handling
Response JSON handling
HTTP status
```

---

# 33. Service Unit Test vs MVC Slice Test

## Service Unit Test

Tools:

```text
JUnit
Mockito
@Mock
@InjectMocks
```

Purpose:

```text
Test business logic in isolation.
```

Usually no Spring context is loaded.

## MVC Slice Test

Tools:

```text
@WebMvcTest
MockMvc
```

Purpose:

```text
Test Spring MVC/controller behavior.
```

Spring loads the relevant web infrastructure rather than the full application.

---

# 34. API Contract for Day 02

## Create Profile

```http
POST /profiles
```

Request:

```json
{
  "firstName": "Sayantani",
  "lastName": "Mondal",
  "email": "sayantani@example.com"
}
```

Expected:

```text
201 Created
```

## Get Profile by ID

```http
GET /profiles/{customerId}
```

Expected:

```text
200 OK
```

## Get Profile by Email

```http
GET /profiles?email=sayantani@example.com
```

Expected:

```text
200 OK
```

## Full Update

```http
PUT /profiles/{customerId}
```

Body:

```json
{
  "firstName": "Sayantani",
  "lastName": "Mondal Sen",
  "email": "sayantani@example.com"
}
```

Expected:

```text
200 OK
```

## Partial Update

```http
PATCH /profiles/{customerId}
```

Body:

```json
{
  "lastName": "Sen"
}
```

Expected:

```text
200 OK
```

Only supplied fields should change.

## Delete Profile

```http
DELETE /profiles/{customerId}
```

Expected:

```text
204 No Content
```

---

# 35. Day 02 Interview Questions

You should be able to answer these without opening the code.

## Spring MVC

1. What is `DispatcherServlet`?
2. What does `HandlerMapping` do?
3. How does Spring choose a controller method?
4. What does `@RestController` do?
5. `@Controller` vs `@RestController`?
6. What does `@RequestMapping` do?
7. `@GetMapping` vs generic `@RequestMapping`?
8. What does `@RequestBody` do?
9. How does JSON become Java?
10. How does Java become JSON?
11. What is an `HttpMessageConverter`?
12. What role does Jackson play?

## REST

13. POST vs PUT?
14. PUT vs PATCH?
15. Is PUT idempotent?
16. Is POST normally idempotent?
17. Why return `204 No Content` for DELETE?
18. When should `201 Created` be returned?
19. When should `404 Not Found` be returned?
20. When is `409 Conflict` appropriate?

## Request Mapping

21. `@PathVariable` vs `@RequestParam`?
22. When would you use `@RequestHeader`?
23. How would you pass pagination parameters?
24. How would you represent a resource ID?

## Response Handling

25. `ResponseEntity` vs `@ResponseStatus`?
26. When should you use `ResponseEntity`?
27. How can you add custom response headers?

## Architecture

28. Why should a controller not call a repository directly?
29. Why use a service layer?
30. Why use DTOs?
31. Why not expose internal/domain objects directly?

## Testing

32. What does `@WebMvcTest` test?
33. What is `MockMvc`?
34. Does a Mockito unit test start Spring?
35. Unit test vs MVC slice test?
36. Why should controller and service tests be separated?

---

# 36. Day 02 Coding Checklist

```text
[ ] POST /profiles works

[ ] GET /profiles/{customerId} works

[ ] GET /profiles?email=... works

[ ] PUT /profiles/{customerId} works

[ ] PATCH /profiles/{customerId} works

[ ] PATCH modifies only supplied fields

[ ] DELETE /profiles/{customerId} works

[ ] DELETE returns 204

[ ] DTOs are separate from CustomerProfile

[ ] Controller calls Service

[ ] Service calls Repository

[ ] At least 4 service tests pass

[ ] At least 1 MockMvc controller test passes

[ ] Can explain DispatcherServlet

[ ] Can explain HandlerMapping

[ ] Can explain HttpMessageConverter

[ ] Can explain Jackson

[ ] Can explain PUT vs PATCH

[ ] Can explain @PathVariable vs @RequestParam

[ ] Can explain ResponseEntity vs @ResponseStatus
```

---

# 37. Day 02 Scorecard

```text
Application starts                         1/1

POST + GET work                            1/1

GET by email works                         1/1

PUT works                                  1/1

PATCH works correctly                      1/1

DELETE returns 204                         1/1

Service tests pass                         1/1

MVC/controller test passes                 1/1

Can explain DispatcherServlet flow         1/1

Can explain REST/MVC annotations            1/1

TOTAL                                     /10
```

Target:

```text
8/10 minimum
10/10 preferred
```

---

# 38. Topics Deliberately Deferred to Day 03

Do not add these yet unless required:

```text
@Valid
@Validated
@NotBlank
@NotNull
@Email

@RestControllerAdvice
@ControllerAdvice
@ExceptionHandler

Custom API error contract
Field-level validation errors
Proper 400/404/409 exception mapping
```

Day 02 should remain focused on:

```text
Spring MVC
REST semantics
Request mapping
Response handling
DTOs
Controller testing
```

---

# 39. Recommended Git Branch and Commit

Suggested branch:

```text
day-02-spring-mvc-rest
```

Suggested commit:

```text
day-02: implement Spring MVC profile REST operations and controller tests
```

---

# Day 02 Definition of Done

Day 02 is complete when you can demonstrate this full flow:

```text
POST /profiles
      |
      v
DispatcherServlet
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
In-memory datastore
```

You should also be able to explain:

- Why each controller annotation exists.
- How Spring maps an HTTP request to a controller method.
- How JSON is converted into Java and back into JSON.
- Why each HTTP status code is appropriate.
- Why PUT and PATCH have different semantics.
- What `MockMvc` verifies.
- How controller tests differ from service unit tests.
