# Day 03 — Validation, Exception Handling, and API Error Design

## Objective

Day 03 makes the Profile Service behave like a production REST API when input is invalid, a resource is missing, or a business rule is violated.

By the end of the day, the service should correctly handle:

```text
Blank first name
Blank last name
Invalid email
Missing required fields
Profile not found
Duplicate email
Unexpected server failures
```

Main concepts:

```text
Bean Validation
@Valid
@Validated
@NotNull
@NotEmpty
@NotBlank
@Size
@Email
@Pattern
Custom exceptions
@RestControllerAdvice
@ControllerAdvice
@ExceptionHandler
MethodArgumentNotValidException
HTTP 400 / 404 / 409 / 500
Standard API error contracts
Field-level validation responses
Controller and service error-path tests
```

---

# 1. Day 03 Request Flow

Successful flow:

```text
Client
  |
  v
ProfileController
  |
  v
ProfileService
  |
  v
ProfileRepository
```

Validation failure:

```text
Client
  |
  v
@RequestBody
  |
  v
@Valid
  |
  v
Bean Validation
  |
  v
MethodArgumentNotValidException
  |
  v
@RestControllerAdvice
  |
  v
400 Bad Request
```

Business exception:

```text
ProfileService
   |
   +--> ProfileNotFoundException
   |
   +--> DuplicateEmailException
              |
              v
     @RestControllerAdvice
              |
      +-------+-------+
      |               |
     404             409
```

---

# 2. Why Validation Matters

Without validation, an API can accept invalid data such as:

```json
{
  "firstName": "",
  "lastName": "",
  "email": "abc"
}
```

Validation protects the application boundary and prevents invalid state from entering the service layer.

A good API should communicate:

```text
What failed
Which field failed
What HTTP status applies
What stable error code applies
```

---

# 3. Bean Validation

Spring Boot integrates with Jakarta Bean Validation.

Example:

```java
public record CreateProfileRequest(

        @NotBlank
        @Size(max = 50)
        String firstName,

        @NotBlank
        @Size(max = 50)
        String lastName,

        @NotBlank
        @Email
        String email
) {
}
```

The annotations define input constraints.

---

# 4. `@Valid`

`@Valid` triggers Bean Validation for the request object.

Example:

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public ProfileResponse createProfile(
        @Valid
        @RequestBody
        CreateProfileRequest request) {

    return service.createProfile(request);
}
```

Flow:

```text
JSON
  |
  v
@RequestBody
  |
  v
CreateProfileRequest
  |
  v
@Valid
  |
  +--> valid   -> controller runs
  |
  +--> invalid -> validation exception
```

Without `@Valid`, the DTO validation annotations may not be applied to the request body.

---

# 5. `@Validated`

`@Validated` is Spring-specific and is useful for:

```text
Method-level validation
Validation groups
Service-layer validation
Class-level validation
```

Example:

```java
@Service
@Validated
public class ProfileService {
}
```

Then:

```java
public ProfileResponse getProfile(
        @NotNull UUID customerId) {
    ...
}
```

Interview summary:

```text
@Valid
    Jakarta validation trigger.
    Commonly used on request bodies and nested objects.

@Validated
    Spring-specific.
    Supports method validation and validation groups.
