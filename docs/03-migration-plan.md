# Migration plan — 3.5 days

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
| 4 | **Characterize before you change.** Encode legacy behaviour as executable tests first. | Epic 2 | The only defensible definition of "functional equivalence". Scales because the test suite, not a person, holds the parity knowledge. |
| 5 | **Migrate by vertical slice**, each one demoable. | Epics 3–6 | A slice that browses → buys → ships proves the whole stack. Horizontal (all entities, then all services) is unfalsifiable until the last day. |
| 6 | **Automate the gates, review the intent.** | CI: build, test, ArchUnit, coverage | The gate that is not automated is the gate that is skipped in week 6 of a real programme. |

The corresponding **anti-patterns deliberately avoided**: no big-bang rewrite, no "port the EJBs
one-for-one", no starting with the hardest module, no leaving the stretch goal until the end, and
no framework choice made on preference rather than on recoverability (ADR-0001).

## 2. Scope, in one sentence

One Spring Boot 4.1.1 / Java 21 application, internally modular along the old EAR boundaries,
running the full storefront-to-fulfilment golden path against MongoDB (and H2 via a profile
switch), with the Swing admin client replaced by a web screen and plaintext passwords replaced by
BCrypt. Details and exclusions: [ADR-0002](adr/0002-migration-scope.md).

## 3. Schedule — 7 half-days (~28h)

| Slot | Focus | Epics | Exit condition |
| --- | --- | --- | --- |
| **D1 AM** | Foundation | E1, E2.1 | `mvn verify` green in CI; `docker compose up` starts Mongo; skeleton spike closed (ADR-0001 fallback decided) |
| **D1 PM** | Catalog slice | E3 | Browse category → product → item in a browser, from seeded data, on Mongo |
| **D2 AM** | Identity & customer | E4 | Sign in, create account, duplicate-account rule enforced, BCrypt in place |
| **D2 PM** | Cart & checkout | E5 | Add to cart → check out → PurchaseOrder persisted with a `1001…` id |
| **D3 AM** | Order workflow | E6 | Over-threshold order parks PENDING, admin approves, inventory decrements, order COMPLETED |
| **D3 PM** | Persistence & parity hardening | E7, E2 remainder | Same suite green under `mongo` **and** `jpa`; all §5 rules covered |
| **D4 AM** | Delivery | E8 | Clean clone → README steps → running app; demo script rehearsed once |

**Cut line, in the order things get dropped:** REST facade (E3.6) → JPA adapter (E3.3, E7.4
degrades to Mongo-only) → `ja_JP` UI messages (E4.5) → partial-shipment completion nuance (E6.6).
Nothing on the golden path is ever a cut candidate.

**Buffer:** the estimates total ~30h against 28h of capacity. That is intentional — the cut line
*is* the buffer, and it is decided now rather than at 2am on day 4.

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
- The parity tests from Epic 2 are tagged `@Tag("parity")` and run as their own CI job, so a
  parity break is visibly different from a unit-test break.

## 6. Risk register

| # | Risk | Likelihood | Impact | Mitigation | Trigger to act |
| --- | --- | --- | --- | --- | --- |
| R1 | Spring Boot 4.x ecosystem gaps (most published answers are 3.5.x-shaped) | Medium | High | Issue 1.1 spike, timeboxed to 90 min; documented fallback to 3.5.x | Spike exceeds 90 min |
| R2 | Java-per-day rustiness costs more than estimated | Medium | High | Vertical slices keep the app demoable at all times; cut line is pre-agreed | Any half-day slot overruns by >1h |
| R3 | Dual persistence adapters eat the schedule | Medium | Medium | Mongo is primary; JPA adapter is a cut candidate from day 1 | End of D2 PM with JPA behind |
| R4 | No authoritative DDL for CMP tables (finding #2) — re-derived model is wrong | Low | Medium | Model derived from `ejb-jar.xml` CMP fields and cross-checked against `PopulateSQL.xml` and the JSPs that render them | A screen needs a field the model lacks |
| R5 | Order-workflow parity is subtler than it reads (partial shipment, status transitions) | Medium | Medium | Rules encoded as failing tests in Epic 2 *before* Epic 6 starts | — |
| R6 | Scope creep into `opc`/`supplier` as separate deployables, or into the Swing client | Low | High | ADR-0002 says no, in writing | — |
| R7 | Demo fails live (data state, ports, containers) | Medium | High | E8.5: clean-clone rehearsal; seed data reloadable with one command; screenshots as fallback | — |

## 7. What the playback should claim — and what it should not

**Claim:** a seam-driven, test-anchored, slice-by-slice migration with the decisions written down
and the boundaries machine-enforced; a document model that removes a six-way join and four
duplicated value objects; two persistence backends behind one set of ports.

**Do not claim:** full functional equivalence. State the gaps plainly — no SMTP, no Swing client,
no JAX-RPC path, `zh_CN` data-only, process boundaries collapsed to in-process events, retry/DLQ
semantics simulated. A migration engineer who volunteers the gaps is more credible than one who
is caught on them.
