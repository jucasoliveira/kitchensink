# ADR-0005 — Persistence: repository ports, MongoDB first-class

- **Status:** Accepted
- **Date:** 2026-09-01

## Context

The legacy app persists two ways: a hand-written catalog DAO with externalised SQL
(`CatalogDAOSQL.xml`, 7 statements × 2 dialects, six-way joins for one product page) and CMP 2.0
container-managed persistence for everything else — for which **there is no DDL in the repository
at all** (finding #2). The data model had to be re-derived from `ejb-jar.xml` CMP fields.

Both shapes are document-shaped in disguise: the catalog is a category → product → item tree with
per-locale detail rows, and a purchase order is a classic aggregate that owns its line items,
addresses and card details and is never shared. `AddressEJB`, `ContactInfoEJB`, `CreditCardEJB`
and `LineItemEJB` are each declared in **four** `ejb-jar.xml` files purely because EJB 2.0 CMP
relationships cannot cross jar boundaries (finding #4).

## Decision

1. Domain aggregates are **plain Java** (records/classes, no persistence annotations).
2. Each aggregate has a **repository port** in the application layer.
3. Two adapters implement the ports: **MongoDB** (Spring Data MongoDB) and **JPA/H2**.
4. Selected by profile: `--spring.profiles.active=mongo` (default) or `jpa`.
5. **The Mongo adapter is written slice by slice, alongside the JPA one — not as a day-4
   afterthought.** Deferring the stretch goal to the end is the reliable way to lose it.

## Document design

- **Catalog:** one `products` document per product with `details` embedded per locale, items
  embedded or referenced. The six-way join in `CatalogDAOSQL.xml` becomes one `findById`. Text
  index on localised name/description replaces the legacy `LIKE`-based search.
- **Order:** one `orders` document per purchase order, line items / shipTo / billTo / card
  embedded — the aggregate boundary the CMP model could not express, so the four duplicated value
  objects collapse into one embedded shape each.
- **Inventory** stays a small keyed document (`itemId`, `quantity`) with atomic `$inc` for the
  decrement, which is a genuinely better story than the CMP entity's read-modify-write.

## Consequences

- Two persistence models plus mappers ≈ 25–30% extra per slice. Accepted, because the
  profile-switch demo — same test suite, same golden path, both stores — is the highest-value
  thing this project can show a MongoDB panel.
- **Cut line:** if the schedule slips, the JPA adapter is dropped (Mongo-only) and the decision is
  recorded here. The reverse — dropping Mongo — is not on the table.
- A short note on how a real engagement would move the data (schema analysis → mapping rules →
  Relational Migrator-style transform, not a hand-written script) belongs in the README; the
  seed-data loader in Issue 2.1 is a miniature of exactly that.