```

---

# 6. Core Validation Annotations

## `@NotNull`

Ensures the value is not `null`.

```text
null -> invalid
""   -> valid
" "  -> valid
```

## `@NotEmpty`

Useful for strings, collections, maps, and arrays.

```text
null  -> invalid
""    -> invalid
"   " -> valid
```

## `@NotBlank`

Best for required text fields.

```text
null   -> invalid
""     -> invalid
"   "  -> invalid
"John" -> valid
```

## `@Size`

Validates string length or collection size.

```java
@Size(min = 2, max = 50)
String firstName;
```

## `@Email`

Validates email-like formatting.

```java
@NotBlank
@Email
String email;
```

## `@Pattern`

Validates against a regex.

```java
@Pattern(
    regexp = "^[A-Za-z]+$",
    message = "must contain only letters"
)
String firstName;
```

Other useful constraints:

```java
@Min
@Max
@Positive
@PositiveOrZero
@Negative
@NegativeOrZero
```

---

# 7. Recommended CreateProfileRequest

```java
public record CreateProfileRequest(

        @NotBlank(message = "firstName is required")
        @Size(
            max = 50,
            message = "firstName must not exceed 50 characters"
        )
        String firstName,

        @NotBlank(message = "lastName is required")
        @Size(
            max = 50,
            message = "lastName must not exceed 50 characters"
        )
        String lastName,

        @NotBlank(message = "email is required")
        @Email(message = "email must be valid")
        String email
) {
}
```

---

# 8. Recommended UpdateProfileRequest

For `PUT`, all required fields should normally be present.

```java
public record UpdateProfileRequest(

        @NotBlank
        @Size(max = 50)
        String firstName,

        @NotBlank
        @Size(max = 50)
        String lastName,

        @NotBlank
        @Email
        String email
) {
}
```

Controller:

```java
@PutMapping("/{customerId}")
public ProfileResponse updateProfile(
        @PathVariable UUID customerId,
        @Valid @RequestBody UpdateProfileRequest request) {

    return service.updateProfile(customerId, request);
}
```

---

# 9. PATCH Validation

PATCH is partial.

Example:

```json
{
  "firstName": "Jane"
}
```

So a simple PATCH DTO should allow omitted fields:

```java
public record PatchProfileRequest(
        String firstName,
        String lastName,
        String email
) {
}
```

Advanced alternatives:

```text
Validation groups
Custom validators
JSON Merge Patch
Command-specific DTOs
```

These are optional for Day 03.

---

# 10. Nested Validation

Example:

```java
public record PreferencesRequest(
        @NotBlank String language
) {
}
```

Parent:

```java
public record CreateProfileRequest(
        @NotBlank String firstName,
        @Valid PreferencesRequest preferences
) {
}
```

The nested `@Valid` triggers validation inside `PreferencesRequest`.

---

# 11. What Happens When Validation Fails?

For invalid request-body validation, Spring MVC commonly raises:

```text
MethodArgumentNotValidException
```

Example invalid request:

```json
{
  "firstName": "",
  "lastName": "Doe",
  "email": "wrong-email"
}
```

Potential errors:

```text
firstName -> firstName is required
email     -> email must be valid
```

Day 03 converts these into a consistent JSON error response.

---

# 12. Custom Exceptions

Avoid:

```java
throw new RuntimeException("Profile not found");
```

Prefer:

```java
ProfileNotFoundException
DuplicateEmailException
```

Benefits:

```text
Clear intent
Better tests
Easy HTTP mapping
Consistent API behavior
Cleaner service code
```

---

# 13. `ProfileNotFoundException`

```java
public class ProfileNotFoundException
        extends RuntimeException {

    public ProfileNotFoundException(UUID customerId) {
        super(
            "Profile not found for customerId: "
            + customerId
        );
    }
}
```

Usage:

```java
CustomerProfile profile =
        repository.findById(customerId)
                .orElseThrow(() ->
                        new ProfileNotFoundException(
                                customerId
                        )
                );
```

Recommended mapping:

```text
404 Not Found
PROFILE_NOT_FOUND
```

---

# 14. `DuplicateEmailException`

```java
public class DuplicateEmailException
        extends RuntimeException {

    public DuplicateEmailException(String email) {
        super(
            "Profile already exists for email: "
            + email
        );
    }
}
```

Usage:

```java
repository.findByEmail(request.email())
        .ifPresent(existing -> {
            throw new DuplicateEmailException(
                    request.email()
            );
        });
```

Recommended mapping:

```text
409 Conflict
DUPLICATE_EMAIL
```

---

# 15. Validation Error vs Business Error

Validation error:

```text
email = "abc"
```

Typical result:

```text
400 Bad Request
```

Business conflict:

```text
email = "existing@example.com"
```

Typical result:

```text
409 Conflict
```

Missing resource:

```http
GET /profiles/{unknown-id}
```

Typical result:

```text
404 Not Found
```

---

# 16. Standard API Error Contract

```java
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path
) {
}
```

Example:

```json
{
  "timestamp": "2026-09-11T04:00:00Z",
  "status": 404,
  "code": "PROFILE_NOT_FOUND",
  "message": "Profile not found for customerId: ...",
  "path": "/profiles/..."
}
```

Benefits:

```text
Consistent errors
Frontend-friendly behavior
Machine-readable error codes
Better debugging
Easier documentation
```

---

# 17. Validation Error Contract

```java
public record ValidationErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, String> errors
) {
}
```

Example:

```json
{
  "timestamp": "2026-09-11T04:00:00Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/profiles",
  "errors": {
    "firstName": "firstName is required",
    "email": "email must be valid"
  }
}
```

---

# 18. `@ControllerAdvice`

Provides cross-cutting controller behavior.

Common uses:

```text
Global exception handling
Binder configuration
Shared MVC behavior
```

Example:

```java
@ControllerAdvice
public class GlobalExceptionHandler {
}
```

---

# 19. `@RestControllerAdvice`

Conceptually:

```text
@RestControllerAdvice
=
@ControllerAdvice
+
@ResponseBody
```

Example:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
}
```

