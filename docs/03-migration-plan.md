# Migration plan 

> Decisions live in [`adr/`](adr/). The issue backlog lives in
> [`04-work-breakdown.md`](04-work-breakdown.md) and in `scripts/backlog.json`.

## 1. Method — the part that is being graded

The brief asks to approach this "in the way you might do if the legacy application codebase was
far larger". That is a question about **method**, not about Pet Store. The method used here has
six steps, and each one leaves an artefact in this repository:

| # | Step | Artefact | Why it scales |
| --- | --- | --- | --- |
| 1 | **Inventory and anchor.** Reverse-engineer the system; every claim carries a `file:line`. | `01-legacy-architecture.md` | On a 10× codebase you cannot hold it in your head, and unanchored claims rot. Anchors make review possible. |
| 2 | **Find the seams.** Locate the joints the system can be cut along. | §2 "the four EARs are coupled only through XML-over-JMS" | Seams decide whether migration is incremental (strangler) or big-bang. Everything else follows from this. |
| 3 | **Decide, in writing, before coding.** | `adr/0001`–`0005` | On a large programme the expensive mistakes are decisions, not code. ADRs make them reversible and reviewable. |
| 4 | **Characterize before you change.** Encode legacy behaviour as executable tests first. | E2 | The only defensible definition of "functional equivalence". Scales because the test suite, not a person, holds the parity knowledge. |
| 5 | **Migrate by vertical slice**, each one demoable. | E1 (tracer bullet) → E4 → E3 | A slice that registers, validates, persists, renders and serves JSON proves the whole stack. Horizontal (all entities, then all services) is unfalsifiable until the last day. |
| 6 | **Automate the gates, review the intent.** | CI: build, test, ArchUnit, coverage | The gate that is not automated is the gate that is skipped in week 6 of a real programme. |

The corresponding **anti-patterns deliberately avoided**: no big-bang rewrite, no "port the EJBs
one-for-one", no starting with the hardest module, no leaving the stretch goal until the end, and
no framework choice made on preference rather than on recoverability (ADR-0001).

## 2. Scope, in one sentence

One Spring Boot 4.1.1 / Java 21 application delivering **the kitchensink equivalent of Pet Store**
— the registration/identity vertical slice (29 of 309 legacy files: `signon` + `customer` +
`address` + `contactinfo`) plus the catalog read path, running against MongoDB and H2 behind the
same repository ports, with plaintext passwords replaced by BCrypt and a REST resource where the
legacy had none. Cart, checkout and the order workflow are **designed and not built**.

Why a slice rather than the golden path, why this slice, and everything excluded or deviated
from: [**ADR-0006**](adr/0006-deliverable-scope-kitchensink-slice.md) — the single scope ADR.
[ADR-0002](adr/0002-migration-scope.md) is its superseded first cut, kept for the decision trail.

## 3. Schedule — 7 half-days (~28h capacity, 23.5h of work)

| Slot | Focus | Tier | Exit condition |
| --- | --- | --- | --- |
| **D1 AM** | Foundation: skeleton spike, module layout, compose, CI, profiles | T1 | `mvn verify` green in CI; `docker compose up` starts Mongo; ADR-0001 fallback decided |
| **D1 mid** | Walking skeleton (1.7–1.10): one aggregate, one form, one endpoint, one green gate of each kind | T1 | Every layer of the target stack exercised once before any of it is built properly |
| **D1 PM** | Customer aggregate + Mongo adapter + Bean Validation | T1 | A customer document with embedded contactInfo/address/card round-trips; validation rejects bad input |
| **D2 AM** | Spring Security replacing `SignOnFilter`, BCrypt, create-account + duplicate-account rule | T1 | Sign in, register, duplicate rejected — all covered by parity tests |
| **D2 PM** | Form + table screens, REST resource, parity + Testcontainers tests | T1 | **T1 complete and demoable.** The kitchensink twin ships from here on. |
| **D3 AM** | Catalog read path: domain, Mongo adapter, service, browse/search screens | T2 | Browse category → product → item from seeded data; text search works |
| **D3 PM** | JPA/H2 adapter + profile-switch demo + CI hardening | T2 | Same suite green under `mongo` **and** `jpa` |
| **D4 AM** | Delivery: README, architecture doc, demo script, clean-clone rehearsal | T1 | Clean clone → README steps → running app; demo rehearsed once |

