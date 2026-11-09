# Day 08 — Spring Profiles, Externalized Configuration, Environment Variables, and Typed Properties

## Recommended Git Branch

`day-08-configuration-profiles`

## Objective

Day 08 focuses on configuration management. The goal is to run the same Spring Boot code in local, test, and Docker environments without changing Java code.

You will use:

- `application.yml`
- `application-local.yml`
- `application-test.yml`
- `application-docker.yml`
- `SPRING_PROFILES_ACTIVE`
- environment variables
- `@ConfigurationProperties`
- `@ConfigurationPropertiesScan`
- `@EnableConfigurationProperties`
- `@Profile`
- `@ActiveProfiles`
- configuration validation

By the end of the day, Profile Service, Favorites Service, and API Gateway should all load environment-specific values cleanly.


---

## 1. Why Externalized Configuration Matters

Bad:

```java
String dbUrl = "jdbc:postgresql://localhost:5433/profile_db";
String profileUrl = "http://localhost:8081";
```

This couples Java code to one environment.

Desired model:

```text
Java code
   |
   v
Configuration abstraction
   |
   +--> local values
   +--> test values
   +--> Docker values
```

The application should not care whether PostgreSQL is on your Mac, in Docker, or later in Kubernetes.


---

## 2. Two-Hour Agenda

```text
00–10 min   Run Day 07 stack and verify all services
10–20 min   Understand Spring configuration sources
20–35 min   Split local/docker/test configuration
35–50 min   Introduce environment variables
50–65 min   Replace scattered @Value usage
65–80 min   Create @ConfigurationProperties
80–90 min   Add configuration validation
90–100 min  Use @Profile for environment-specific beans
100–110 min Test property precedence
110–118 min Verify Docker Compose overrides
118–120 min Interview review + scorecard
```


---

## 3. Base `application.yml`

Keep only values common to all environments.

Profile Service:

```yaml
spring:
  application:
    name: profile-service

server:
  port: 8081

management:
  endpoints:
    web:
      exposure:
        include:
          - health
          - info
```

Do not put environment-specific database hostnames here.


---

## 4. `application-local.yml`

Your Spring app runs directly on your Mac, while PostgreSQL runs in Docker.

If Day 07 maps:

```text
localhost:5433 -> profile-db:5432
localhost:5434 -> favorites-db:5432
```

Profile Service local config:

```yaml
spring:
  datasource:
    url: ${PROFILE_DB_URL:jdbc:postgresql://localhost:5433/profile_db}
    username: ${PROFILE_DB_USERNAME:profile_user}
    password: ${PROFILE_DB_PASSWORD:profile_password}

  jpa:
    hibernate:
      ddl-auto: update

    show-sql: true
```

Favorites local config:

```yaml
spring:
  datasource:
    url: ${FAVORITES_DB_URL:jdbc:postgresql://localhost:5434/favorites_db}
    username: ${FAVORITES_DB_USERNAME:favorites_user}
    password: ${FAVORITES_DB_PASSWORD:favorites_password}
```


---

## 5. `application-docker.yml`

When the Spring service itself runs inside Docker Compose, use Docker DNS names instead of host ports.

Profile Service:

```yaml
spring:
  datasource:
    url: ${PROFILE_DB_URL:jdbc:postgresql://profile-db:5432/profile_db}
    username: ${PROFILE_DB_USERNAME:profile_user}
    password: ${PROFILE_DB_PASSWORD:profile_password}
```

Favorites Service:

```yaml
spring:
  datasource:
    url: ${FAVORITES_DB_URL:jdbc:postgresql://favorites-db:5432/favorites_db}
    username: ${FAVORITES_DB_USERNAME:favorites_user}
    password: ${FAVORITES_DB_PASSWORD:favorites_password}
```

Key distinction:

```text
Local application -> localhost:5433 / localhost:5434
Docker application -> profile-db:5432 / favorites-db:5432
```


---

## 6. API Gateway Environment-Specific URLs

Local:

```yaml
app:
  services:
    profile:
      base-url: http://localhost:8081

    favorites:
      base-url: http://localhost:8082
```

Docker:

```yaml
app:
  services:
    profile:
      base-url: http://profile-service:8081

    favorites:
      base-url: http://favorites-service:8082
```

This removes Docker topology from Java code.


---

## 7. Activating Profiles

Environment variable:

```bash
export SPRING_PROFILES_ACTIVE=local
```

Run:

```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

Docker Compose:

```yaml
environment:
  SPRING_PROFILES_ACTIVE: docker
```

Command line:

```bash
java -jar app.jar --spring.profiles.active=local
```

Tests:

```java
@ActiveProfiles("test")
```


---

## 8. How Spring Loads Profile Files

With:

```text
spring.profiles.active=local
```

Spring conceptually combines:

```text
application.yml
+
application-local.yml
```

With:

```text
docker
```

it combines:

```text
application.yml
+
application-docker.yml
```

Keep shared settings in `application.yml`; override only differences.


---

## 9. `@Profile`

`@Profile` controls bean registration.

Example:

```java
@Component
@Profile("local")
public class LocalStartupLogger {

    public LocalStartupLogger() {
        System.out.println("LOCAL profile active");
    }
}
```

Docker-specific bean:

```java
@Component
@Profile("docker")
public class DockerStartupLogger {

    public DockerStartupLogger() {
        System.out.println("DOCKER profile active");
    }
}
```

Use `@Profile` when bean behavior differs, not just because one URL changes.


---

## 10. Environment Variables

Example YAML:

```yaml
spring:
  datasource:
    url: ${PROFILE_DB_URL}
    username: ${PROFILE_DB_USERNAME}
    password: ${PROFILE_DB_PASSWORD}