For REST APIs, it is a convenient choice because handler return values are serialized into the response body.

---

# 20. `@ExceptionHandler`

Maps exception types to handler methods.

Example:

```java
@ExceptionHandler(ProfileNotFoundException.class)
public ResponseEntity<ApiError> handleProfileNotFound(
        ProfileNotFoundException ex,
        HttpServletRequest request) {

    ApiError error =
            new ApiError(
                    Instant.now(),
                    404,
                    "PROFILE_NOT_FOUND",
                    ex.getMessage(),
                    request.getRequestURI()
            );

    return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(error);
}
```

---

# 21. Handling Duplicate Email

```java
@ExceptionHandler(DuplicateEmailException.class)
public ResponseEntity<ApiError> handleDuplicateEmail(
        DuplicateEmailException ex,
        HttpServletRequest request) {

    ApiError error =
            new ApiError(
                    Instant.now(),
                    409,
                    "DUPLICATE_EMAIL",
                    ex.getMessage(),
                    request.getRequestURI()
            );

    return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(error);
}
```

---

# 22. Handling `MethodArgumentNotValidException`

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ValidationErrorResponse>
handleValidationFailure(
        MethodArgumentNotValidException ex,
        HttpServletRequest request) {

    Map<String, String> errors =
            new HashMap<>();

    ex.getBindingResult()
            .getFieldErrors()
            .forEach(error ->
                    errors.put(
                            error.getField(),
                            error.getDefaultMessage()
                    )
            );

    ValidationErrorResponse response =
            new ValidationErrorResponse(
                    Instant.now(),
                    400,
                    "VALIDATION_FAILED",
                    "Request validation failed",
                    request.getRequestURI(),
                    errors
            );

    return ResponseEntity
            .badRequest()
            .body(response);
}
```

---

# 23. Why Not Try/Catch in Every Controller?

Avoid repeating:

```java
try {
    return service.getProfile(id);
} catch (ProfileNotFoundException ex) {
    ...
}
```

Problems:

```text
Repeated code
Noisy controllers
Inconsistent responses
Harder maintenance
Harder testing
```

Prefer:

```text
Controller
   |
   v
Service throws exception
   |
   v
@RestControllerAdvice
   |
   v
Standard response
```

---

# 24. HTTP Status Codes

## `400 Bad Request`

Use for invalid client input:

```text
Blank required field
Malformed email
Invalid JSON
Invalid enum
Invalid request structure
```

## `404 Not Found`

Use when the requested resource does not exist.

## `409 Conflict`

Use when a valid request conflicts with current application state.

Example:

```text
Duplicate email
```

## `500 Internal Server Error`

Use for unexpected server-side failures.

Do not use it for normal business scenarios.

---

# 25. Generic Fallback Handler

Optional safety net:

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ApiError> handleUnexpectedException(
        Exception ex,
        HttpServletRequest request) {

    ApiError error =
            new ApiError(
                    Instant.now(),
                    500,
                    "INTERNAL_SERVER_ERROR",
                    "An unexpected error occurred",
                    request.getRequestURI()
            );

    return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(error);
}
```

Do not expose:

```text
Stack traces
SQL statements
Credentials
Internal implementation details
```

to clients.

---

# 26. Request Validation vs Business Validation

Request validation:

```text
Is the request structurally valid?
```

Examples:

```text
firstName is not blank
email is formatted correctly
name length is allowed
```

Business validation:

```text
Is the operation permitted?
```

Examples:

```text
email does not already exist
profile exists before update
state transition is valid
```

Recommended placement:

```text
Request validation -> DTO/controller boundary
Business rules      -> Service layer
```

---

# 27. Stable Error Codes

Recommended codes:

```text
PROFILE_NOT_FOUND
DUPLICATE_EMAIL
VALIDATION_FAILED
INVALID_REQUEST
INTERNAL_SERVER_ERROR
```

Clients should depend on codes, not parse message text.

`code`:

```text
Stable
Machine-readable
Client-facing contract
```

`message`:

```text
Human-readable
May evolve
Useful for troubleshooting/UI
```

---

# 28. Recommended Package Structure

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
+-- exception/
|   +-- ProfileNotFoundException.java
|   +-- DuplicateEmailException.java
|   +-- GlobalExceptionHandler.java
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

---

# 29. Controller Changes

