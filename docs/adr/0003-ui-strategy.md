# ADR-0003 — UI strategy: server-rendered Thymeleaf, with a thin REST facade

- **Status:** Accepted
- **Date:** 2026-09-01

## Decision

Port the storefront as **server-rendered Thymeleaf pages**, and expose the same application
services through a thin `/api/**` REST layer.

## Why not a SPA

The brief grades *functional equivalence* and *migration process*, not front-end craft. I was considering 
using a React SPA, but it would add a second build, a second dependency tree, an auth story and a whole day of work,
and would make side-by-side screen comparison with the legacy JSPs harder, not easier. As mainly a
JavaScript developer I would be faster in React — which is precisely why choosing it here would be
optimising for my comfort instead of for the brief.

## Considered and deferred: React + TanStack hybrid

Evaluated and **deferred until after the migration lands** — React is a post-migration
enhancement.

| Option | UI work | Java work added | Infra | Net vs plan |
| --- | --- | --- | --- | --- |
| **A. Thymeleaf only** (chosen) | ~4h | 0 | 1 deployable | baseline |
| B. React SPA served by Spring (Vite build into the jar) | ~7h | +2.5h REST facade across all slices | 1 deployable, 1 origin | +5–6h |
| C. TanStack Start SSR as a separate Node process | ~9h | +3h REST + BFF/auth | 2 runtimes | +9–11h |
| D. Thymeleaf shell + one React island (catalog search, TanStack Query) | ~6h | +1h (catalog API only) | 1 deployable | +2h |

Reasoning, kept here because it is the likeliest question in the playback:

1. **React does not remove Java work; it adds a serialization boundary to every slice.** Thymeleaf
   lets a controller pass a domain object straight to the view — no DTOs, no JSON contract, no
   CORS. The backlog is already 29.75h against 28h of capacity, so C is only affordable by cutting
   the MongoDB stretch or the order workflow. Both are the wrong trade for this audience.
2. **Auth is the hidden cost in C.** A same-origin front end inherits the Spring Security session
   cookie untouched (the direct descendant of `SignOnFilter`). A separate Node origin needs a BFF
   proxy with cookie forwarding, or JWT + refresh — half a day spent debugging cookie attributes
   instead of demoing.
3. **The legacy JSPs port mechanically to Thymeleaf and not at all to JSX.** They are JSTL:
   `c:forEach` → `th:each`, `c:if` → `th:if`, `fmt:message` → `th:text="#{...}"`,
   `fmt:formatNumber` → `#numbers.formatCurrency`. There is **no CSS anywhere in `docroot`** — it
   is 2003 table markup with `bgcolor`/`cellpadding` and 84 GIFs — so a React rewrite also means
   authoring a stylesheet from scratch. That is redecorating, not migrating.
4. **SSR is not a new capability here.** The legacy app is server-rendered, Thymeleaf keeps it, a
   SPA loses it, and TanStack Start pays a Node runtime to buy it back.
5. **At 10× scale the trade flips.** With a team and a six-month horizon, API-first + SPA is right:
   the presentation rewrite parallelizes and the API outlives the front end. It is wrong *here*
   only because one person over 7 days pays the integration cost without collecting the
   parallelism benefit. Knowing why the answer changes with team size is the point.

The `/api/**` facade below is what keeps option B or D cheap **later**: once the application layer
has no view coupling, the front end is a choice rather than a rewrite.

## Mapping

| Legacy | New |
| --- | --- |
| `template.jsp` + `screendefinitions_en_US.xml` (19 screens) | one Thymeleaf layout fragment (`layout.html`: banner / sidebar / body / footer) |
| `MainServlet` + `RequestProcessor` + `mappings.xml` | `@Controller` methods; routing is code, checked by the compiler, not an XML table |
| `HTMLAction` → `Event` → `EJBAction` | Controller → application service (the two-step web/EJB dispatch collapses) |
| `ScreenFlowManager` error-screen mapping | `@ControllerAdvice` exception handlers |
| `screendefinitions_ja_JP.xml` etc. | `MessageSource` + `messages_ja.properties` |
| Catalog JSPs calling `CatalogHelper` directly | Catalog controllers → `CatalogService` (the legacy shortcut past the controller is *not* reproduced) |

The REST facade exists to prove the application layer has no view coupling (and it is what a real
modernisation would expose to a future front end). Under [ADR-0006](0006-deliverable-scope-kitchensink-slice.md)
it is **promoted from cut-line item to required**: kitchensink ships a JAX-RS resource, Pet Store
has none (`grep -rl javax.ws.rs src` → 0 files), and closing that gap is part of being a faithful
kitchensink equivalent rather than an optional extra.

## Consequence

`mappings.xml`, `screendefinitions_*.xml` and `signon-config.xml` — three XML files that *were*
the routing table — disappear. That is a headline simplification worth showing side by side in the
playback, and the dead `SignOffEvent` mapping (finding #11) is the argument for why config-driven
dispatch needs startup validation when you do keep it.
