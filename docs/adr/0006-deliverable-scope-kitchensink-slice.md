# ADR-0006 — Deliverable scope: the kitchensink slice

- **Status:** Accepted. **Supersedes [ADR-0002](0002-migration-scope.md) in full** — this ADR is
  the single authoritative statement of scope, and absorbs the exclusions and deviations
  ADR-0002 recorded. Defers [ADR-0004](0004-async-workflow.md) unbuilt; amends
  [ADR-0005](0005-persistence-and-mongodb.md).
- **Date:** 2026-09-01
- **Driver:** 7 days against 309 `.java` files. ADR-0002's golden path was ~30h of work against
  28h of capacity, with the buffer consisting entirely of a cut line. That is a plan that is
  already behind on day one.

## What "kitchensink" actually is

The brief says to publish "for just 'kitchensink' only". There is no kitchensink in Pet Store —
it is the JBoss quickstart from the *other* variant of this challenge, and knowing what it is for
answers the scope question directly.

kitchensink is **~7 Java files**: one `Member` entity (id / name / email / phone) with Bean
Validation, a repository, a registration service, a JSF backing bean, a JAX-RS resource, one
`index.xhtml` with a form and a table, `import.sql` against H2, and one Arquillian test.

The name means *one of everything*. It is deliberately the **smallest application that touches
every layer of the platform** — persistence, validation, injection, web tier, REST, datasource
configuration, test harness. It was not picked for being one app among many. It was picked for
being a complete vertical slice through the stack at minimum volume.

**So the Pet Store analogue of kitchensink is a thin vertical slice, not one of the four EARs.**
Candidates on the other variant of this challenge migrate 7 files. This variant hands over 309.
Picking a slice is not a compromise on the brief — it is what the brief's own example does.

## The direct twin

kitchensink is a user-registration app. Pet Store has one of those, and it is the closest thing
in the codebase:

| kitchensink | Pet Store equivalent |
| --- | --- |
| `Member` entity + Bean Validation | CMP graph: `CustomerEJB` → `AccountEJB` → `ContactInfoEJB` → `AddressEJB` |
| `MemberRepository` (Criteria) | CMP finders + `components/signon/src/.../web/SignOnDAO.java` |
| `MemberRegistration` (EJB + CDI event) | `components/signon/src/.../web/CreateUserServlet.java` + `SignOnEJB` |
| `MemberController` (JSF `@Model`) | WAF `HTMLAction` + screenflow XML |
| `MemberResourceRESTService` (JAX-RS) | **nothing** — `grep -rl javax.ws.rs src` returns 0 files |
| `index.xhtml` form + table | `docroot/create_customer.jsp`, `signon.jsp` |
| `import.sql` + H2 | `docroot/populate/*.xml` + Cloudscape |
| Arquillian test | **nothing** — `find src -iname '*test*' -name '*.java'` returns 0 files |

That slice is `signon` + `customer` + `address` + `contactinfo` — **29 of the 309 `.java` files**,
verified by `find`.

The two empty rows are the most interesting thing in the table: they are exactly what the legacy
application lacks that the target platform assumes. Both get built, and both are talking points.

## Decision — three tiers

| Tier | Contents | Commitment |
| --- | --- | --- |
| **T1 — the kitchensink slice** | Foundation (skeleton, CI, compose, profiles) · customer aggregate with embedded value objects · Spring Security replacing `SignOnFilter` · BCrypt · create-account + duplicate-account rule · Bean Validation replacing hand-rolled checks · form + table screens · **REST resource** · Testcontainers + parity tests · README, demo, playback docs | **Must ship.** The deliverable. |
| **T2 — catalog read path** | Category/Product/Item with locale-scoped details · Mongo adapter (six-way join → one document) · text index · JPA/H2 adapter behind the same port · browse/search screens · `/api/catalog/**` | **Should ship.** Included because it is the strongest MongoDB argument in the codebase and it makes the demo look like Pet Store rather than a signup form. First thing to go if T1 is at risk. |
| **T3 — cart, checkout, order workflow, approval, supplier** | ADR-0004's event model, the $500/¥50 000 threshold, admin approve/deny, inventory decrement, invoice fan-out | **Designed, not built.** Issues closed and parked in the `Deferred — designed, not built` milestone. The analysis stands in ADR-0004 and `01-legacy-architecture.md` §5; the code is not written. |

## Why this is the right shape, and not just a smaller one

