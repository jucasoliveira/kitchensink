# ADR-0001 — Target runtime: Spring Boot (not Quarkus)

- **Status:** Accepted
- **Date:** 2026-09-01
- **Drivers:** MongoDB stretch goal.

## Decision

Build the migrated application on **Spring Boot 4.1.1 / Java 21** .

## The technical choice

I've **previous work done in Spring Boot** — the cost of being wrong at 11pm in a language you don't write daily is higher than the cost of being wrong in a language you do. The brief's "modern platform" requirement is satisfied by Spring Boot, and the MongoDB stretch goal is satisfied by Spring Data MongoDB.

## Consequences

- I've give up Quarkus's better inner loop. Mitigation: DevTools + `spring-boot:test-run`, and the
  test suite (not the browser) is the primary feedback channel.
- Spring Boot 4.x is new enough that a large share of published answers are 3.5.x-shaped
  (config property names, Security DSL). **Mitigation is Issue 1.1: a 1-hour skeleton spike that
  boots web + security + Thymeleaf + Spring Data MongoDB + events on 4.1.1 before anything else is
  written.** If that spike burns more than 90 minutes, fall back to the latest 3.5.x line and
  record it here as a superseding decision — "latest stable" is worth points, a stalled build is
  worth none.
- Spring Modulith (for transactional event publication) must be version-matched to Boot 4.1;
  if no compatible release exists, fall back to plain `ApplicationEventPublisher` +
  `@TransactionalEventListener`, which are core-framework and always available. See ADR-0004.

## Rejected

**Quarkus** — better DX, worse recoverability. **Micronaut / Helidon / plain Jakarta EE on
WildFly** — WildFly would preserve more legacy shape but is the opposite of "modern platform" for
this brief, and none of them were offered as options.
