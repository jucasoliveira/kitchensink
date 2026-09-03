# Legacy → new traceability matrix

> Issue [2.3](https://github.com/jucasoliveira/kitchensink/issues/11). Legacy anchor: `mappings.xml`
> and the EJB inventory in [`01-legacy-architecture.md` §4](01-legacy-architecture.md#4-business-tier--ejb-inventory-33-beans).
> Acceptance criteria: every in-scope legacy component maps to a new component, or to an explicit
> "dropped, because…".

This is the row-level complement to [ADR-0006](adr/0006-deliverable-scope-kitchensink-slice.md),
which decides scope at the tier level (T1/T2/T3 + exclusions). This document walks every
component named in the EJB inventory, the WAF routing table and the messaging topology, one row
each, and says what happened to it. It is the artefact [`03-migration-plan.md` §4](03-migration-plan.md#4-definition-of-done-per-issue)
requires every issue to add a row to — new rows should be filed here as issues close, not batched.

**Status legend**

| Status | Meaning |
| --- | --- |
| ✅ **Built** | Shipped in `src/main`. Cited by file. |
| 🔜 **Planned** | In T1 or T2 (must/should ship), not built yet. Cited by open issue. |
| 🧭 **Designed, not built** | In T3 — cart, checkout, order workflow, approval, supplier. Analysis stands in [ADR-0004](adr/0004-async-workflow.md) and `01-legacy-architecture.md` §5; issue closed and parked in the `Deferred — designed, not built` milestone per [ADR-0006](adr/0006-deliverable-scope-kitchensink-slice.md). Not "dropped" — reopenable. |
| ❌ **Dropped** | Excluded outright per ADR-0006's exclusion table or a finding in `01-legacy-architecture.md` §7. Reason given inline. |

As of this writing, T1's walking-skeleton slice (customer → account → contact info → address,
sign-on, registration, one REST resource) is built, as is T2's catalog read path up to the port:
seed data (2.1), the locale-scoped aggregates (3.1), the `CatalogRepository` port and its MongoDB
adapter (3.2) and `CatalogService` with browse, search and paging (3.4). Still planned: T1's
remaining widening (credit card, profile, full URL-protection parity), the JPA adapter (3.3) and
the catalog screens (3.5).

---

## 1. Session beans (10)

| Legacy component | New component / status | Note |
| --- | --- | --- |
| `ShoppingControllerEJB` (SFSB) | 🧭 Designed, not built | Session-scoped cart controller. Folds into issue 5.1 (`Cart`) if T3 reopens. |
| `ShoppingClientFacadeEJB` (SFSB) | 🧭 Designed, not built | Facade over cart/customer/sign-on for the web tier; same fate as `ShoppingControllerEJB`. |
| `ShoppingCartEJB` (SFSB) | 🧭 Designed, not built | Issue 5.1, `Cart` (session-scoped). |
| `CatalogEJB` (stateless, JDBC DAO) | ✅ Built (partial) / 🔜 Planned | The read path is in: [`Category`](../src/main/java/com/jucasoliveira/kitchensink/catalog/domain/Category.java)/[`Product`](../src/main/java/com/jucasoliveira/kitchensink/catalog/domain/Product.java)/[`Item`](../src/main/java/com/jucasoliveira/kitchensink/catalog/domain/Item.java) with locale-scoped details (issue 3.1), behind [`CatalogRepository.java`](../src/main/java/com/jucasoliveira/kitchensink/catalog/application/CatalogRepository.java) (issue 3.2). Browse, search and paging landed with issue 3.4 ([`CatalogService.java`](../src/main/java/com/jucasoliveira/kitchensink/catalog/application/CatalogService.java), [`CatalogPage.java`](../src/main/java/com/jucasoliveira/kitchensink/catalog/application/CatalogPage.java)); the JPA adapter (issue 3.3) is still open. The 3.4 divergences are itemised in §5, `CatalogHelper` row. Seed data landed in issue 2.1 ([`LegacyCatalogSeed.java`](../src/test/java/com/jucasoliveira/kitchensink/catalog/LegacyCatalogSeed.java), [`CatalogSeeder.java`](../src/main/java/com/jucasoliveira/kitchensink/catalog/adapter/persistence/mongo/CatalogSeeder.java)). |
| `SignOnEJB` (stateless) | ✅ Built | [`SecurityConfig.java`](../src/main/java/com/jucasoliveira/kitchensink/shared/security/SecurityConfig.java) + [`CustomerUserDetailsService.java`](../src/main/java/com/jucasoliveira/kitchensink/customer/adapter/security/CustomerUserDetailsService.java) — Spring Security filter chain replaces the EJB + `SignOnFilter` combo (issue 1.8; full URL-protection parity is issue 4.2, planned). |
| `UniqueIdGeneratorEJB` (stateless) | 🧭 Designed, not built | Issue 5.3, order id generator (`"1001"` prefix + counter). Needed only once checkout exists. |
| `ProcessManagerEJB` (stateless) | 🧭 Designed, not built | Issue 6.2, order status tracking. Depends on ADR-0004's in-process event model. |
| `AsyncSenderEJB` (stateless) | 🧭 Designed, not built | Issue 6.1, transactional event publisher — the modern replacement for XML-over-JMS. |
| `OrderFulfillmentFacadeEJB` (stateless) | 🧭 Designed, not built | Issue 6.5, supplier inventory decrement + invoice emission. |
| `OPCAdminFacadeEJB` (stateless) | 🧭 Designed, not built | Issue 6.4, admin approve/deny screen — replaces the Swing client's one load-bearing function. |

## 2. CMP 2.0 entity beans (13)

| Legacy component | New component / status | Note |
| --- | --- | --- |
| `UserEJB` (userName, password) | ✅ Built, with a deviation | [`PasswordHash.java`](../src/main/java/com/jucasoliveira/kitchensink/customer/domain/PasswordHash.java) — BCrypt, not the plaintext `password.equals(getPassword())` at `UserEJB.java:88` (finding #1). Deliberate parity deviation, called out in the README per ADR-0006. |
| `CustomerEJB` (userId) | ✅ Built | [`Customer.java`](../src/main/java/com/jucasoliveira/kitchensink/customer/domain/Customer.java) — aggregate root, issue 1.7. |
| `AccountEJB` (status) | ✅ Built | [`Account.java`](../src/main/java/com/jucasoliveira/kitchensink/customer/domain/Account.java) — embedded value, not a separate CMP entity. |
| `ProfileEJB` (preferredLanguage, favoriteCategory, myListPreference, bannerPreference) | 🔜 Planned | Issue 4.5, customer/profile screens + `MessageSource` (en_US, ja_JP). No `Profile` type exists yet. |
| `ContactInfoEJB` | ✅ Built | [`ContactInfo.java`](../src/main/java/com/jucasoliveira/kitchensink/customer/domain/ContactInfo.java) — embedded value; collapses the 4-jar duplication (finding #4). |
| `AddressEJB` | ✅ Built | [`Address.java`](../src/main/java/com/jucasoliveira/kitchensink/customer/domain/Address.java) — embedded value, same collapse. |
| `CreditCardEJB` | 🔜 Planned | Issue 4.1, widen the customer aggregate. Not yet an embedded value in `Customer`. |
| `PurchaseOrderEJB` | 🧭 Designed, not built | Issue 5.4, `PurchaseOrder` aggregate + empty-cart rule. |
| `LineItemEJB` | 🧭 Designed, not built | Embedded in the (unbuilt) `PurchaseOrder`/`SupplierOrder` aggregates, issues 5.4/6.5. |
| `SupplierOrderEJB` | 🧭 Designed, not built | Issue 6.5, supplier inventory decrement. |
| `InventoryEJB` (itemId, quantity) | 🧭 Designed, not built | Issue 6.5, atomic `$inc` on Mongo per the acceptance criteria. |
| `ManagerEJB` (orderId, status) | 🧭 Designed, not built | Issue 6.2, order status; the `PENDING → APPROVED\|DENIED → COMPLETED` lifecycle is one of the 7 parity rules (issue 2.2). |
| `CounterEJB` | 🧭 Designed, not built | Issue 5.3, backs the order id generator. |

## 3. Message-driven beans (8)

All eight are queue/topic consumers in the async order workflow (`01-legacy-architecture.md` §5).
Four carry real business logic and are designed-not-built with the rest of T3; four exist only to
send mail, which is excluded outright regardless of tiering.

| Legacy component | New component / status | Note |
| --- | --- | --- |
| `PurchaseOrderMDB` | 🧭 Designed, not built | Issues 6.2–6.3; owns the auto-approve-under-$500/¥50000 rule (`PurchaseOrderMDB.java:183`), one of the 7 parity rules. |
| `OrderApprovalMDB` | 🧭 Designed, not built | Issue 6.4, feeds the admin approve/deny screen. |
| `InvoiceMDB` | 🧭 Designed, not built | Issue 6.6; one of two independent subscribers on the invoice topic — the fan-out is load-bearing and named in the parity rules. |
| `SupplierOrderMDB` | 🧭 Designed, not built | Issue 6.5, inventory decrement. |
| `MailInvoiceMDB` | ❌ Dropped | JavaMail/SMTP excluded outright — already disabled by default in the legacy app (finding #9: `SendConfirmationMail`/`SendApprovalMail`/`SendCompletedOrderMail` all `false`), so parity does not require it. |
| `MailOrderApprovalMDB` | ❌ Dropped | Same reason. |
| `MailCompletedOrderMDB` | ❌ Dropped | Same reason. |
| `MailerMDB` | ❌ Dropped | Same reason — this is the terminal JavaMail sender all three `Mail*MDB` beans route through. |

## 4. Web tier — `mappings.xml` routing table (the 7 URLs)

Anchor: `apps/petstore/src/docroot/WEB-INF/mappings.xml`, walked in
`01-legacy-architecture.md` §3.

| Legacy URL → chain | New component / status | Note |
| --- | --- | --- |
| `createuser.do` → `CreateUserHTMLAction` → `CreateUserEvent` → `CreateUserEJBAction` → `create_customer.screen` | ✅ Built | [`CustomerController.java`](../src/main/java/com/jucasoliveira/kitchensink/customer/adapter/web/CustomerController.java) (Thymeleaf form, issue 1.9) + [`CustomerRegistration.java`](../src/main/java/com/jucasoliveira/kitchensink/customer/application/CustomerRegistration.java) (application service). Duplicate-account rule is issue 4.4, planned. |
| `customer.do` / `createcustomer.do` → `CustomerHTMLAction` → `CustomerEvent` → `CustomerEJBAction` → `customer.screen` | 🔜 Planned | Profile/customer screens, issue 4.5. |
| `changelocale.do` → `ChangeLocaleHTMLAction` → `ChangeLocaleEvent` → `ChangeLocaleEJBAction` | 🔜 Planned | Locale switch is part of issue 4.5 (`MessageSource`, en_US + ja_JP). |
| `signoff.do` → `SignOffHTMLAction` → `ChangeLocaleEvent` (not `SignOffEvent`) → `ChangeLocaleEJBAction` → `signoff.screen` | ❌ Dropped as mapped; sign-out behaviour is a Spring Security concern | The `mappings.xml` entry for `SignOffEvent` → `SignOffEJBAction` is dead configuration — `SignOffHTMLAction:69` actually raises `ChangeLocaleEvent`, so that row is unreachable (finding #11). Not ported; standing argument for validating config-driven dispatch tables at startup. |
| `cart.do` → `CartHTMLAction` → `CartEvent` → `CartEJBAction` → `cart.screen` | 🧭 Designed, not built | Issue 5.2. |
| `order.do` → `OrderHTMLAction` → `OrderEvent` → `OrderEJBAction` → `order_complete.screen` | 🧭 Designed, not built | Issue 5.4; owns the empty-cart-cannot-be-ordered rule, one of the 7 parity rules. |
| Catalog browsing (`category`/`product`/`item`/search) — bypasses the controller, JSPs call `CatalogHelper` directly | 🔜 Planned | Issues 3.4–3.5 (`CatalogService`, Thymeleaf screens). The bypass itself is the deviation: four JSPs reaching a DAO directly through a session-scoped bean is the Fast Lane Reader pattern, and it is why this row has no `mappings.xml` chain to trace. Both catalog screens and the REST facade will go through `CatalogService`, which ArchUnit already enforces (`the_web_adapter_does_not_reach_into_persistence`). |

## 5. Other web-tier and cross-cutting components

| Legacy component | New component / status | Note |
| --- | --- | --- |
| `SignOnFilter` + `signon-config.xml` | ✅ Built (partial) / 🔜 Planned (full parity) | [`SecurityConfig.java`](../src/main/java/com/jucasoliveira/kitchensink/shared/security/SecurityConfig.java) — one protected URL live (issue 1.8); the full `signon-config.xml` URL set is issue 4.2. |
| `TemplateServlet` + `template.jsp` + `screendefinitions_*.xml` | ✅ Built (partial) / 🔜 Planned | Thymeleaf layout, issue 1.9 done for the registration form/list; the banner/sidebar/body/footer composition for catalog is issue 3.5. |
| — (no REST endpoint in the legacy) | ✅ Built | [`CustomerResource.java`](../src/main/java/com/jucasoliveira/kitchensink/customer/adapter/web/CustomerResource.java) — `POST /api/customers`, `GET /api/customers/{id}` (issue 4.7). One of the two empty rows ADR-0006 calls out as the most interesting thing in its own mapping table. |
| — (no tests in the legacy) | ✅ Built | `src/test/**` — Testcontainers, ArchUnit, `@Tag("parity")` characterization tests (issues 1.10, 2.2, 2.4, 2.5). The other empty row from ADR-0006. |
| `CatalogDAOSQL.xml` (7 statements × 2 dialects) + `CatalogDAOFactory` | ✅ Built (`mongo`) / 🔜 Planned (`jpa`) | Issue 3.2. All 7 statements are 7 methods on [`CatalogRepository.java`](../src/main/java/com/jucasoliveira/kitchensink/catalog/application/CatalogRepository.java), implemented once by [`MongoCatalogRepository.java`](../src/main/java/com/jucasoliveira/kitchensink/catalog/adapter/persistence/mongo/MongoCatalogRepository.java) — the two dialects and the factory that chose between them are gone with the SQL. `GET_ITEM`'s four-table, locale-correlated join is one `findById` ([`MongoCatalogRepositoryTest.java`](../src/test/java/com/jucasoliveira/kitchensink/catalog/adapter/persistence/mongo/MongoCatalogRepositoryTest.java)); `SEARCH_ITEMS` is one `$lookup` aggregation rather than `$text`, because a stemmed word index cannot express `like '%dog%'` and has no analyzer for the `ja_JP`/`zh_CN` rows ([`MongoCatalogSearchTest.java`](../src/test/java/com/jucasoliveira/kitchensink/catalog/adapter/persistence/mongo/MongoCatalogSearchTest.java)). Two deviations, both pinned by tests: `order by name` moves to the service, since `name` is locale-scoped and a Mongo collation would not match Cloudscape's for `ja_JP`/`zh_CN`; and a `%` typed into search is a literal here, where `LIKE` made it a wildcard. **Gap:** under the `jpa` profile the port has no adapter until issue 3.3 (#17), so catalog reads are `mongo`-only. Issue 3.4 answers the follow-on risk by putting `@Profile("mongo")` on `CatalogService`, the same guard [`CustomerRegistration.java`](../src/main/java/com/jucasoliveira/kitchensink/customer/application/CustomerRegistration.java) carries, so injecting the port does not stop `jpa` from booting; both guards come off when 3.3 lands. |
| `CatalogHelper.java` (Fast Lane Reader bean) + `catalog/model/Page.java` | ✅ Built, with three divergences | Issue 3.4. The JSP bean's seven getters become seven parameterised methods on [`CatalogService.java`](../src/main/java/com/jucasoliveira/kitchensink/catalog/application/CatalogService.java), so a session-scoped `<jsp:useBean>` can no longer be left half-set between requests; `Page` becomes [`CatalogPage.java`](../src/main/java/com/jucasoliveira/kitchensink/catalog/application/CatalogPage.java), a record rather than a Spring Data `Page`, because the legacy carries no total — only `hasNext`, learned by reading one row past the page. Paging stays in memory on purpose: `GenericCatalogDAO.java:246` ran every statement unbounded against a `TYPE_SCROLL_INSENSITIVE` cursor and skipped client-side with `rs.absolute(start + 1)`, so this is the legacy behaviour, not a stand-in for `$skip`/`$limit`. **Three quirks preserved**, each pinned by a test naming it as a decision ([`CatalogPageTest.java`](../src/test/java/com/jucasoliveira/kitchensink/catalog/application/CatalogPageTest.java)): the `do`/`while` at `GenericCatalogDAO.java:255` adds a row before testing `count`, so `count=0` returns one row; `Page.EMPTY_PAGE` was built with `start` 0, so paging past the end loses Previous as well as Next; and `Page.java:89` subtracts the *current* page's size, so Previous off a short last page steps back short. **Three divergences**, documented against `file:line` in [`CatalogServiceTest.java`](../src/test/java/com/jucasoliveira/kitchensink/catalog/application/CatalogServiceTest.java) and summarised in [`decisions/catalog-browsing.md`](decisions/catalog-browsing.md): (a) *ordering* — `order by name` (`CatalogDAOSQL.xml:75,89`) is kept for categories and products on the requested locale's name, but `GET_ITEMS` and `SEARCH_ITEMS` have no `ORDER BY` at all, which is not a contract you can page over, so items and search results are sorted by id; (b) *locale scoping* — `where b.locale = ?` over a join that also required `b.locale = c.locale` (`CatalogDAOSQL.xml:114-119`) dropped untranslated rows for free, and with details embedded per document the service must filter explicitly, still with no `en_US` fallback because the legacy had none; (c) *mixed-case search* — `GenericCatalogDAO.java:361-365` wrapped the keyword verbatim and compared it to `lower(name)`, so any capital letter matched nothing and searching "Angelfish" in the shipped app returned an empty page. `CloudscapeCatalogDAO.java:392` lower-cases, but `ejb-jar.xml:61` and `web.xml:194` both deploy the *Generic* DAO. This one is a defect fixed rather than a behaviour preserved: `CatalogService.keywords()` lower-cases, where the disabled DAO did. Tokenizing is otherwise faithful — `StringTokenizer` on whitespace into a `HashSet` (`GenericCatalogDAO.java:345-349`), empty set ⇒ empty page. |
| `PopulateSQL.xml`, `Populate-UTF8.xml` (catalog seed data) | ✅ Built | Issue 2.1: [`LegacyCatalogSeed.java`](../src/test/java/com/jucasoliveira/kitchensink/catalog/LegacyCatalogSeed.java), [`src/main/resources/seed/catalog.json`](../src/main/resources/seed/catalog.json), loaded by [`CatalogSeeder.java`](../src/main/java/com/jucasoliveira/kitchensink/catalog/adapter/persistence/mongo/CatalogSeeder.java). Verbatim-copy and category/product/item-count parity are asserted in [`LegacySeedCopyIsVerbatimTest.java`](../src/test/java/com/jucasoliveira/kitchensink/catalog/LegacySeedCopyIsVerbatimTest.java) and [`LegacyCatalogSeedCharacterizationTest.java`](../src/test/java/com/jucasoliveira/kitchensink/catalog/LegacyCatalogSeedCharacterizationTest.java). |
| `PetStoreAdminClient.java` (Swing/JNLP) | ❌ Dropped | A desktop client is not a modernisation target. Its one load-bearing function, order approval, moved to T3's `OPCAdminFacadeEJB` → admin approve/deny screen (issue 6.4), designed not built. |
| `src/webservices` (JAX-RPC opc↔supplier duplicate) | ❌ Dropped | Dead weight: a second implementation of the opc↔supplier path, itself deferred with T3 (finding #10). |
| JavaMail / SMTP (`param/Send*Mail` env-entries) | ❌ Dropped | Already disabled by default in the legacy app (finding #9); parity baseline does not include it. |
| The four EAR boundaries (`petstore`, `opc`, `supplier`, `admin`) | ❌ Dropped as classloader boundaries; ✅ preserved as module seams | Collapse into modules inside one deployable (`customer`, `catalog`, `shared`, plus the unbuilt `cart`/`order`/`opc`/`supplier`/`admin` named in the ArchUnit rule set), enforced by ArchUnit rather than four classloaders. Splitting them back out later is a packaging change, not a rewrite. |

---

## Summary

| Status | Count | Of 33 EJBs |
| --- | --- | --- |
| ✅ Built | 6 | `UserEJB`, `CustomerEJB`, `AccountEJB`, `ContactInfoEJB`, `AddressEJB`, `SignOnEJB` |
| 🔜 Planned (T1/T2, open issue) | 3 | `CatalogEJB`, `ProfileEJB`, `CreditCardEJB` |
| 🧭 Designed, not built (T3) | 20 | The remaining session beans, entity beans and all 4 non-mail MDBs |
| ❌ Dropped | 4 | The 4 `Mail*MDB` beans |

The web tier, messaging topology and cross-cutting rows (§4–5) are additional legacy components
outside the 33-bean count — `mappings.xml` routes, `SignOnFilter`, the catalog DAO, seed data, the
Swing client, the JAX-RPC duplicate, and JavaMail — each with its own row above.

Every 🧭 row is reopenable, in the order ADR-0006 fixes: 5.1 → 5.4 (cart, then checkout) before any
other T3 issue. Every ❌ row is excluded independent of schedule, per ADR-0006's exclusion table.