POST:

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public ProfileResponse createProfile(
        @Valid
        @RequestBody
        CreateProfileRequest request) {

    return service.createProfile(request);
}
```

PUT:

```java
@PutMapping("/{customerId}")
public ProfileResponse updateProfile(
        @PathVariable UUID customerId,
        @Valid
        @RequestBody
        UpdateProfileRequest request) {

    return service.updateProfile(
            customerId,
            request
    );
}
```

---

# 30. Service Changes

Replace:

```java
throw new RuntimeException("Profile not found");
```

with:

```java
throw new ProfileNotFoundException(customerId);
```

Before creating a profile:

```java
repository.findByEmail(request.email())
        .ifPresent(existing -> {
            throw new DuplicateEmailException(
                    request.email()
            );
        });
```

---

# 31. Controller Test — Valid Request

```java
mockMvc.perform(
        post("/profiles")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\\"firstName\\":\\"John\\",\\"lastName\\":\\"Doe\\",\\"email\\":\\"john@example.com\\"}"
            )
    )
    .andExpect(status().isCreated());
```

---

# 32. Controller Test — Blank First Name

```java
mockMvc.perform(
        post("/profiles")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\\"firstName\\":\\"\\",\\"lastName\\":\\"Doe\\",\\"email\\":\\"john@example.com\\"}"
            )
    )
    .andExpect(status().isBadRequest())
    .andExpect(
        jsonPath("$.code")
            .value("VALIDATION_FAILED")
    )
    .andExpect(
        jsonPath("$.errors.firstName")
            .exists()
    );
```

---

# 33. Controller Test — Invalid Email

Request:

```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "wrong-email"
}
```

Expected:

```text
400 Bad Request
VALIDATION_FAILED
```

---

# 34. Controller Test — Missing Profile

Mock service:

```java
when(profileService.getProfile(id))
        .thenThrow(
                new ProfileNotFoundException(id)
        );
```

Test:

```java
mockMvc.perform(
        get("/profiles/{id}", id)
    )
    .andExpect(status().isNotFound())
    .andExpect(
        jsonPath("$.code")
            .value("PROFILE_NOT_FOUND")
    );
```

---

# 35. Controller Test — Duplicate Email

Mock service:

```java
when(profileService.createProfile(any()))
        .thenThrow(
                new DuplicateEmailException(
                        "john@example.com"
                )
        );
```

Expected:

```text
409 Conflict
DUPLICATE_EMAIL
```

---

# 36. Service Test — Duplicate Email

```java
@Test
void shouldRejectDuplicateEmail() {

    CreateProfileRequest request =
            new CreateProfileRequest(
                    "John",
                    "Doe",
                    "john@example.com"
            );

    when(repository.findByEmail(
            "john@example.com"))
            .thenReturn(
                    Optional.of(
                            new CustomerProfile(
                                    UUID.randomUUID(),
                                    "John",
                                    "Doe",
                                    "john@example.com"
                            )
                    )
            );

    assertThrows(
            DuplicateEmailException.class,
            () -> service.createProfile(request)
    );

    verify(repository, never())
            .save(any());
}
```

---

# 37. Day 03 Interview Questions

## Validation

1. What is Bean Validation?
2. What does `@Valid` do?
3. What does `@Validated` do?
4. `@Valid` vs `@Validated`?
5. `@NotNull` vs `@NotEmpty` vs `@NotBlank`?
6. What does `@Size` validate?
7. Why combine `@Email` with `@NotBlank`?
8. How does nested validation work?
9. Why is PATCH validation different from PUT?
10. Where should request validation happen?

## Exception Handling

11. What is `@ControllerAdvice`?
12. What is `@RestControllerAdvice`?
13. What is the difference?
14. What does `@ExceptionHandler` do?
15. Why not put try/catch in every controller?
16. Why create custom exceptions?
17. Why should expected failures not become `500`?
18. Why add a generic fallback handler?

## HTTP Semantics

19. When should you return `400`?
20. When should you return `404`?
21. When should you return `409`?
22. When should you return `500`?
23. Why is duplicate email a candidate for `409`?

## API Design

24. Why define a standard error contract?
25. Why use machine-readable error codes?
26. Why should clients not parse message text?
27. Why include timestamp and path?
28. Should stack traces be returned to clients?
29. Where should technical exception details be logged?

## Architecture

30. Request validation vs business validation?
31. Where should duplicate-email checking live?
32. Where should `ProfileNotFoundException` originate?
33. Why should HTTP mapping stay outside the service?

## Testing

34. How do you test invalid payloads?
35. How do you verify field-level errors?
36. How do you test custom exception mappings?
37. Why test controller validation and service rules separately?
38. How do you verify `repository.save()` is not called after a business failure?

---

# 38. Manual Test Cases

## Valid Create

```http
POST /profiles
```

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

## Blank First Name

```json
{
  "firstName": "",
  "lastName": "Mondal",
  "email": "sayantani@example.com"
}
```

Expected:

```text
400 Bad Request
VALIDATION_FAILED
```

## Invalid Email

```json
{
  "firstName": "Sayantani",
  "lastName": "Mondal",
  "email": "abc"
}
```

Expected:

```text
400 Bad Request
```

## Duplicate Email

Create the same email twice.

Expected on the second request:

```text
409 Conflict
DUPLICATE_EMAIL
```

## Missing Profile

```http
GET /profiles/{random-uuid}
```

Expected:

```text
404 Not Found
PROFILE_NOT_FOUND
```

## Invalid PUT

```http
PUT /profiles/{id}
```

```json
{
  "firstName": "",
  "lastName": "",
  "email": "wrong"
}
```

Expected:

```text
400 Bad Request
```

---

# 39. Day 03 Coding Checklist

```text
[ ] @Valid added to POST

