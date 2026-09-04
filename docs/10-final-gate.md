# 10 — Final gate: clean-clone rehearsal, CI, and the gaps

**Issue:** 8.5 (#53) · **Acceptance:** *"Fresh `git clone` → README steps → running app, with no
undocumented step."*

Run on **2026-09-04**, against a fresh clone in a temp directory carrying the prospective PR
content, on macOS 15 with JDK 21 and Docker. Not a summary of intentions — a log of what was
executed and what broke.

---

## 1. The rehearsal

```bash
git clone <repo> /tmp/clone && cd /tmp/clone
scripts/dev-up.sh          # mongo:7.0 replica set, healthy in ~11s
scripts/seed.sh            # app + catalog seed
```

Every URL the README claims, checked from the clone:

| URL | Expected | Got |
| --- | --- | --- |
| `/` | 302 → `/catalog` | **302** |
| `/catalog` | 200 | **200** |
| `/catalog/categories/FISH` | 200 | **200** |
| `/customers` | 200 (public form) | **200** |
| `/customers/me` | 302 → `/login` | **302** |
| `/api/customers` | 302 → `/login` | **302** |
| `/actuator/health` | 200, `mongo` component | **200**, `[diskSpace, livenessState, mongo, ping, readinessState, ssl]` |

Store contents after seeding: `categories 5 · products 16 · items 28` — the legacy
`Populate-UTF8.xml` counts.

**The `jpa` profile, no Docker involved:**

```bash
scripts/run.sh -Dspring-boot.run.profiles=jpa
```

| | |
| --- | --- |
| `/actuator/health` components | `[db, diskSpace, livenessState, ping, readinessState, ssl]` — **`db`, and no `mongo`** |
| `/catalog` | **200** — H2, seeded |
| `POST /api/customers` | **201** — a registration written to a relational row |

That last line is the one worth noting: the customer slice answering end-to-end against H2 in a
*running application*, not only in tests. Before issue 4.6 the whole slice was `@Profile("mongo")`
and this request would have been a 404.

**The demo, from the clone:**

```
OK: 39 assertions, one source, green against MongoDB and against H2.
```

---

## 2. What the rehearsal broke — two undocumented steps, both now closed

The point of this exercise is the things it finds, so both are recorded rather than quietly fixed.

### 2.1 `scripts/seed.sh` was committed non-executable

```
nohup: ./scripts/seed.sh: Permission denied
```

`git ls-files -s` showed mode **`100644`** for `scripts/seed.sh` — and for
`scripts/profile-switch.sh`, added in issue 7.4. `dev-up.sh`, `dev-down.sh` and `run.sh` were all
`100755`, which is why this had never been noticed: the one script a new developer needs *second*
was the broken one, and it only fails in a fresh clone. In the working tree it had been `chmod +x`'d
locally at some point and the mode never reached the index.

Fixed with `git update-index --chmod=+x` on both. **This is the exact class of failure 8.5 exists
to catch** — invisible in every environment except the one the reviewer will use.

### 2.2 Port 8080 conflicts fail with an unhelpful message

A stale instance from an earlier run made the first attempt fail with:

```
Failed to start bean 'webServerStartStop'
```

Nothing near the top of that stack says "port in use", and the app *appears* to work because
`curl localhost:8080/actuator/health` cheerfully answers — **from the old process**. I lost time to
this and then nearly wrote a demo script around a stale database. The README now carries the
symptom and the fix (`lsof -ti:8080 | xargs kill -9`), and `docs/08-demo-script.md` §0 makes killing
port 8080 and dropping the volume the first two commands of the demo.

### 2.3 Repository hygiene (not blocking, worth a broom)

The working tree has accumulated junk that a clone does not carry but that clutters the source:

- `._README.md` and similar AppleDouble sidecar files — macOS on a non-APFS volume
- two directories literally named `.._.._.._.._var_folders_…_kitchensink-target_test-classes`,
  from a relative `-Dkitchensink.build.directory` at some point

Neither is tracked. `find . -name '._*' -delete` and removing the two stray directories is enough;
worth doing before the final push so a reviewer browsing the repo does not meet them.

---

## 3. Gates, at the gate

| Gate | Command | Result |
| --- | --- | --- |
| Full build | `./mvnw verify` | **351 tests, 0 failures** |
| Parity | `./mvnw test -Dgroups=parity` | **177 tests, 0 failures** |
| ArchUnit | in `verify` | 13 layering + 5 bounded-context rules; 9 proven to bite by `RulesCanFailTest` |
| JaCoCo | in `verify` | floor met on `domain` + `application` |
| Profile switch | `./scripts/profile-switch.sh` | **39 shared assertions, both stores** |
| Clean clone | §1 above | **passes** |

One environment note that is not a defect: on this machine Testcontainers' Ryuk reaper cannot be
reached, so local runs need `TESTCONTAINERS_RYUK_DISABLED=true`. CI is unaffected — this is a local
Docker Desktop configuration, not a repository setting, and nothing in the build refers to it.

---

## 4. The gaps, listed

Identical wherever it appears, on purpose — `03-migration-plan.md` §7,
`07-target-architecture.md` §8, `08-demo-script.md` §5, and here.

**Deliberately not built:**

- Cart, checkout, the order workflow, approval, supplier fulfilment. Designed in
  [ADR-0004](adr/0004-async-workflow.md) — in-process transactional events replacing four JMS queues
  and a topic — and unimplemented. Their issues are **closed**, in a separate milestone.
- The four `Mail*MDB` beans. No SMTP in scope.
- The Swing admin client and the JAX-RPC OPC endpoint: duplicate delivery mechanisms for the same
  deferred workflow.
- `zh_CN` UI messages. The catalog carries `zh_CN` data; the UI has `en_US` and `ja_JP`.

**Deliberate parity break, one:** BCrypt. `UserEJB.java:88` compared plaintext passwords with
`equals`. `PasswordHash` refuses any value that is not a BCrypt hash.

**Known weaknesses in what *was* built** — these are the honest answers to "what would you fix
next", and they are argued in [`09-what-i-learned.md`](09-what-i-learned.md) §8:

| | |
| --- | --- |
| Spring Data writes `_class: "…CustomerDocument"` into every document | pins an adapter class name into stored data; rename the mapping type and existing documents stop reading |
| `CatalogIndexes` builds indexes in the foreground at startup | correct at 28 items, wrong against a live collection |
| `GET /api/customers` returns 302 → `/login`, not 401 | `formLogin` owns the entry point for the whole chain; needs an API-scoped `SecurityFilterChain` |
| No schema test for the JPA customer adapter | deliberate — JPA is a verification device here, not a deliverable — but it is a gap |
| Search is a `COLLSCAN` under both stores | no index can serve a post-`$lookup` regex, and `like '%kw%'` was never indexable either; measured, not assumed |

**What this is not:** Pet Store was not migrated. What shipped is the kitchensink equivalent — one
vertical slice, end to end, with the seams found and written down.
[ADR-0006](adr/0006-deliverable-scope-kitchensink-slice.md) is the argument for that being the right
unit of work.
