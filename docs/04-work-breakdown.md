# Work breakdown — 8 epics, 45 sub-issues

> Narrative view. The machine-readable source is `scripts/backlog.json`; run
> `scripts/create-github-issues.sh` to materialise it as GitHub issues with parent/sub-issue links.
> Estimates are in hours and total ~30h against 28h of capacity — see the cut line in
> [`03-migration-plan.md`](03-migration-plan.md) §3.

**Labels:** `epic`, `infra`, `slice`, `parity`, `persistence`, `docs`, `stretch`, `cut-candidate`.
**Milestone:** `Pet Store → Spring Boot (3.5 days)`.

Every sub-issue carries three things: the **legacy anchor** it replaces, **acceptance criteria**,
and an **estimate**. The anchor is what makes this scale — on a larger codebase the issue tracker
becomes the traceability matrix.

---

## E1 — Foundation: runtime, build, CI, local infra · 4h

| # | Sub-issue | Est | Legacy anchor | Acceptance |
| --- | --- | --- | --- | --- |
| 1.1 | **Skeleton spike:** Boot 4.1.1 + Java 21 with web, security, thymeleaf, data-mongodb, events | 1h | — | App boots, one page renders, one document round-trips. **Timebox 90 min**, then fall back to 3.5.x and amend ADR-0001 |
| 1.2 | Package/module layout per bounded context (`catalog`, `cart`, `customer`, `order`, `opc`, `supplier`, `admin`, shared `domain`/`application`) | 0.5h | the four EARs | Packages exist; ArchUnit test asserts the dependency rules (even before there is code) |
| 1.3 | `compose.yaml` (MongoDB, optional Postgres) + run scripts | 0.5h | `setup.xml` `create_*_db` | `docker compose up` then `mvn spring-boot:run` works from a clean clone |
| 1.4 | GitHub Actions: build, test, ArchUnit, JaCoCo, parity job | 1h | `build.xml` / Ant 1.x | PR checks required on `main` |
| 1.5 | Config profiles `mongo`/`jpa`, externalised config, Actuator health | 0.5h | `sun-j2ee-ri.xml` JNDI wiring, `JNDINames.java` | `--spring.profiles.active=jpa` switches the store with no code change |
| 1.6 | Repo hygiene: labels, milestone, PR template, branch protection, conventional commits | 0.5h | — | Backlog script runs clean; `main` protected |

## E2 — Parity harness: characterization tests + seed data · 3h

| # | Sub-issue | Est | Legacy anchor | Acceptance |
| --- | --- | --- | --- | --- |
| 2.1 | Extract catalog seed data → fixtures + loader | 1h | `docroot/populate/PopulateSQL.xml`, `Populate-UTF8.xml` | Same category/product/item counts per locale as the legacy seed; one command reloads |
| 2.2 | Encode the 7 business rules as **failing** tests, tagged `@Tag("parity")` | 1h | `01-legacy-architecture.md` §5 | Red before Epics 3–6; each test names its `file:line` |
| 2.3 | Legacy→new traceability matrix | 0.5h | `mappings.xml`, EJB inventory §4 | Every in-scope legacy component maps to a new one or to an explicit "dropped, because…" |
| 2.4 | ArchUnit rule set | 0.25h | EJB jar boundaries | Violations fail the build |
| 2.5 | Testcontainers base test + CI wiring | 0.25h | Cloudscape | Mongo-backed tests run in CI without a local install |

The seven parity rules (2.2): auto-approve under $500 / ¥50 000 · order lifecycle
`PENDING → APPROVED|DENIED → COMPLETED` · empty cart cannot be ordered · duplicate account
rejected · order ids are prefix `"1001"` + counter (`10011`, `10012`, …) · order completes only
when *all* line items ship · invoice fans out to two independent subscribers.

## E3 — Catalog slice (read path) · 4.5h

| # | Sub-issue | Est | Legacy anchor | Acceptance |
| --- | --- | --- | --- | --- |
| 3.1 | Domain: `Category`/`Product`/`Item` + locale-scoped details | 0.5h | `*_details.locale` tables | Plain Java, no persistence annotations |
| 3.2 | `CatalogRepository` port + **Mongo** adapter | 1h | `CatalogDAOSQL.xml` (7 statements × 2 dialects) | Product page is one `findById`; the six-way join is gone |
| 3.3 | JPA/H2 adapter for the same port · `cut-candidate` | 0.75h | `CatalogDAOFactory` | Same port tests pass under `jpa` |
| 3.4 | `CatalogService`: browse, search, paging | 0.75h | `CatalogHelper.java` | Search behaviour documented where it differs (text index vs `LIKE`) |
| 3.5 | Thymeleaf layout + 4 catalog screens | 1h | `template.jsp`, `screendefinitions_en_US.xml` | banner/sidebar/body/footer composition preserved |
| 3.6 | REST `/api/catalog/**` · `cut-candidate` | 0.5h | — | Same service layer, no view coupling |

## E4 — Identity, customer, i18n · 4.25h