[ ] @Valid added to PUT

[ ] CreateProfileRequest has @NotBlank

[ ] CreateProfileRequest has @Size

[ ] CreateProfileRequest has @Email

[ ] UpdateProfileRequest has validation

[ ] ProfileNotFoundException created

[ ] DuplicateEmailException created

[ ] Known RuntimeException usages replaced

[ ] @RestControllerAdvice created

[ ] @ExceptionHandler for ProfileNotFoundException

[ ] @ExceptionHandler for DuplicateEmailException

[ ] @ExceptionHandler for MethodArgumentNotValidException

[ ] 400 returned for invalid request

[ ] 404 returned for missing profile

[ ] 409 returned for duplicate email

[ ] ApiError contract created

[ ] ValidationErrorResponse created

[ ] Blank first-name test passes

[ ] Invalid-email test passes

[ ] Missing-profile test passes

[ ] Duplicate-email controller test passes

[ ] Duplicate-email service test passes

[ ] repository.save() is not called for duplicate email
```

---

# 40. Day 03 Scorecard

```text
Validation annotations added correctly          1/1

@Valid triggers request validation              1/1

Custom exceptions implemented                   1/1

@RestControllerAdvice implemented               1/1

Invalid requests return 400                     1/1

Missing profiles return 404                     1/1

Duplicate email returns 409                     1/1

Field-level error response implemented          1/1

Controller + service tests pass                 1/1

Can explain validation/error architecture       1/1

TOTAL                                          /10
```

Target:

```text
8/10 minimum
10/10 preferred
```

---

# 41. Topics Deliberately Deferred

Do not add these yet:

```text
PostgreSQL
Spring Data JPA
Hibernate
Transactions
Dockerized databases
Redis
Kafka
Spring Security
JWT
Spring Cloud Gateway
Kubernetes
```

Day 03 should remain focused on:

```text
Input validation
Business validation
Custom exceptions
Global exception handling
REST error contracts
HTTP error semantics
Failure-path testing
```

---

# 42. Recommended Git Branch and Commit

Suggested branch:

```text
day-03-validation-exception-handling
```

Suggested commit:

```text
day-03: add request validation and centralized API exception handling
```

---

# Day 03 Definition of Done

Day 03 is complete when valid requests still work and invalid requests fail predictably.

You should be able to demonstrate:

```text
POST /profiles
      |
      v
@RequestBody
      |
      v
@Valid
      |
      +----------------------+
      |                      |
    Valid                 Invalid
      |                      |
      v                      v
ProfileService     Validation exception
      |                      |
      |                      v
      |            @RestControllerAdvice
      |                      |
      |                      v
      |                 400 response
      |
      +--> ProfileNotFoundException
      |
      +--> DuplicateEmailException
                     |
                     v
            @RestControllerAdvice
                     |
             +-------+-------+
             |               |
            404             409
```

You should be able to explain:

- Why `@Valid` is required.
- `@NotNull` vs `@NotEmpty` vs `@NotBlank`.
- `@Valid` vs `@Validated`.
- Why custom exceptions are better than generic `RuntimeException`.
- Why exception handling belongs in `@RestControllerAdvice`.
- Why invalid input maps to `400`.
- Why missing resources map to `404`.
- Why duplicate state may map to `409`.
- Why expected errors should not become `500`.
- Why clients should depend on stable error codes rather than parsing message strings.

If you can intentionally trigger every Day 03 error case, obtain the expected HTTP status and JSON response, and explain how Spring converted the validation failure or thrown exception into the final HTTP response, Day 03 is complete.
