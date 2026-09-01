# Java Pet Store 1.3.1_02 — Legacy Architecture

> Reverse-engineered from source at `petstore1.3.1_02/src`.
> Every claim below is anchored to a file path so it can be verified during walkthrough.

## 1. What this application is

Sun's **J2EE BluePrints Java Pet Store Demo, version 1.3.1_02** (build date Jan 2003). It is not
one application — it is **four separately deployed EAR files** that communicate through JMS
queues, plus a shared library of 16 reusable "components".

| EAR | Source root | Role |
| --- | --- | --- |
| `petstore.ear` | `src/apps/petstore` | Customer-facing storefront (JSP + servlet + session EJBs) |
| `opc.ear` | `src/apps/opc` | Order Processing Center — async order workflow (MDBs) |
| `supplier.ear` | `src/apps/supplier` | Supplier / warehouse — inventory + shipping |
| `petstoreadmin.ear` | `src/apps/admin` | Admin console — order approval (Swing/JNLP client + web tier) |

Scale: **309 `.java` files, 98 JSPs, 95 XML descriptors**, 33 EJBs.

### Runtime target (the reason it does not run today)

`petstore1.3.1_02/docs/installing.html` requires:

- **J2SE SDK 1.4.1**
- **J2EE SDK 1.3.1** (Sun's Reference Implementation app server) — provides the EJB container,
  JMS provider, and JavaMail
- **Cloudscape** (the RI's bundled DB, ancestor of Apache Derby)

All deployment descriptors are RI-proprietary (`sun-j2ee-ri.xml` in every module). Bootstrap is
Ant 1.x driven by `setup.xml` (`create_jms_queues`, `create_petstore_db`, `create_supplier_db`,
`create_opc_db`, `deploy`).

---

## 2. Container / deployment view

```mermaid
flowchart LR
    subgraph client["Clients"]
        BR["Browser<br/>(JSP/HTML, i18n en_US/ja_JP/zh_CN)"]
        SW["Swing admin client<br/>PetStoreAdminClient.java<br/>(JNLP / HTTP POST XML)"]
    end

    subgraph ri["J2EE 1.3.1 Reference Implementation server"]
        subgraph ps["petstore.ear"]
            PSW["PetStoreWAR<br/>MainServlet + TemplateServlet"]
            PSE["ShoppingControllerEJB (SFSB)<br/>ShoppingClientFacadeEJB (SFSB)"]
        end
        subgraph opc["opc.ear"]
            OPCM["6 Message-Driven Beans"]
            OPCF["OPCAdminFacadeEJB (SLSB)"]
        end
        subgraph sup["supplier.ear"]
            SUPM["SupplierOrderMDB"]
            SUPF["OrderFulfillmentFacadeEJB (SLSB)"]
            INV["InventoryEJB (CMP entity)"]
        end
        subgraph adm["petstoreadmin.ear"]
            ADMW["AdminRequestProcessor<br/>(HTTP POST XML endpoint)"]
        end
        MAIL["MailerMDB → JavaMail"]
    end

    subgraph infra["Infrastructure"]
        JMS[("JMS queues<br/>jms/petstore · jms/opc<br/>jms/supplier · jms/admin")]
        DB[("Cloudscape<br/>jdbc/petstore · jdbc/opc · jdbc/supplier")]
        SMTP["SMTP server"]
    end

    BR -->|"*.do / *.screen"| PSW
    SW -->|"HTTP POST XML"| ADMW
    PSW --> PSE
    PSE --> JMS
    JMS --> OPCM
    OPCM --> JMS
    JMS --> SUPM
    SUPM --> INV
    SUPF --> JMS
    ADMW --> OPCF
    OPCF --> JMS
    JMS --> MAIL
    MAIL --> SMTP
    PSE --> DB
    OPCM --> DB
    INV --> DB
```

**Key point for migration risk:** the four EARs are only ever coupled through **JMS text messages
carrying XML documents** (`src/components/xmldocuments`) and through a shared relational schema.
That seam is what makes an incremental, strangler-style migration possible.

---

## 3. Web tier — the WAF (Web Application Framework)

The storefront does not use Struts; it uses BluePrints' own MVC micro-framework, `src/waf`.
This is the single most important thing to understand about the codebase, because **almost all
"where does the logic live?" questions resolve here**.

```mermaid
sequenceDiagram
    participant B as Browser
    participant EF as EncodingFilter
    participant SF as SignOnFilter
    participant MS as MainServlet (*.do)
    participant RP as RequestProcessor
    participant HA as HTMLAction<br/>(web tier)
    participant WC as ShoppingWebController
    participant SC as ShoppingControllerEJB (SFSB)
    participant SM as StateMachine
    participant EA as EJBAction<br/>(business tier)
    participant SFM as ScreenFlowManager
    participant TS as TemplateServlet (*.screen)

    B->>EF: GET/POST /cart.do
    EF->>SF: force UTF-8
    SF->>SF: is URL protected?<br/>(signon-config.xml)
    SF->>MS: pass / or redirect to signon.screen
    MS->>RP: processRequest(request)
    RP->>HA: perform(request) → Event
    Note over HA: CartHTMLAction:82<br/>parses HTTP params → CartEvent
    RP->>WC: handleEvent(Event)
    WC->>SC: processEvent(Event)
    SC->>SM: processEvent(Event)
    SM->>EA: perform(Event) → EventResponse
    Note over EA: CartEJBAction<br/>mutates ShoppingCartEJB
    EA-->>RP: EventResponse (into request scope)
    MS->>SFM: forwardToNextScreen()
    SFM->>TS: forward to "cart.screen"
    TS->>B: template.jsp composes<br/>banner + sidebar + body + footer
```

### The three configuration files that *are* the routing table

| File | What it decides |
| --- | --- |
| `apps/petstore/src/docroot/WEB-INF/mappings.xml` | URL → `HTMLAction` → next screen; Event class → `EJBAction`; exception class → error screen |
| `apps/petstore/src/docroot/WEB-INF/screendefinitions_en_US.xml` | Screen name → JSP fragments (19 screens; `_ja_JP` and `_zh_CN` variants exist) |
| `apps/petstore/src/docroot/WEB-INF/signon-config.xml` | Which URLs require authentication |

### The 7 request URLs

| URL | Web action | Event | EJB action | Next screen |
| --- | --- | --- | --- | --- |
| `cart.do` | `CartHTMLAction` | `CartEvent` | `CartEJBAction` | `cart.screen` |
| `order.do` | `OrderHTMLAction` | `OrderEvent` | `OrderEJBAction` | `order_complete.screen` |
| `customer.do` | `CustomerHTMLAction` | `CustomerEvent` | `CustomerEJBAction` | `customer.screen` |
| `createuser.do` | `CreateUserHTMLAction` | `CreateUserEvent` | `CreateUserEJBAction` | `create_customer.screen` |
| `createcustomer.do` | `CustomerHTMLAction` | `CustomerEvent` | `CustomerEJBAction` | via `CreateUserFlowHandler` |
| `signoff.do` | `SignOffHTMLAction` | `ChangeLocaleEvent` (!) | `ChangeLocaleEJBAction` | `signoff.screen` |
| `changelocale.do` | `ChangeLocaleHTMLAction` (WAF) | `ChangeLocaleEvent` | `ChangeLocaleEJBAction` | via `ClientStateFlowHandler` |

Catalog browsing (`category`, `product`, `item`, search) is **not** in this table — those screens
are rendered straight from JSPs that call `CatalogHelper`
(`components/catalog/src/.../client/CatalogHelper.java`), bypassing the controller entirely.

### Navigation cheat-sheet

| Question | Answer |
| --- | --- |
| Front controller | `waf/src/.../controller/web/MainServlet.java:108` |
| Web-tier dispatch | `waf/src/.../controller/web/RequestProcessor.java:117` |
| Business-tier dispatch | `waf/src/.../controller/ejb/StateMachine.java:92` |
| Screen forwarding + error screens | `waf/src/.../controller/web/flow/ScreenFlowManager.java:164` |
| Templating engine | `waf/src/view/template/.../TemplateServlet.java` + `docroot/template.jsp` |
| Authentication filter | `components/signon/src/.../web/SignOnFilter.java` |
| Catalog SQL | `apps/petstore/src/docroot/CatalogDAOSQL.xml` |
| Seed data + catalog DDL | `apps/petstore/src/docroot/populate/PopulateSQL.xml`, `Populate-UTF8.xml` |
| JNDI name constants | `*/util/JNDINames.java` (one per module) |

---

## 4. Business tier — EJB inventory (33 beans)

```mermaid
flowchart TB
    subgraph sess["Session beans"]
        SCE["ShoppingControllerEJB<br/>(stateful)"]
        SCF["ShoppingClientFacadeEJB<br/>(stateful)"]
        CART["ShoppingCartEJB<br/>(stateful)"]
        CAT["CatalogEJB<br/>(stateless, JDBC DAO)"]
        SON["SignOnEJB (stateless)"]
        UID["UniqueIdGeneratorEJB (stateless)"]
        PM["ProcessManagerEJB (stateless)"]
        ASY["AsyncSenderEJB (stateless)"]
        OFF["OrderFulfillmentFacadeEJB (stateless)"]
        OAF["OPCAdminFacadeEJB (stateless)"]
    end

    subgraph ent["CMP 2.0 entity beans"]
        USER["UserEJB<br/>userName, password"]
        CUST["CustomerEJB (userId)"]
        ACC["AccountEJB (status)"]
        PROF["ProfileEJB<br/>preferredLanguage, favoriteCategory,<br/>myListPreference, bannerPreference"]
        CI["ContactInfoEJB"]
        ADDR["AddressEJB"]
        CC["CreditCardEJB"]
        PO["PurchaseOrderEJB"]
        LI["LineItemEJB"]
        SPO["SupplierOrderEJB"]
        INV["InventoryEJB<br/>itemId, quantity"]
        MGR["ManagerEJB<br/>orderId, status"]
        CTR["CounterEJB"]
    end

    subgraph mdb["Message-driven beans"]
        POMDB["PurchaseOrderMDB"]
        OAMDB["OrderApprovalMDB"]
        INVMDB["InvoiceMDB"]
        SOMDB["SupplierOrderMDB"]
        M1["MailInvoiceMDB"]
        M2["MailOrderApprovalMDB"]
        M3["MailCompletedOrderMDB"]
        MAILER["MailerMDB"]
    end

    SCE --> SCF
    SCF --> CART
    SCF --> CUST
    SCF --> SON
    CUST --> ACC
    CUST --> PROF
    ACC --> CI
    ACC --> CC
    CI --> ADDR
    PO --> LI
    PO --> CI
    PO --> CC
    SPO --> LI
    POMDB --> PO
    POMDB --> PM
    PM --> MGR
    UID --> CTR
    SOMDB --> INV
```

**Note the duplication:** `AddressEJB`, `ContactInfoEJB`, `CreditCardEJB` and `LineItemEJB` are
each declared in *multiple* `ejb-jar.xml` files (customer, contactinfo, purchaseorder,
supplierpo) because EJB 2.0 CMP relationships cannot cross jar boundaries. In a modern rewrite
these collapse into single embeddable value objects — a concrete simplification win.

### Persistence is split two ways

- **Catalog** (read-heavy, denormalised, i18n) uses a hand-written **DAO with externalised SQL**:
  `CatalogDAOSQL.xml` holds 7 named statements per database dialect (`cloudscape`, `oracle`),
  selected at runtime by `CatalogDAOFactory`. Tables: `category`, `category_details`, `product`,
  `product_details`, `item`, `item_details` — the `_details` tables carry the `locale` column.
- **Everything else** uses **CMP 2.0 container-managed persistence** — the schema is generated by
  the container, not checked in. This is a migration hazard: *there is no authoritative DDL in
  the repo for the customer/order tables.*

---

## 5. Order fulfilment — the asynchronous workflow

This is the part of the system with real business logic and the part most at risk in a migration.

```mermaid
sequenceDiagram
    autonumber
    participant U as Customer
    participant PS as petstore.ear<br/>OrderEJBAction
    participant Q1 as jms/opc/OrderQueue
    participant PO as opc<br/>PurchaseOrderMDB
    participant PM as ProcessManagerEJB
    participant AD as Admin (Swing)
    participant Q2 as jms/opc/OrderApprovalQueue
    participant OA as opc<br/>OrderApprovalMDB
    participant SUP as supplier.ear<br/>SupplierOrderMDB
    participant Q3 as jms/opc/InvoiceTopic
    participant IN as opc<br/>InvoiceMDB
    participant ML as MailerMDB

    U->>PS: order.do (checkout)
    PS->>PS: UniqueIdGeneratorEJB → orderId
    PS->>PS: build PurchaseOrder from cart
    Note over PS: throws ShoppingCartEmptyOrderException<br/>if cart is empty
    PS->>Q1: AsyncSenderEJB.sendAMessage(po.toXML())
    PS->>PS: cart.empty()
    Q1->>PO: onMessage
    PO->>PO: PurchaseOrderEJB.create()
    PO->>PM: createManager(orderId, PENDING)
    alt total < $500 (US) / ¥50 000 (JP)
        PO->>Q2: auto-approve
    else above threshold
        PO-->>AD: wait for human approval
        AD->>Q2: OPCAdminFacadeEJB → approve/deny
    end
    Q2->>OA: onMessage
    OA->>PM: updateStatus(APPROVED / DENIED)
    OA->>SUP: jms/supplier/PurchaseOrderQueue<br/>TPA SupplierOrder XML
    OA->>ML: MailOrderApprovalMDB → customer email
    SUP->>SUP: InventoryEJB: decrement stock
    SUP->>Q3: OrderFulfillmentFacadeEJB → TPA Invoice XML
    Q3->>IN: onMessage (topic subscriber)
    Q3->>ML: MailInvoiceMDB (2nd subscriber)
    IN->>IN: mark line items shipped
    IN->>PM: updateStatus(COMPLETED) when fully shipped
    IN->>ML: MailCompletedOrderMDB → customer email
```

### Messaging topology (logical name → physical JNDI)

Resolved from each module's `sun-j2ee-ri.xml`. Note that **invoices travel over a Topic, not a
Queue** — `InvoiceMDB` and `MailInvoiceMDB` are two independent subscribers, so the
publish/subscribe fan-out is load-bearing and must survive the migration.

| Producer | Logical ref | Physical destination | Consumer |
| --- | --- | --- | --- |
| petstore `AsyncSenderEJB` | `jms/AsyncSenderQueue` | `jms/opc/OrderQueue` (queue) | opc `PurchaseOrderMDB` |
| opc `PurchaseOrderTD` / `OPCAdminFacadeEJB` | `jms/OrderApprovalQueue` | `jms/opc/OrderApprovalQueue` (queue) | opc `OrderApprovalMDB` |
| opc `OrderApprovalTD` | `jms/PurchaseOrderQueue` | `jms/supplier/PurchaseOrderQueue` (queue) | supplier `SupplierOrderMDB` |
| opc `OrderApprovalTD` | `jms/OrderApprovalMailQueue` | `jms/opc/MailOrderApprovalQueue` (queue) | opc `MailOrderApprovalMDB` |
| supplier `OrderFulfillmentFacadeEJB` | `jms/InvoiceTopic` | `jms/opc/InvoiceTopic` (**topic**) | opc `InvoiceMDB` **and** `MailInvoiceMDB` |
| opc `InvoiceTD` | `jms/CompletedOrderMailQueue` | `jms/opc/MailCompletedOrderQueue` (queue) | opc `MailCompletedOrderMDB` |
| opc `Mail*MDB` | `jms/MailQueue` | `jms/opc/MailQueue` (queue) | `MailerMDB` → JavaMail |

### Business rules worth pinning down (these are the parity tests)

| Rule | Location |
| --- | --- |
| Auto-approve under $500 US / ¥50 000 JP | `apps/opc/src/.../opc/ejb/PurchaseOrderMDB.java:183` (`canIApprove`) |
| Order lifecycle `PENDING → APPROVED\|DENIED → COMPLETED` | `components/processmanager/src/.../ejb/OrderStatusNames.java` |
| Empty cart cannot be ordered | `OrderEJBAction` → `ShoppingCartEmptyOrderException` → `cart_empty_order_error.screen` |
| Duplicate account rejected | `DuplicateAccountException` → `duplicate_account.screen` |
| Order IDs = counter name `"1001"` used as a string **prefix** + incrementing DB counter (`10011`, `10012`, …) | `components/uidgen/src/.../counter/ejb/CounterEJB.java:67`, called from `OrderEJBAction` as `getUniqueId("1001")` |
| Partial shipment: order completes only when *all* line items shipped | `InvoiceMDB.doWork` |
| Mail sending is **disabled by default** | `apps/opc/src/ejb-jar.xml` — `param/SendConfirmationMail`, `param/SendApprovalMail`, `param/SendCompletedOrderMail` all `false` |
| Inter-app messages are XML validated against DTDs | `param/xml/validation/*` env-entries = `true` |

---

## 6. Domain / data model

```mermaid
erDiagram
    USER ||--|| CUSTOMER : "userId = userName"
    CUSTOMER ||--|| ACCOUNT : has
    CUSTOMER ||--|| PROFILE : has
    ACCOUNT ||--|| CONTACTINFO : has
    ACCOUNT ||--|| CREDITCARD : has
    CONTACTINFO ||--|| ADDRESS : has
    PURCHASEORDER ||--o{ LINEITEM : contains
    PURCHASEORDER ||--|| CONTACTINFO : "shipTo / billTo"
    PURCHASEORDER ||--|| CREDITCARD : "paid with"
    PURCHASEORDER ||--|| MANAGER : "workflow status"
    SUPPLIERORDER ||--o{ LINEITEM : contains
    ITEM ||--|| INVENTORY : "stock level"

    CATEGORY ||--o{ CATEGORY_DETAILS : "per locale"
    CATEGORY ||--o{ PRODUCT : contains
    PRODUCT ||--o{ PRODUCT_DETAILS : "per locale"
    PRODUCT ||--o{ ITEM : "variants"
    ITEM ||--o{ ITEM_DETAILS : "per locale"
```

The catalog side is **document-shaped**: a category has localised names/descriptions, products
hang off categories, items hang off products with five free-form attributes (`attr1`..`attr5`).
The order side is an **aggregate**: a purchase order owns its line items, addresses and card
details outright and is never shared. Both shapes map far more naturally to documents than to the
six-way join in `CatalogDAOSQL.xml` — relevant to the MongoDB stretch goal.

---

## 7. Findings that will drive migration decisions

| # | Finding | Evidence | Implication |
| --- | --- | --- | --- |
| 1 | **Passwords stored and compared in plaintext** | `components/signon/src/.../user/ejb/UserEJB.java:88` — `password.equals(getPassword())` | Must not be carried forward. Replace with a modern hash; this is a deliberate deviation from strict parity. |
| 2 | No authoritative DDL for non-catalog tables | CMP 2.0 auto-schema | Data model must be re-derived from `ejb-jar.xml` CMP fields (done in §4). |
| 3 | Sign-on is a **custom filter**, not container security | `signon-config.xml` + `SignOnFilter` | Maps cleanly onto Spring Security filter chain. |
| 4 | Value objects duplicated across 4 EJB jars | `AddressEJB` etc. declared 4× | Collapse into shared embeddables. |
| 5 | Presentation is server-rendered JSP with a bespoke template engine | `TemplateServlet` + 19 screen definitions | Choose deliberately: keep server-side rendering, or expose REST + SPA. Affects "functional equivalence" claims. |
| 6 | i18n is first-class (en_US, ja_JP, zh_CN) with locale-scoped catalog rows | `screendefinitions_*.xml`, `*_details.locale` | Easy to silently drop; must be an explicit scope decision. |
| 7 | Four apps coupled only via XML-over-JMS | `components/xmldocuments` | Enables strangler migration app-by-app rather than big bang. |
| 8 | Admin client is a **Swing desktop app** talking XML over HTTP POST | `apps/admin/src/client/PetStoreAdminClient.java` | Almost certainly out of scope; needs an explicit call. |
| 9 | Email is disabled by default | env-entries in `apps/opc/src/ejb-jar.xml` | Parity baseline does not include SMTP. |
| 10 | `webservices/` contains a JAX-RPC variant of opc↔supplier | `src/webservices` | Duplicate of the JMS path; exclude from scope. |
| 11 | **`mappings.xml` declares `SignOffEvent` → `SignOffEJBAction`, neither of which exists in the source tree** | `apps/petstore/src/docroot/WEB-INF/mappings.xml`; `grep -r SignOffEvent src` returns nothing | Dead configuration. `SignOffHTMLAction:69` actually invalidates the session, rebuilds the `PetstoreComponentManager`, and returns a **`ChangeLocaleEvent`** — so `SignOffEvent` is never raised and the mapping is unreachable. Do not port it; it is also a good argument for validating config-driven dispatch tables at startup in the new app. |

---

## 8. Open scope questions

1. **Which app(s) are in scope?** The brief says "for kitchensink only", but this repository
   contains Pet Store, not the JBoss `kitchensink` quickstart. Migrating all four EARs is a very
   different exercise from migrating the storefront.
2. **UI strategy** — keep server-rendered pages (closest to parity, easiest to demo side-by-side)
   or move to REST + SPA?
3. **Async workflow** — keep real messaging (embedded broker) or collapse to in-process events?
   Keeping it is much closer to functional equivalence.
4. **Admin Swing client** — replace with a web admin screen, or drop?