**Cut line, in order:** catalog screens (T2 degrades to REST + tests only) → JPA adapter
(Mongo-only) → `ja_JP` UI messages. Nothing in T1 is ever a cut candidate.

**Buffer:** ~4.5h of genuine slack, which is the entire point of ADR-0006. The previous plan's
"buffer" was a list of things to delete under pressure; this one is time. If T1 and T2 land early
the reopen candidates are 5.1 → 5.4 (cart, then checkout), in that order — never T3's async
workflow, which cannot be half-built.

## 4. Definition of done (per issue)

1. Behaviour is covered by a test that names the legacy rule or `file:line` it preserves.
2. The legacy→new traceability matrix (Issue 2.3) has a row for it.
3. No new ArchUnit violation; module boundaries hold.
4. Works under both active persistence profiles, or the gap is written down.
5. Merged via PR to `main`; CI green. No direct pushes to `main`.

## 5. Quality gates (CI on every PR)

- `mvn verify` — unit + slice tests, Testcontainers for Mongo.
- **ArchUnit**: no module reaches into another module's internals; no controller touches a
  repository; domain package imports no Spring persistence annotation.
- **JaCoCo**: line coverage floor on the `domain` and `application` packages only — coverage on
  controllers and adapters is theatre.
- The parity tests from E2 are tagged `@Tag("parity")` and run as their own CI job, so a
  parity break is visibly different from a unit-test break.

## 6. Risk register

| # | Risk | Likelihood | Impact | Mitigation | Trigger to act |
| --- | --- | --- | --- | --- | --- |
| R1 | Spring Boot 4.x ecosystem gaps (most published answers are 3.5.x-shaped) | Medium | High | Issue 1.1 spike, timeboxed to 90 min; documented fallback to 3.5.x | Spike exceeds 90 min |
| R2 | Java-per-day rustiness costs more than estimated | Medium | High | Vertical slices keep the app demoable at all times; cut line is pre-agreed | Any half-day slot overruns by >1h |
| R3 | Dual persistence adapters eat the schedule | Low | Medium | Both are now affordable inside T1+T2; Mongo stays primary and JPA remains the first thing cut | End of D3 AM with JPA not started |
| R4 | No authoritative DDL for CMP tables (finding #2) — re-derived model is wrong | Low | Medium | Model derived from `ejb-jar.xml` CMP fields and cross-checked against `PopulateSQL.xml` and the JSPs that render them | A screen needs a field the model lacks |
| R5 | The slice reads as under-delivery to the panel | Medium | High | Lead with ADR-0006: kitchensink is itself a 7-file slice, chosen for touching every layer. Show the deferred design, not just the shipped code | — |
| R6 | Scope creep back into cart / checkout / order workflow because the analysis is already written | **Medium** | High | ADR-0006 defers them explicitly; their issues are closed and parked in a separate milestone, not sitting in the backlog looking available | Any T3 issue reopened before T1 is demoable |
| R7 | Demo fails live (data state, ports, containers) | Medium | High | E8.5: clean-clone rehearsal; seed data reloadable with one command; screenshots as fallback | — |

## 7. What the playback should claim — and what it should not

**Claim:** a seam-driven, test-anchored migration of a deliberately chosen vertical slice, with the
decisions written down and the boundaries machine-enforced; a document model that removes a
six-way join and collapses four duplicated value objects into one embedded shape; two persistence
backends behind one set of ports; and a REST resource plus a test suite where the legacy had
literally zero of either.

**Do not claim:** that Pet Store was migrated. What shipped is the kitchensink equivalent of it —
and the reason that is the right unit of work is the brief's own example. State the gaps plainly:
cart, checkout and the order workflow are designed and not built (ADR-0004 stands unimplemented);
no SMTP, no Swing client, no JAX-RPC path; `zh_CN` data-only. A migration engineer who volunteers
the gaps is more credible than one who is caught on them.
