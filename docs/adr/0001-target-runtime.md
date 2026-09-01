# ADR-0001 — Target runtime: Spring Boot (not Quarkus)

- **Status:** Accepted
- **Date:** 2026-09-01
- **Drivers:** 3.5 calendar days; primary author's day-to-day language is JavaScript/TypeScript, not
  Java; audience for the playback is MongoDB engineering staff; MongoDB stretch goal.

## Decision

Build the migrated application on **Spring Boot 4.1.1 / Java 21** (verified as the current
Initializr default on 2026-09-01; Quarkus current release for comparison is 3.39.1).

## The actual question

Both frameworks can express everything in `01-legacy-architecture.md`. The tie-breaker is not
capability, it is **cost of being wrong at 11pm on day 2 in a language you don't write daily**.

| Dimension | Spring Boot | Quarkus | Weight for this project |
| --- | --- | --- | --- |
| Answer density (SO, blogs, model training data) | Overwhelming | Good but an order of magnitude smaller | **Decisive.** Every hour lost to an un-Googleable stack trace is 3% of the budget. |
| Scaffolding | `start.spring.io`, IDE-integrated | `code.quarkus.io`, `quarkus create` | Tie |
| Dev loop | DevTools restart (~2–4s) | `quarkus dev` live reload + Dev Services + continuous testing | **Quarkus wins.** Closest thing to `npm run dev` in Java. |
| Legacy sign-on filter → new | Spring Security filter chain is a near-1:1 conceptual port of `SignOnFilter` + `signon-config.xml` | Quarkus Security is more opinionated, less filter-shaped | Spring |
| JSP + `TemplateServlet` → new | Thymeleaf + layout dialect maps directly onto `template.jsp` (banner/sidebar/body/footer) | Qute is capable, far fewer worked examples | Spring |
| CMP 2.0 entities → new | JPA/Hibernate | Panache (nicer API) | Quarkus marginally |
| XML-over-JMS → new | In-process events, or embedded Artemis | SmallRye Reactive Messaging (Kafka/AMQP — heavier local infra) | Spring |
| MongoDB stretch | Spring Data MongoDB — the stack MongoDB's own docs and education teach | Panache MongoDB / native driver | **Spring.** Talking to MongoDB staff in their own idiom. |
| Failure modes | Runtime, well-documented | Build-time CDI/reflection errors, extension gaps | Spring |
| Native image / startup | Slower | Excellent | Irrelevant here; a distraction under a 3.5-day budget |

## Consequences

- We give up Quarkus's better inner loop. Mitigation: DevTools + `spring-boot:test-run`, and the
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