The slice forces **every target-stack decision to become real**: Boot 4.1.1 on Java 21 boots,
MongoDB round-trips an aggregate, Jakarta Validation replaces hand-rolled checks, Spring Security
replaces the custom filter, the web tier renders a form, a REST endpoint exists where the legacy
had none, Testcontainers runs in CI, and the BCrypt deviation lands somewhere small and visible.
Nothing in the stack is left unproven. That is the property that made kitchensink worth shipping
as a quickstart in the first place.

It also concentrates the budget on the thing this audience cares about. The customer graph is the
best embedding example in the codebase — `AddressEJB`, `ContactInfoEJB`, `CreditCardEJB` and
`LineItemEJB` are each declared in **four** `ejb-jar.xml` files purely because EJB 2.0 CMP
relationships cannot cross jar boundaries (finding #4), and they collapse into one embedded
document. Cart and checkout would have consumed ~8h to demonstrate none of that.

## Exclusions — carried forward from ADR-0002, still in force

Excluded outright, independent of tiering:

| Legacy | Why not |
| --- | --- |
| `petstoreadmin` **Swing/JNLP client** | A desktop client is not a modernisation target. Its one load-bearing function — order approval — went to T3 with the workflow it belongs to. |
| `src/webservices` JAX-RPC duplicate of the JMS path | Dead weight; a second implementation of a path that is itself deferred (finding #10). |
| JavaMail / SMTP | Already disabled by default in the legacy app (finding #9), so parity does not include it. |
| `SignOffEvent` → `SignOffEJBAction` mapping | Unreachable dead config (finding #11). Not ported — and a standing argument for validating config-driven dispatch tables at startup. |
| i18n (partial) | Locale-scoped catalog data is preserved in the model; UI messages ship for `en_US` + `ja_JP`; `zh_CN` stays data-only. |

The four EAR boundaries collapse into **modules inside one deployable**, with the boundaries
enforced by ArchUnit rather than by classloaders. The legacy split was a J2EE packaging artefact,
not a scaling decision, and one deployable is demoable on a laptop in 90 seconds. The module seams
stay explicit, so splitting them back out is a packaging change rather than a rewrite — which is
the argument to make about a real, larger migration.

## Deliberate deviations from strict parity

1. **Plaintext passwords → BCrypt** (finding #1). Carrying a plaintext credential store forward
   would be indefensible. Called out in the README rather than hidden, and it lands inside the
   delivered slice where it is visible.
2. **Swing admin client dropped, not replaced.** ADR-0002 planned a web approval screen; that
   screen belonged to the order workflow, which is now T3.
3. **Process boundaries collapsed** — design in [ADR-0004](0004-async-workflow.md), deferred unbuilt.

## Note on running the legacy app

Two sources disagree. The challenge **PDF** asks only for "a live demo of the running *migrated*
application". The **candidate-portal prep notes** say "ensure both the legacy and modernized
versions are functional... run the application live during the presentation *if requested*".

The portal notes are later guidance and are not safely ignored, but the live-run clause is
conditional, and "demonstrate how the legacy app works" is met by `01-legacy-architecture.md`,
which is derived from source and anchored to file and line.

**Decision stands:** the qemu / JDK-1.4 container is out of scope, and the parity baseline is
derived from source, seed data and the `components/xmldocuments` DTD contracts. This protects
roughly a day of budget.

**Hedged, not abandoned:** the container scaffold in `legacy-runtime/` is already complete and
costs nothing further to hold. The only missing piece is `j2sdkee-1_3_1-linux.tar.gz`, a licensed
binary behind Oracle SSO that has to be fetched by hand. Time-boxed at fifteen minutes; if the
download is gone, we stop. See `02-running-the-legacy-app.md` §5.

## Consequences

- **Freed budget goes to depth, not breadth**: both persistence adapters ship (the profile-switch
  demo is no longer a cut candidate), the REST resource is promoted from optional to required, and
  the parity/CI work is done properly rather than sampled.
- Estimates drop from ~30h to **23.5h against 28h**. The buffer is now real slack instead of a cut line.
- **What must not be claimed:** that Pet Store was migrated. What was migrated is a vertical slice
  of it, chosen on the same principle as the artefact the brief names, with the rest analysed,
  decided in writing, and deliberately not built. Say this first, before being asked.
- ADR-0004's decision (in-process transactional events) is **not reversed** — it is deferred
  unbuilt. If T1 and T2 land early, 5.1–5.4 are the reopen candidates, in that order.