| # | Sub-issue | Est | Legacy anchor | Acceptance |
| --- | --- | --- | --- | --- |
| 4.1 | Customer aggregate: account, profile, contact info, address, credit card as **embedded** values | 0.75h | 4× duplicated `AddressEJB`/`ContactInfoEJB`/`CreditCardEJB`/`LineItemEJB` (finding #4) | One definition each; duplication removed and shown in the playback |
| 4.2 | Spring Security filter chain replacing `SignOnFilter` | 1h | `SignOnFilter.java`, `signon-config.xml` | Same URLs protected; unauthenticated access redirects to sign-in |
| 4.3 | BCrypt password hashing (documented deviation) | 0.25h | `UserEJB.java:88` `password.equals(...)` (finding #1) | No plaintext credential path anywhere; README says so |
| 4.4 | Create-account flow + duplicate-account rule | 0.75h | `CreateUserEJBAction`, `DuplicateAccountException` | Parity test green |
| 4.5 | Customer/profile screens + `MessageSource` (`en_US`, `ja_JP`) · `ja_JP` is `cut-candidate` | 1h | `ProfileEJB` fields, `screendefinitions_ja_JP.xml` | Locale switch works; preferred-language preference honoured |
| 4.6 | Persistence adapters for the customer aggregate | 0.5h | customer `ejb-jar.xml` CMP fields | Both profiles green |

## E5 — Cart & checkout · 3.5h

| # | Sub-issue | Est | Legacy anchor | Acceptance |
| --- | --- | --- | --- | --- |
| 5.1 | Session-scoped `Cart` | 0.75h | `ShoppingCartEJB` (SFSB) | Survives navigation; cleared on checkout |
| 5.2 | `cart.do` parity: add / update quantity / delete / subtotal | 0.75h | `CartHTMLAction:82`, `CartEJBAction` | Screen behaviour matches the legacy cart |
| 5.3 | Order id generator: `"1001"` prefix + counter | 0.5h | `CounterEJB.java:67`, `OrderEJBAction` `getUniqueId("1001")` | Ids are `10011`, `10012`, … ; parity test green |
| 5.4 | Checkout → `PurchaseOrder` aggregate + empty-cart rule | 1h | `OrderEJBAction`, `ShoppingCartEmptyOrderException` | Order persisted as one document; empty cart rejected |
| 5.5 | Cart + order-complete screens | 0.5h | `cart.screen`, `order_complete.screen` | — |

## E6 — Order workflow (async parity) · 4.5h

| # | Sub-issue | Est | Legacy anchor | Acceptance |
| --- | --- | --- | --- | --- |
| 6.1 | Event model + transactional publisher | 0.5h | XML-over-JMS, `components/xmldocuments` | Typed events replace DTD-validated XML; published after commit |
| 6.2 | `OrderPlaced` handler: persist PO, status `PENDING` | 0.75h | `PurchaseOrderMDB`, `ProcessManagerEJB`, `ManagerEJB` | Status visible in the admin screen |
| 6.3 | Approval rule: auto-approve < $500 US / ¥50 000 JP | 0.5h | `PurchaseOrderMDB.java:183` `canIApprove` | Both currencies tested |
| 6.4 | Admin approve/deny screen | 1h | `PetStoreAdminClient.java` (Swing) + `OPCAdminFacadeEJB` | Replaces the desktop client; drives the demo's decision moment |
| 6.5 | Supplier: inventory decrement + invoice emission | 0.75h | `SupplierOrderMDB`, `InventoryEJB`, `OrderFulfillmentFacadeEJB` | Atomic `$inc` on Mongo; stock cannot go negative |
| 6.6 | Invoice fan-out to two listeners + completion when all lines ship · nuance is `cut-candidate` | 0.75h | `jms/opc/InvoiceTopic`, `InvoiceMDB.doWork` | Two independent subscribers observed in a test; partial shipment does not complete the order |
| 6.7 | Reliability notes: at-least-once, idempotency, promotion path to a broker | 0.25h | queue durability | Written down in the README's known-gaps section |

## E7 — Persistence strategy & MongoDB stretch · 3h

| # | Sub-issue | Est | Legacy anchor | Acceptance |
| --- | --- | --- | --- | --- |
| 7.1 | Port/adapter package rules + ArchUnit enforcement | 0.25h | — | Domain imports no Spring Data type |
| 7.2 | Document design: catalog tree vs order aggregate | 0.75h | ER model §6 | Written up with the join/duplication removed, quantified |
| 7.3 | Indexes + text search for catalog | 0.5h | `CatalogDAOSQL.xml` search statements | Query plans checked, not assumed |
| 7.4 | Profile-switch demo: same suite green under `mongo` and `jpa` · `cut-candidate` | 1h | — | One command each; results identical |
| 7.5 | Relational→document migration note (how a real engagement moves the data) | 0.5h | CMP auto-schema (finding #2) | Mapping rules, not a hand-written script |

## E8 — Delivery: README, demo, playback material · 3h

| # | Sub-issue | Est | Acceptance |
| --- | --- | --- | --- |
| 8.1 | **README with build & run steps** (explicit requirement of the brief) | 0.75h | A developer with Docker + JDK 21 and no context gets the app running |
| 8.2 | Architecture doc + diagram for the new app | 0.5h | Mermaid, same style as `01-legacy-architecture.md` |
| 8.3 | Demo script: golden path → over-threshold approval → profile switch | 0.5h | Rehearsed once, timed under 15 min |
| 8.4 | "What I learned / how I'd run the next one" notes | 0.5h | Answers the brief's third playback bullet directly |
| 8.5 | Final gate: clean-clone rehearsal, CI green, gaps listed | 0.75h | Fresh `git clone` → README steps → running app, no undocumented step |