```

Shell:

```bash
export PROFILE_DB_URL=jdbc:postgresql://localhost:5433/profile_db
export PROFILE_DB_USERNAME=profile_user
export PROFILE_DB_PASSWORD=profile_password
```

A default value can be supplied:

```yaml
url: ${PROFILE_DB_URL:jdbc:postgresql://localhost:5433/profile_db}
```

For real secrets, avoid insecure defaults.


---

## 11. Docker Compose Environment Variables

Profile Service:

```yaml
profile-service:
  environment:
    SPRING_PROFILES_ACTIVE: docker
    PROFILE_DB_URL: jdbc:postgresql://profile-db:5432/profile_db
    PROFILE_DB_USERNAME: profile_user
    PROFILE_DB_PASSWORD: profile_password
```

Favorites Service:

```yaml
favorites-service:
  environment:
    SPRING_PROFILES_ACTIVE: docker
    FAVORITES_DB_URL: jdbc:postgresql://favorites-db:5432/favorites_db
    FAVORITES_DB_USERNAME: favorites_user
    FAVORITES_DB_PASSWORD: favorites_password
```

Gateway:

```yaml
api-gateway:
  environment:
    SPRING_PROFILES_ACTIVE: docker
    APP_SERVICES_PROFILE_BASE_URL: http://profile-service:8081
    APP_SERVICES_FAVORITES_BASE_URL: http://favorites-service:8082
```


---

## 12. `@Value`

Example:

```java
@Value("${app.services.profile.base-url}")
private String profileBaseUrl;
```

This works well for one or two simple values.

But if a class has many related values:

```java
@Value("${app.services.profile.base-url}")
String baseUrl;

@Value("${app.services.profile.connect-timeout}")
Duration connectTimeout;

@Value("${app.services.profile.read-timeout}")
Duration readTimeout;
```

prefer `@ConfigurationProperties`.


---

## 13. `@ConfigurationProperties`

YAML:

```yaml
app:
  services:
    profile:
      base-url: http://localhost:8081
      connect-timeout: 2s
      read-timeout: 5s
```

Java:

```java
@ConfigurationProperties(
    prefix = "app.services.profile"
)
public record ProfileServiceProperties(
        URI baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
}
```

Benefits:

```text
Type safety
Grouped configuration
Cleaner injection
Validation
Better testability
IDE metadata/support
```


---

## 14. Registering Configuration Properties

Option A:

```java
@SpringBootApplication
@ConfigurationPropertiesScan
public class ApiGatewayApplication {
}
```

Option B:

```java
@Configuration
@EnableConfigurationProperties(
    ProfileServiceProperties.class
)
public class GatewayConfiguration {
}
```

For a project with several property classes, `@ConfigurationPropertiesScan` is convenient.


---

## 15. Nested Typed Properties

YAML:

```yaml
app:
  services:
    profile:
      base-url: http://localhost:8081

    favorites:
      base-url: http://localhost:8082
```

Java:

```java
@ConfigurationProperties(prefix = "app.services")
public record ServiceProperties(
        ServiceEndpoint profile,
        ServiceEndpoint favorites
) {
    public record ServiceEndpoint(
            URI baseUrl
    ) {
    }
}
```


---

## 16. Use Typed Properties in Gateway Routes

Instead of:

```java
.uri("http://localhost:8081")
```

use configuration:

```java
@Configuration
public class GatewayRoutes {

    @Bean
    RouteLocator routes(
            RouteLocatorBuilder builder,
            ServiceProperties properties) {

        return builder.routes()

                .route(
                    "profile-service",
                    r -> r
                        .path("/api/profiles/**")
                        .filters(f -> f.stripPrefix(1))
                        .uri(
                            properties
                                .profile()
                                .baseUrl()
                                .toString()
                        )
                )

                .route(
                    "favorites-service",
                    r -> r
                        .path("/api/customers/*/favorites/**")
                        .filters(f -> f.stripPrefix(1))
                        .uri(
                            properties
                                .favorites()
                                .baseUrl()
                                .toString()
                        )
                )

                .build();
    }
}
```


---

## 17. Configuration Validation

Example:

```java
@Validated
@ConfigurationProperties(
    prefix = "app.services.profile"
)
public record ProfileServiceProperties(

        @NotNull
        URI baseUrl,

        @NotNull
        Duration connectTimeout,

        @NotNull
        Duration readTimeout
) {
}
```

If required configuration is missing, startup should fail.

This is desirable because the application fails fast instead of discovering bad config on the first request.


---

## 18. Fail-Fast Experiment

Temporarily remove:

```yaml
app:
  services:
    profile:
      base-url: ...
```

Start Gateway.

Expected:

```text
ApplicationContext startup failure
```

Restore the property afterward.


---

## 19. Property Precedence

Configuration can come from multiple sources.

Practical mental model:

```text
packaged defaults
        |
profile-specific config
        |
environment overrides
        |
command-line overrides
```

Important lesson:

> External values can override values packaged inside the application.

Do not depend on accidental precedence; document overrides clearly.


---

## 20. Command-Line Override Experiment

Base config:

```yaml
server:
  port: 8081
```

Run:

```bash
java -jar target/profile-service.jar   --spring.profiles.active=local   --server.port=9091
```

Expected:

```text
Profile Service runs on 9091
```


---

## 21. Relaxed Binding

Spring Boot supports relaxed property binding.

For example:

```text
app.services.profile.base-url
```

can be supplied as:

```text
APP_SERVICES_PROFILE_BASE_URL
```

This makes environment variables convenient for Docker and CI/CD systems.


---

## 22. Test Profile

Create:

```text
application-test.yml
```

Example:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: create-drop

logging:
  level:
    org.hibernate.SQL: DEBUG
```

Use:

```java
@SpringBootTest
@ActiveProfiles("test")
class ProfileIntegrationTest {
}
```

This prevents tests from accidentally using local or Docker config.


---

## 23. Configuration Binding Test

Example:

```java
@SpringBootTest
@ActiveProfiles("test")
class ServicePropertiesTest {

    @Autowired
    ServiceProperties properties;

    @Test
    void shouldBindServiceUrls() {

        assertNotNull(
            properties.profile().baseUrl()
        );

        assertNotNull(
            properties.favorites().baseUrl()
        );
    }
}
```


---

## 24. `.env` for Local Docker Compose

Example `.env`:

```text
PROFILE_DB_USERNAME=profile_user
PROFILE_DB_PASSWORD=profile_password

FAVORITES_DB_USERNAME=favorites_user
FAVORITES_DB_PASSWORD=favorites_password
```

Compose can reference:

```yaml
environment:
  PROFILE_DB_USERNAME: ${PROFILE_DB_USERNAME}
  PROFILE_DB_PASSWORD: ${PROFILE_DB_PASSWORD}
```

Add secret-bearing local env files to `.gitignore`.


---

## 25. Suggested `.gitignore`

```text
.env
.env.local
*.secret
```

Do not commit:

```text
production DB password
API keys
OAuth secrets
private keys
```


---

## 26. Common Mistakes

### Wrong active profile

Symptom:

```text
Application in Docker tries localhost.
```

Cause:

```text
local profile active instead of docker
```

### Duplicate config everywhere

Keep common settings in `application.yml`; override only differences.

### Too much `@Value`

If several related values exist, group them into `@ConfigurationProperties`.

### Using `@Profile` for every URL

Use profiles for different bean behavior; use property overrides for simple value changes.

### Reading `System.getenv()` throughout business code

Keep environment access inside Spring's configuration system.


---

## 27. Local Verification

Start only Docker databases:

```bash
docker compose up -d profile-db favorites-db
```

Run Profile Service locally:

```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

Expected DB:

```text
localhost:5433
```

Run Favorites locally with `local` profile.

Expected DB:

```text
localhost:5434
```


---

## 28. Docker Verification

Run:

```bash
docker compose up --build
```

Verify:

```text
Profile Service -> profile-db:5432
Favorites Service -> favorites-db:5432
Gateway -> profile-service:8081
Gateway -> favorites-service:8082
```

Then test through:

```text
http://localhost:8080
```


---

## 29. Environment Override Experiment

Run with an intentionally wrong DB username:

```bash
PROFILE_DB_USERNAME=wrong_user SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

Expected:

```text
Database authentication/startup failure
```

Restore the valid username.

This proves the environment variable overrides configuration.


---

## 30. Interview Questions

1. What is externalized configuration?
2. What is a Spring profile?
3. How do you activate a profile?
4. How are `application.yml` and `application-local.yml` combined?
5. What does `@Profile` do?
6. When should `@Profile` not be used?
7. `@Value` vs `@ConfigurationProperties`?
8. What is `@ConfigurationPropertiesScan`?
9. What is `@EnableConfigurationProperties`?
10. Why use typed `URI` and `Duration` values?
11. How do environment variables map to Spring properties?
12. What is relaxed binding?
13. What is property precedence?
14. Why validate configuration?
15. Why is fail-fast startup useful?
16. Why does local config use `localhost:5433`?
17. Why does Docker config use `profile-db:5432`?
18. Why shouldn't Java code hardcode Docker service names?
19. Why use `@ActiveProfiles("test")`?
20. Why should secrets stay out of Git?


---

## 31. Coding Checklist

```text
[ ] application.yml cleaned up

[ ] application-local.yml created

[ ] application-test.yml created

[ ] application-docker.yml created

[ ] Profile local DB config works

[ ] Profile Docker DB config works

[ ] Favorites local DB config works

[ ] Favorites Docker DB config works

[ ] Gateway local URLs configured

[ ] Gateway Docker URLs configured

[ ] Environment variables introduced

[ ] No hard-coded DB URL in Java

[ ] No hard-coded downstream URL in Java

[ ] @ConfigurationProperties added

[ ] @ConfigurationPropertiesScan or explicit registration added

[ ] Typed URI/Duration values used

[ ] Configuration validation added

[ ] Missing property failure observed

[ ] @Profile experiment completed

[ ] @ActiveProfiles("test") added

[ ] Environment override demonstrated

[ ] Command-line override demonstrated

[ ] Docker Compose environment verified

[ ] .gitignore updated

[ ] Tests green

[ ] docs/day-08.md updated

[ ] Commit created
```


---

## 32. Scorecard

```text
Local profile works                       1/1
Docker profile works                      1/1
Test profile works                        1/1
Environment overrides work                1/1
@ConfigurationProperties implemented      1/1
Validation works                          1/1
@Profile behavior demonstrated            1/1
Property precedence demonstrated          1/1
Docker Compose config verified            1/1
Tests/docs/commit complete                1/1

TOTAL                                    /10
```

Target: **8/10 minimum, 10/10 preferred.**


---

## 33. Topics Deliberately Deferred

Do not add yet:

```text
Spring Cloud Config Server
Vault
AWS Secrets Manager
Kubernetes ConfigMaps
Kubernetes Secrets
Helm
Terraform
Dynamic configuration refresh
```

Day 08 is specifically about Spring Boot configuration, profiles, environment variables, typed properties, validation, and Docker/test separation.


---

# Day 08 Definition of Done

Day 08 is complete when the same compiled application can run locally and in Docker without Java-code changes.

You should be able to demonstrate:

```text
Local Profile Service
    |
    +--> application.yml
    +--> application-local.yml
    +--> localhost:5433

Docker Profile Service
    |
    +--> application.yml
    +--> application-docker.yml
    +--> profile-db:5432
```

Gateway:

```text
local:
http://localhost:8081
http://localhost:8082

docker:
http://profile-service:8081
http://favorites-service:8082
```

You should be able to explain, without notes:

```text
Spring profile
@Profile
@Value
@ConfigurationProperties
@ConfigurationPropertiesScan
environment-variable binding
relaxed binding
property precedence
configuration validation
@ActiveProfiles
Docker hostname vs localhost
secret-management basics
```

If you can run the same JAR in local and Docker environments by changing only configuration, Day 08 is complete.
