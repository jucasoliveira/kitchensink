# ADR-0002 — Migration scope

- **Status:** Accepted
- **Date:** 2026-09-01

## Context

The brief has an internal contradiction: it points at the **Java Pet Store** repository but then
says to publish "for just 'kitchensink' only". `kitchensink` is the JBoss quickstart used in the
other variant of this challenge — `grep -ril kitchensink petstore1.3.1_02` returns nothing. The
target repository is already named `kitchensink`, so the name is kept; the *content* is Pet Store.

Pet Store is four EARs, 309 `.java` files, 33 EJBs. Migrating all of it in 3.5 days is not
possible, and pretending otherwise is the single biggest risk to the deliverable.

## Decision

**In scope — one Spring Boot application, internally modular**, covering the customer-visible
value stream end to end:

| Legacy | In scope | New module |
| --- | --- | --- |
| `petstore.ear` storefront (catalog, cart, checkout, sign-on, customer) | ✅ | `catalog`, `cart`, `customer`, `order` |
| `opc.ear` order workflow (PO, approval, invoice, process manager) | ✅ business logic, not the deployment boundary | `opc` |
| `supplier.ear` inventory + fulfilment | ✅ business logic | `supplier` |
| `petstoreadmin.ear` web tier (approve/deny) | ✅ replaced by a minimal admin web screen | `admin` |
| `petstoreadmin` **Swing/JNLP client** | ❌ replaced, not ported | — |
| `src/webservices` JAX-RPC duplicate of the JMS path | ❌ dead weight (finding #10) | — |
| JavaMail / SMTP | ❌ disabled by default in the legacy app already (finding #9); ports emit events, no SMTP | — |
| `SignOffEvent` → `SignOffEJBAction` mapping | ❌ unreachable dead config (finding #11) | — |
| i18n | ✅ **partial**: locale-scoped catalog data preserved in the model; UI messages for `en_US` + `ja_JP`; `zh_CN` data-only | `messages_*.properties` |

The four EAR boundaries collapse into **modules inside one deployable**, with the boundaries
enforced by ArchUnit rather than by classloaders. Rationale: the legacy split was a J2EE packaging
artefact, not a scaling decision, and one deployable is demoable on a laptop in 90 seconds. The
module seams are kept explicit so that splitting them back out is a packaging change, not a
rewrite — which is exactly the argument to make about a real, larger migration.

## Deliberate deviations from strict parity

1. **Plaintext passwords → BCrypt** (finding #1). Carrying a plaintext credential store forward
   would be indefensible; this is called out in the README rather than hidden.
2. **Swing admin → web admin screen.** The approval step is load-bearing for the demo (the
   $500 threshold rule), the desktop client is not.
3. **Process boundaries collapsed** (see ADR-0004).

## What "done" means

The golden path runs end to end: browse catalog → sign in → add to cart → check out → order over
threshold parks as PENDING → admin approves → inventory decrements → invoice fans out → order
completes. Every business rule in `01-legacy-architecture.md` §5 has a test.

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
