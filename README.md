# kitchensink — Java Pet Store 1.3.1_02 on Spring Boot 4.1.1

A migration of Sun's 2003 Java Pet Store (4 EARs, 33 EJBs) to Spring Boot 4.1.1 on Java 21,
delivered as a vertical slice with MongoDB as the primary store and a JPA/H2 store behind the
same ports. What is in scope, and why, is [ADR-0006](docs/adr/0006-deliverable-scope-kitchensink-slice.md);
the architecture of what replaced it is [docs/07-target-architecture.md](docs/07-target-architecture.md),
and the rest of the paper trail is indexed in [docs/README.md](docs/README.md). The working agreement
for anyone (or anything) contributing is [AGENTS.md](AGENTS.md); the branch, commit and PR
mechanics are in [CONTRIBUTING.md](CONTRIBUTING.md).

## Running it

You need JDK 21 and Docker. Everything else is downloaded by the Maven wrapper.

```bash
scripts/dev-up.sh      # docker compose up -d --wait: a single-node MongoDB 7.0 replica set
scripts/run.sh         # ./mvnw spring-boot:run
```

Then open <http://localhost:8080>, which redirects to the catalog.

**For anything worth looking at, start it seeded instead** — `scripts/run.sh` gives you an empty
catalog:

```bash
scripts/seed.sh        # same as run.sh, plus --kitchensink.seed.catalog=true
```

That loads the legacy catalog verbatim from `Populate-UTF8.xml` (5 categories, 16 products, 28
items, 4 locales) and leaves the app running. It is the twin of the legacy's
`GET /Populate?forcefully=true`.

| URL | |
| --- | --- |
| `/catalog` | browse; `?locale=ja_JP` switches language |
| `/customers` | register (public), and the member table (signed on) |
| `/customers/me` | the account screen — sign in first |
| `/api/catalog/**` · `/api/customers/**` | the REST facade; the legacy had none |
| `/actuator/health` | which store is wired |

`/`, `/catalog/**`, `/customers` (the form), `POST /api/customers` and `/actuator/health` are open;
everything else redirects to `/login`. Which URLs are protected, and why the default is inverted
from the legacy's, is [docs/07-target-architecture.md](docs/07-target-architecture.md) §5.

> **If startup fails with `Failed to start bean 'webServerStartStop'`,** port 8080 is already
> taken — usually a previous run. `lsof -ti:8080 | xargs kill -9`. Worth knowing because the
> failure message does not say "port in use" anywhere near the top.

### Sign-on and passwords — the one deliberate deviation from parity

