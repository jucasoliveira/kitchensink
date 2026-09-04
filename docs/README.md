# Docs

Working documentation for the Pet Store modernisation take-home.

**Build and run steps:** [`README.md`](../README.md) at the repository root — prerequisites, the
`mongo`/`jpa` persistence profiles, and how to run the tests.

**Working agreement:** [`AGENTS.md`](../AGENTS.md) at the repository root — who writes what,
and the standing instruction that an AI assistant writes into the codebase only when asked.

**Read in this order:** [ADR-0006](adr/0006-deliverable-scope-kitchensink-slice.md) for *what is
being delivered and why that is the right unit of work*, then
[03-migration-plan.md](03-migration-plan.md) for *how*, then
[01-legacy-architecture.md](01-legacy-architecture.md) for *what is being migrated from*.

| Doc | Contents |
| --- | --- |
| [01-legacy-architecture.md](01-legacy-architecture.md) | Reverse-engineered architecture of Java Pet Store 1.3.1_02: deployment view, WAF request flow, EJB inventory, async order workflow, data model, and the findings that drive migration decisions. |
| [02-running-the-legacy-app.md](02-running-the-legacy-app.md) | What the legacy stack requires, what this machine has, the options for running it, and the recommended containerised approach. **Note:** the challenge asks for a live demo of the *migrated* app only — running the legacy stack is out of scope per ADR-0002. |
| [03-migration-plan.md](03-migration-plan.md) | The method (inventory → seams → decide → characterize → slice → gate), scope, the 7-half-day schedule (23.5h of work in 28h), definition of done, quality gates, and the risk register. |
| [04-work-breakdown.md](04-work-breakdown.md) | 6 epics / 38 sub-issues across two tiers, each with a legacy anchor, acceptance criteria and an estimate, plus the deferred E5/E6 tables. |
| [05-test-harness.md](05-test-harness.md) | The four quality gates (unit + slice tests, Testcontainers, ArchUnit, JaCoCo floor) and the parity job: where each lives, the green CI runs, and a drill per gate showing what red looks like and how to cause it without touching `src/main`. |
| [06-traceability-matrix.md](06-traceability-matrix.md) | Every legacy component from the EJB inventory, the `mappings.xml` routing table and the messaging topology, mapped one row each to a new component (built or planned), "designed, not built" (T3), or "dropped, because…". |
| [../legacy-runtime/README.md](../legacy-runtime/README.md) | Container scaffold that would run the unmodified 2003 app on the J2EE 1.3.1 RI. Out of scope per ADR-0002; kept as a costless hedge and as evidence for the environment analysis. |
| [07-target-architecture.md](07-target-architecture.md) | The migrated application in the legacy document's own sections and diagram style, so the two can be read side by side — deployment view, web tier, where the 33 EJBs went, security, one port two adapters, and the gaps. | Second, after the legacy doc |
| [09-what-i-learned.md](09-what-i-learned.md) | What changed my mind during the work, and how I would run the next migration. | The brief's third playback bullet |
| [10-final-gate.md](10-final-gate.md) | The clean-clone rehearsal, what it broke, the gate results, and the gaps listed. | Last |
| [decisions/](decisions/) | Short prose notes written as each slice landed, one per topic — the stack, the data model, the two stores, the seed fixture, the testing approach, catalog browsing, the catalog screens, the [document design](decisions/document-design.md) (issue 7.2 — the removed joins and the removed duplication, counted) and the [relational→document migration note](decisions/relational-to-document-migration.md) (issue 7.5). Where an ADR decides before the fact, these record what the code actually did and which legacy behaviours were kept, fixed or dropped. |
| [adr/](adr/) | The six decisions taken before any code: target runtime (Spring Boot over Quarkus), scope, UI strategy, async workflow, persistence + MongoDB, and **[ADR-0006](adr/0006-deliverable-scope-kitchensink-slice.md) — the deliverable is the kitchensink vertical slice**, which supersedes part of ADR-0002 and defers ADR-0004. |

Diagrams are Mermaid, so they render inline on GitHub. To view them locally, use any Markdown
previewer with Mermaid support (VS Code's built-in preview handles them).

## Decisions

| ADR | Decision | Status |
| --- | --- | --- |
| [0001](adr/0001-target-runtime.md) | Spring Boot 4.1.1 / Java 21, not Quarkus — chosen on recoverability, not preference | Accepted |
| [0002](adr/0002-migration-scope.md) | Migration scope, first cut — sized at ~30h against 28h | **Superseded by 0006** |
| [0003](adr/0003-ui-strategy.md) | Server-rendered Thymeleaf + a REST facade; React costed and deferred | Accepted |
| [0004](adr/0004-async-workflow.md) | In-process transactional events replace the JMS queues and topic | **Deferred, unbuilt** |
| [0005](adr/0005-persistence-and-mongodb.md) | Repository ports with MongoDB and JPA adapters behind one interface | Accepted, amended by 0006 |
| [0006](adr/0006-deliverable-scope-kitchensink-slice.md) | **The deliverable is the kitchensink vertical slice**, in three tiers — the single authoritative scope ADR: tiers, exclusions, deviations, legacy-run decision | Accepted |

Work is tracked as GitHub issues — 6 epics and 38 sub-issues on the `Pet Store -> Spring Boot`
milestone, plus 14 closed issues parked in `Deferred - designed, not built`. `scripts/backlog.json`
is the machine-readable source; `scripts/create-github-issues.sh` projects it onto GitHub and is
idempotent.
