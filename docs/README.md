# Docs

Working documentation for the Pet Store modernisation take-home.

| Doc | Contents |
| --- | --- |
| [01-legacy-architecture.md](01-legacy-architecture.md) | Reverse-engineered architecture of Java Pet Store 1.3.1_02: deployment view, WAF request flow, EJB inventory, async order workflow, data model, and the findings that drive migration decisions. |
| [02-running-the-legacy-app.md](02-running-the-legacy-app.md) | What the legacy stack requires, what this machine has, the options for running it, and the recommended containerised approach. **Note:** the challenge asks for a live demo of the *migrated* app only — running the legacy stack is out of scope per ADR-0002. |
| [03-migration-plan.md](03-migration-plan.md) | The method (inventory → seams → decide → characterize → slice → gate), scope, the 7-half-day schedule with its cut line, definition of done, quality gates, and the risk register. |
| [04-work-breakdown.md](04-work-breakdown.md) | 8 epics / 45 sub-issues, each with a legacy anchor, acceptance criteria and an estimate. |
| [../legacy-runtime/README.md](../legacy-runtime/README.md) | Container scaffold that would run the unmodified 2003 app on the J2EE 1.3.1 RI. Out of scope per ADR-0002; kept as a costless hedge and as evidence for the environment analysis. |
| [adr/](adr/) | The five decisions taken before any code: target runtime (Spring Boot over Quarkus), scope, UI strategy, async workflow, persistence + MongoDB. |

Diagrams are Mermaid, so they render inline on GitHub. To view them locally, use any Markdown
previewer with Mermaid support (VS Code's built-in preview handles them).
