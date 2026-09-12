# Day 05 — JPA Relationships, Fetching, N+1, and EntityGraph

## Objective

Extend the persistence model with related profile preference/attribute entities, deliberately create an N+1 query, then fix it.

### Learning Goals

- Model entity relationships safely
- Understand owning vs inverse side
- Use LAZY vs EAGER deliberately
- Reproduce and fix N+1
- Use fetch join and @EntityGraph
- Understand cascade and orphan removal

---

# 2-Hour Agenda

```text
00–10 min   Run prior tests and recap yesterday
10–25 min   Study today's core concepts
25–45 min   Create/refactor domain/configuration
45–75 min   Implement the main happy path
75–95 min   Implement failure and edge paths
95–110 min  Write/extend tests and inspect runtime behavior
110–120 min Interview drill, scorecard, Git commit
```

Do not mark the day complete just because the code compiles. Trigger at least one failure deliberately and explain why the framework behaves as observed.

---

# Build Target

> Extend the persistence model with related profile preference/attribute entities, deliberately create an N+1 query, then fix it.

Use clean boundaries:

```text
Controller/Gateway -> Service -> Repository/Client/Producer
```

Infrastructure-specific logic should remain behind repository/client/configuration abstractions.

---

# Core Concepts

- Model entity relationships safely
- Understand owning vs inverse side
- Use LAZY vs EAGER deliberately
- Reproduce and fix N+1
- Use fetch join and @EntityGraph
- Understand cascade and orphan removal

For every concept, be able to answer:

```text
What is it?
Why does it exist?
How does Spring/runtime implement it?
What is one limitation/failure mode?
Where did I use it today?
```

---

# Annotation / API Reference

### `@OneToOne`

Maps a one-to-one entity association.

### `@OneToMany`

Maps one entity to a collection of child entities.

### `@ManyToOne`

Maps many child rows to one parent entity.

### `@ManyToMany`

Maps a many-to-many association; use carefully because join-table ownership and lifecycle can become complex.

### `@JoinColumn`

Defines the foreign-key column.

### `@JoinTable`

Defines an intermediate join table.

### `mappedBy`

Marks the inverse side and identifies the owning field of a bidirectional relationship.

### `@EntityGraph`

Overrides fetch behavior for a repository query.


---

# Implementation Steps

## 1. Start Green

Run:

```bash
mvn test
```

Fix existing failures before starting.

## 2. Create Branch

```text
day-05-jpa-relationships-fetching-n-1-and-entityg
```

## 3. Draw the Flow

Add to `docs/day-05.md`:

```text
Input
  |
  v
Boundary
  |
  v
Service
  |
  v
Persistence / downstream / infrastructure
```

## 4. Implement Happy Path

Implement the core requirement and verify manually.

Capture:

```text
Request/input
Response/output
Database/cache/message side effect
Relevant logs
```

## 5. Implement Failure Paths

At minimum test:

```text
Invalid input
Missing resource
Infrastructure/downstream failure
Duplicate/retry behavior where relevant
Unexpected failure mapping
```

## 6. Observe Internals

Turn on useful logs for today's topic. Examples:

```text
Hibernate SQL
Transaction logs
Spring Security logs
Resilience events
Gateway routing logs
Redis activity
```

## 7. Refactor

Confirm:

```text
No field injection
No controller -> repository shortcut
No JPA entity returned directly
No hard-coded secret
No swallowed exception
No duplicated business logic
```

---

# Testing Requirements

- Relationship persistence test
- Lazy/eager observation
- N+1 SQL observation
- Fetch join or EntityGraph test

Use Given/When/Then thinking for every test.

---

# Failure Experiments

Perform at least two:

```text
A. Remove/stop/misconfigure a dependency and observe the failure.
B. Create invalid or conflicting state and observe which layer rejects it.
```

Document the exception/status/log and why it occurred.

---

# Interview Questions

1. Explain Model entity relationships safely and give an example from this project.
2. Explain Understand owning vs inverse side and give an example from this project.
3. Explain Use LAZY vs EAGER deliberately and give an example from this project.
4. Explain Reproduce and fix N+1 and give an example from this project.
5. Explain Use fetch join and @EntityGraph and give an example from this project.
6. Explain Understand cascade and orphan removal and give an example from this project.

Also answer:

1. What trade-off did today's feature introduce?
2. Which layer owns the behavior?
3. What changes when this runs under concurrency?
4. What would you monitor in production?
5. What would you avoid doing in a real production system?

---

# Coding Checklist

```text
[ ] Model entity relationships safely
[ ] Understand owning vs inverse side
[ ] Use LAZY vs EAGER deliberately
[ ] Reproduce and fix N+1
[ ] Use fetch join and @EntityGraph
[ ] Understand cascade and orphan removal
[ ] Relationship persistence test
[ ] Lazy/eager observation
[ ] N+1 SQL observation
[ ] Fetch join or EntityGraph test
[ ] Existing tests remain green
[ ] At least one negative-path test added
[ ] Manual smoke test completed
[ ] Runtime/log behavior inspected
[ ] docs/day-05.md updated
[ ] Code committed
```

---

# Scorecard

```text
Application/modules compile                     1/1
Primary feature works                           2/2
Happy-path tests pass                           1/1
Failure/edge tests pass                         1/1
Architecture/layering is clean                  1/1
Annotations/concepts understood                 1/1
Runtime behavior observed                       1/1
Interview questions answered                    1/1
Documentation + commit complete                 1/1

TOTAL                                          /10
```

Target: **8/10 minimum, 10/10 preferred.**

---

# Suggested Commit

```text
day-05: jpa relationships, fetching, n+1, and entitygraph
```

---

# Definition of Done

Day 05 is complete when the feature works, tests prove it, at least one failure has been deliberately observed, and you can explain the implementation without reading the code.