The legacy sign-on was a hand-written servlet filter (`components/signon/.../web/SignOnFilter.java`,
finding #3 in [docs/01-legacy-architecture.md](docs/01-legacy-architecture.md)) in front of a
`UserEJB` that stored the password as typed and checked it with `password.equals(getPassword())`
(`UserEJB.java:88`, finding #1). The filter maps onto a Spring Security filter chain
(`shared/security/SecurityConfig`) with a `UserDetailsService` over the customer aggregate. The
plaintext comparison does **not** map onto anything: passwords are BCrypt-hashed at registration
and only the hash is stored, and the domain type that holds it (`customer/domain/PasswordHash`)
refuses any value that is not a BCrypt hash, so no plaintext credential path exists anywhere in
this repository's history. Everything else in the slice aims at strict parity; this is the one
place it is deliberately broken, and [ADR-0006](docs/adr/0006-deliverable-scope-kitchensink-slice.md)
lists it as such. The tests that pin it are `PasswordHashTest`, `CustomerRegistrationTest` and
`SignOnTest`.

```bash
scripts/dev-down.sh            # stop the container, keep the data
scripts/dev-down.sh --clean    # stop and drop the volume
```

### Persistence profiles

The store is chosen by a Spring profile. Nothing in the code changes between the two
([ADR-0005](docs/adr/0005-persistence-and-mongodb.md) §4).

| Profile | Store | Needs Docker | Command |
| --- | --- | --- | --- |
| `mongo` (default) | MongoDB 7.0 from `compose.yaml` | yes | `scripts/run.sh` |
| `jpa` | H2 in memory, Hibernate on top | no | `scripts/run.sh -Dspring-boot.run.profiles=jpa` |

**The proof that this is a real switch and not a claim** is one command:

```bash
./scripts/profile-switch.sh          # --mongo or --jpa for one side only
```

It runs three shared test contracts once per profile, reads Surefire's XML for the test methods
that actually executed, and diffs them: **39 assertions, one source, green against MongoDB and
against H2**. The two Mongo-only assertions — about document shape, with no relational counterpart
— are printed by name so the asymmetry stays visible. How it works and the two bugs it found are
[docs/05-test-harness.md](docs/05-test-harness.md) §7.

A cruder proof is `/actuator/health`: under `mongo` it lists a `mongo` component, under `jpa` a
`db` component, and never both.

```bash
curl -s localhost:8080/actuator/health
# mongo  -> {"components":{"diskSpace":…,"mongo":{"status":"UP"},…},"status":"UP"}
# jpa    -> {"components":{"db":{"status":"UP"},"diskSpace":…,…},"status":"UP"}
```

Connection details are environment variables with a localhost default, so a different database
is a variable, not a rebuild:

| Variable | Default | Used by |
| --- | --- | --- |
| `MONGODB_URI` | `mongodb://localhost:27017/kitchensink` | `mongo` |
| `JDBC_URL` | `jdbc:h2:mem:kitchensink;DB_CLOSE_DELAY=-1` | `jpa` |

How the switch works, in [`application.yaml`](src/main/resources/application.yaml): one file,
three documents. The first applies always and sets `spring.profiles.default: mongo`. The other two
are activated by profile, name their own store, and list the *other* store's auto-configuration
under `spring.autoconfigure.exclude`, so the store that was not chosen is absent from the context
rather than idle in it. The legacy app made the same choice at deploy time, through a JNDI
`env-entry` (`param/CatalogDAOClass`, `ejb-jar.xml:58`) that `CatalogDAOFactory` resolved with
`Class.forName`, with the datasource and its credentials baked into `sun-j2ee-ri.xml:361-365`.

### Running the tests

```bash
./mvnw verify -DexcludedGroups=parity     # unit + slice tests, ArchUnit rules, JaCoCo floor
./mvnw test -Dgroups=parity                # the 177 @Tag("parity") characterization tests
```

What each gate checks, where it runs in CI, and how to make each one fail on purpose is
[docs/05-test-harness.md](docs/05-test-harness.md). Two tests exist only to prove the harness
itself: `RulesCanFailTest` evaluates the ArchUnit rules against classes written to break them, and
`TestcontainersHarnessTest` checks the context is wired to the container it started.

The profile switch has its own tests: `PersistenceProfileMongoTest` boots the default profile
against Testcontainers; `PersistenceProfileJpaTest` boots `jpa` with no container at all; and
`ProfileConfigurationTest` pins the shape of `application.yaml` so the two stay honest.

The build directory is `${java.io.tmpdir}/kitchensink/target` by default, because this repository
lives on a non-APFS volume where macOS scatters `._*` sidecar files through `target/`. Pass
`-Dkitchensink.build.directory=target` to put it back in the repository, as CI does.

### Running the fat jar

```bash
./mvnw package -DskipTests -Dkitchensink.build.directory=target
java -jar target/kitchensink-0.0.1-SNAPSHOT.jar --spring.profiles.active=jpa
```

## Where to look next

| | |
| --- | --- |
| What was built and why that scope | [ADR-0006](docs/adr/0006-deliverable-scope-kitchensink-slice.md) |
| The legacy app, reverse-engineered | [docs/01-legacy-architecture.md](docs/01-legacy-architecture.md) |
| What replaced it, same diagram style | [docs/07-target-architecture.md](docs/07-target-architecture.md) |
| Every legacy component → its fate | [docs/06-traceability-matrix.md](docs/06-traceability-matrix.md) |
| The document model, with the joins and duplication counted | [docs/decisions/document-design.md](docs/decisions/document-design.md) |
| How data would actually move | [docs/decisions/relational-to-document-migration.md](docs/decisions/relational-to-document-migration.md) |
| What I learned / how I'd run the next one | [docs/09-what-i-learned.md](docs/09-what-i-learned.md) |

**What is not here**, stated plainly: cart, checkout, the order workflow, approval and supplier
fulfilment are designed ([ADR-0004](docs/adr/0004-async-workflow.md)) and deliberately not built;
there is no SMTP, no Swing admin client and no JAX-RPC endpoint; `zh_CN` is data-only. The full
list is [ADR-0006](docs/adr/0006-deliverable-scope-kitchensink-slice.md) and
[docs/07-target-architecture.md](docs/07-target-architecture.md) §8.
