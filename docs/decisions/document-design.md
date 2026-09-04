# Document design: the catalog tree and the order aggregate

**Issue:** 7.2 (#44) · **Decides:** how the CMP entity graph becomes MongoDB documents ·
**Anchors:** `docs/01-legacy-architecture.md` §6 (ER model), finding #2 (CMP auto-schema)

The question this answers is not "does it fit in a document" — almost anything does — but *where
the aggregate boundary is*, and what the migration removes by drawing it there. Two numbers carry
the argument: the joins that disappear and the duplication that disappears. Both are counted here
rather than asserted, and both are counted from the legacy tree, which is checked in and readable.

---

## 1. The rule used

**One document per aggregate; a separate collection only where the relationship is one-to-many and
the many side is independently addressable.**

That is nearly the rule CMP 2.0 could not express. The legacy already knew which relations were
ownership — it marked them `<cascade-delete/>` — but every entity bean got its own container
generated table regardless, because EJB 2.0 had no notion of an embedded value. So the CMR graph is
not a bad guide to aggregate boundaries; it is a *correct* guide that the container was unable to
act on.

---

## 2. What the duplication actually was

`grep` the CMP entity declarations across every `ejb-jar.xml` in the tree:

| | |
| --- | --- |
| Distinct CMP entity beans | **13** |
| `<entity>` declarations of them | **25** |
| Redundant declarations | **12** (48%) |

The redundancy is entirely in the four value types, each declared once per deployment unit that
needed it, because a CMP entity could not be shared across EARs:

| Bean | Declarations | In |
| --- | --- | --- |
| `AddressEJB` | **5** | `address`, `contactinfo`, `customer`, `purchaseorder`, `supplierpo` |
| `ContactInfoEJB` | **4** | `contactinfo`, `customer`, `purchaseorder`, `supplierpo` |
| `CreditCardEJB` | **3** | `creditcard`, `customer`, `purchaseorder` |
| `LineItemEJB` | **3** | `lineitem`, `purchaseorder`, `supplierpo` |

Fifteen declarations of four types. Each one is a hand-maintained copy of the same field list: a
field added to an address had to be added in five places, and nothing in the build would notice if
it were not.

In the migrated slice each is **one record**, in `customer/domain`, embedded wherever it is needed:
[`Address`](../../src/main/java/com/jucasoliveira/kitchensink/customer/domain/Address.java),
[`ContactInfo`](../../src/main/java/com/jucasoliveira/kitchensink/customer/domain/ContactInfo.java),
[`CreditCard`](../../src/main/java/com/jucasoliveira/kitchensink/customer/domain/CreditCard.java).
`LineItem` is T3 and unbuilt (ADR-0006).

This is finding #4, with the count attached.

---

## 3. The customer aggregate — one document

Seven container-generated tables become one document in one collection.

```
CustomerEJB ──1:1── AccountEJB ──1:1── ContactInfoEJB ──1:1── AddressEJB
     │                    └──1:1── CreditCardEJB
     └──1:1── ProfileEJB
UserEJB (userName = userId, in the signon component)
```

Every one of those relations is `One`-to-`One`, and two carry `<cascade-delete/>`
(`components/customer/src/ejb-jar.xml:280`, `:343`) — the legacy's own statement that account and
contact info are *owned*. So the boundary is not a judgement call: the configuration draws it.

```jsonc
// customers
{
  "_id": "ada",                       // CustomerEJB PK and UserEJB.userName, joined no longer
  "passwordHash": "$2a$10$…",         // UserEJB.password, now BCrypt (finding #1)
  "account": {
    "status": "ACTIVE",
    "contactInfo": { …, "address": { … } },
    "creditCard": {}                  // present and empty: AccountEJB.ejbPostCreate:87-89
  },
  "profile": { "preferredLanguage": "en_US", … }   // sibling, not nested — see below
}
```

Two details worth defending:

- **`profile` is a sibling of `account`, not inside it.** `CustomerEJB.ejbPostCreate:78-84` creates
  `AccountLocal` and `ProfileLocal` side by side and sets both onto the customer: two CMR fields at
  the same level. Nesting profile under account would be a quiet change of the model, so
  `CustomerMongoRoundTripTest.the_profile_is_stored_beside_the_account` pins it.
- **`creditCard` is `{}`, not absent.** An empty subdocument is a row that exists with three null
  columns, which is what `cch.create()` produced. An absent key would mean "no card at all", which
  the legacy never did.

**Reads removed:** loading one shopper was a primary-key read plus six CMR traversals, each of
which the container resolved as a join or a separate select depending on its fetch strategy. It is
now one `findById`.

---

## 4. The catalog tree — three collections, not one

The catalog is where the rule bites the other way, and the contrast is the point.

`CatalogDAOSQL.xml` holds **14 `<SQLStatement>` elements**: seven methods written twice, once for
`cloudscape` and once for `oracle`, because `CatalogDAOFactory.java:58` chose an implementation
from a JNDI env-entry and each DAO carried its own dialect. Six tables:

```
category ──< category_details (locale)
product  ──< product_details  (locale)     product.catid → category
item     ──< item_details     (locale)     item.productid → product
```

`category`/`product`/`item` become **three collections**, and each `*_details` table becomes a
`Map<locale, Details>` **inside** its parent document — one-to-many, but the many side is *not*
independently addressable: nothing ever asks for a `product_details` row except through its
product. That is exactly the case for embedding.

But `product` and `item` stay **separate collections**, not subdocuments of category. Three
reasons, in order of weight:

1. **They are addressed directly.** `GET_ITEM` takes an `itemid` and nothing else. Nesting items
   under categories would turn a primary-key read into a scan with a positional projection.
2. **Growth is unbounded on the wrong axis.** A category holds arbitrarily many products and a
   product arbitrarily many items; the 16MB document limit is not the constraint at Pet Store's
   scale, but the write amplification is — every item edit would rewrite the whole category.
3. **The screens read down the tree one level at a time.** `category.screen` needs products, not
   items.

So the catalog is a *tree of documents*, not a document. The nesting stops where addressability
starts.

### The join that disappears

`GET_ITEM`, the worst of the seven:

```sql
select catid, a.productid, name, b.image, b.descn, attr1..attr5, listprice, unitcost
  from (((item a join item_details b on a.itemid=b.itemid)
      join product_details c on a.productid=c.productid)
      join product d on d.productid=c.productid and b.locale = c.locale)
  where b.locale = ? and a.itemid = ?
```

**Four tables, three joins, and a locale correlation** (`b.locale = c.locale`) restated as a join
predicate — and it still returns **one locale per execution**.

The migrated equivalent is `findItem("EST-1")`: one `findById`, returning **every** locale the seed
holds. Pinned by `CatalogRepositoryContract.get_item_is_one_find_by_id`.

| | Legacy | Migrated |
| --- | --- | --- |
| Tables touched | 4 | 1 collection |
| Joins | 3 | 0 |
| Locales returned | 1 | all |
| Statements maintained | 2 (one per dialect) | 1 port method |

### What it costs, stated honestly

The projection `GET_ITEM` returned flattened item *and* product columns into one row. The port
returns aggregates, so the item page assembles the same projection from **two** reads — the item,
then its product. `CatalogRepositoryContract.the_product_page_projection_takes_two_reads` exists to
say so. Four of the six join predicates vanish with the details tables; the item→product
association becomes a second `findById` rather than a join. That is the trade, and it is a good
one, but it is a trade.

---

## 5. The order aggregate — designed, not built

T3 under ADR-0006, so this is a design note and there is no code behind it. It is here because it
is the case that would have made the strongest argument, and leaving it out would look like
avoiding it.

```
PurchaseOrderEJB ──1:1── ContactInfoEJB ──1:1── AddressEJB
       ├──1:1── CreditCardEJB
       └──1:Many── LineItemEJB
```

`purchaseorder/src/ejb-jar.xml` declares `AddressEJB`, `ContactInfoEJB`, `CreditCardEJB` and
`LineItemEJB` *again* — four of the twelve redundant declarations counted in §2 are in this one
file.

**One document.** Every relation is ownership, including the one-to-many: a line item has no
identity outside its order, is never addressed alone, and the count is bounded by what a person
puts in a cart. This is the textbook embed, and it is also the case where the document model buys
something the relational one cannot — **an order is written atomically**. In the legacy, creating a
purchase order meant inserting into `purchaseorder`, `contactinfo`, `address`, `creditcard` and *n*
rows of `lineitem`, and correctness depended on the container's transaction spanning all of them.
As one document it is a single write, atomic by construction.

That is the argument the order aggregate would have made, and the reason ADR-0004's async workflow
was designed before it was deferred.

---

## 6. Summary

| | Legacy | Migrated |
| --- | --- | --- |
| CMP entity declarations | 25 (for 13 distinct beans) | — |
| Value types declared more than once | 4, in 15 places | 4 records, 1 place each |
| Customer: tables → store | 7 tables | 1 document |
| Catalog: tables → store | 6 tables | 3 collections |
| Catalog SQL statements maintained | 14 (7 × 2 dialects) | 7 port methods |
| `GET_ITEM` | 4 tables, 3 joins, 1 locale | 1 `findById`, all locales |
| Order (unbuilt): tables → store | 5+ tables, *n* line-item rows | 1 document, 1 atomic write |

The claim the whole slice rests on is not that documents are faster. It is that the aggregate
boundaries were *already in the legacy configuration*, marked `<cascade-delete/>`, and the
relational store could not act on them. See [`two-stores.md`](two-stores.md) for the port/adapter
mechanics and [`data.md`](data.md) for the ER model.
