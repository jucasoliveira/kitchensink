# 05 — The test harness, proven end to end

> Issue 1.10. **Legacy anchor: none.** `grep -rl junit petstore1.3.1_02` returns nothing, and
> `build.xml` compiles and packages four EARs without running a single test. Everything on this
> page is new ground; what it protects is the behaviour the parity tests will pin, not any build
> that existed before.

Issues 1.1–1.9 built the harness piece by piece and watched it go green. This issue is about the
other colour. The acceptance criterion is that *"a slice-shaped test can fail for the right
reason"* — because a gate that has only ever been seen green is indistinguishable from a gate that
checks nothing, and the difference only shows up on the day it matters.

So this page does three things: names each gate and where it lives (§1), records the green runs
(§2), and for every gate shows what red looks like *and how to make it red without editing
`src/main`* (§3). §4 lists what 1.10 changed to get there, §5 what it deliberately left, and §6 the
one place two gates turned out to disagree — found the honest way, by a red build.

## 1. The gates

Four gates run on every pull request, in two GitHub Actions jobs
([`.github/workflows/ci.yml`](../.github/workflows/ci.yml)). The split is the one
[03-migration-plan.md](03-migration-plan.md) §5 asks for: a parity break must look different from
a unit-test break.

| Gate | What it checks | Where it lives | Job | Red means |
| --- | --- | --- | --- | --- |
| **Unit + slice tests** | Domain and application rules in isolation; the registration slice end to end — form or JSON in, `CustomerRegistration`, Mongo, page or JSON out | `src/test/**`, Surefire | `build` | The new code is broken |
| **Testcontainers** | Every `@SpringBootTest` boots the *production* wiring against a real `mongo:7.0` it starts itself | `TestcontainersConfiguration` (`@ServiceConnection`, replica set) | `build` | The slice cannot reach its store |
| **ArchUnit** | 16 rules: context boundaries (`BoundedContextRulesTest`), layering and port/adapter inversion (`LayeringRulesTest`) | `src/test/.../architecture/` | `build` | A boundary ADR-0005/0006 relies on has been crossed |
| **JaCoCo floor** | 80 % line / 70 % branch — on `*/domain/**` and `*/application/**` **only**, measured over the *whole* suite (see §6) | `pom.xml`, `verify` phase | `build` | A rule was added without a test naming it |
| **Parity** | `@Tag("parity")` characterization tests — 85 of them as of 3.4 | `src/test/**`, run alone by `-Dgroups=parity` | `parity` (and, since 3.4, inside `build` too) | The migration changed what Pet Store did |

Two design points worth defending in the playback:

- **ArchUnit is a gate, not a job.** The rules are plain JUnit tests and run inside `build`. A
  third job would buy ~40 s of earlier feedback for a second cold dependency resolve and a second
  compile on every PR.
- **Coverage is measured where it means something.** Controllers and adapters are excluded from the
  floor on purpose — "coverage on controllers and adapters is theatre"
  ([03-migration-plan.md](03-migration-plan.md) §5). The floor is on the code that carries the
  business rules, and it is the JaCoCo `check` goal that fails the build, not a report someone
  has to read.

### How the container is wired

`TestcontainersConfiguration` is a `@TestConfiguration` with one bean: a `MongoDBContainer` on
`mongo:7.0` with `withReplicaSet()`, annotated `@ServiceConnection`. Boot turns that bean into a
`MongoConnectionDetails`, which overrides `spring.mongodb.uri`, so the test context dials the
container's mapped port and never the `localhost:27017` default. A test opts in with
`@Import(TestcontainersConfiguration.class)`; a test that must prove it needs *no* container
(`PersistenceProfileJpaTest`) leaves the import out, and that absence is the assertion.

Spring's context cache keys on the configuration, so the suite runs three distinct contexts —
plain, `+MockMvc`, and the spike's extra beans — and therefore starts three containers. That is
~1.5 s each after the image is cached. Collapsing them is possible and not worth it.

The image name is written down exactly once, in `TestcontainersConfiguration.MONGO_IMAGE`.
`ComposeConsistencyTest` fails the build if `compose.yaml` names a different one, so the database
a developer runs locally is the database the tests ran against.

## 2. Green, in GitHub Actions, on a clean runner

| Run | Branch | Tests | Containers started | JaCoCo | Result |
| --- | --- | --- | --- | --- | --- |
| [33752009695](https://github.com/jucasoliveira/kitchensink/actions/runs/33752009695) | `main` after PR #71 (1.9) | 100 | 3 × `mongo:7.0`, image pulled cold in 8.5 s | "All coverage checks have been met" | `build` 1m14s ✓ · `parity` 40s ✓ |
| *this branch's PR run* | `57-110-…` | 110 | 3 | met | *filled in when the PR is opened* |

The `parity` job in the first row is green **with zero tests**. Its upload step even warns *"No
files were found with the provided path: target/surefire-reports/"*. That was not a bug in 1.10, it
was scaffolding for 2.2 — and it is now spent: `-Dgroups=parity` matches 85 tests as of issue 3.4,
`-DfailIfNoTests=false` is out of the workflow, and "the parity job ran no tests" is an error
again. §6 records the one other thing 3.4 had to change to keep the numbers honest.

## 3. Red, for the right reason

Each drill below turns exactly one gate red without touching `src/main`, and shows the text to
expect. The point of recording the text is that "red for the wrong reason" — Docker not running,
a port clash, a stale image — looks different, and a reviewer should be able to tell the two
apart from the log alone.

### 3.1 ArchUnit — a rule that bites, and a rule that would otherwise go quiet

**Permanent proof: [`RulesCanFailTest`](../src/test/java/com/jucasoliveira/kitchensink/architecture/RulesCanFailTest.java).**
It takes the same `ArchRule` objects `LayeringRulesTest` runs and evaluates them against
`architecture.fixtures.leaky`, a package of five test-source classes, four of which break one rule each
— a `@Document` on an aggregate, a service importing an adapter type, a `Repository` that is a
class, a controller reaching past the service into the store. Each test asserts the rule reports
a violation *and names the offender*. A seventh test evaluates a rule the fixtures respect and
asserts silence, so the proof is about the rules, not about the importer. The fixtures never reach
the real gate: it imports with `ImportOption.DoNotIncludeTests`.

**The second half is the empty-rule problem.** Issue 1.2 landed the rules before the packages they
guard existed, and to make that possible set `archRule.failOnEmptyShould=false` in
`archunit.properties` — with a note to flip it back "once the customer slice has landed". The slice
has landed, and until this issue the setting was still off: any rule whose `that()` clause matched
nothing passed silently, which is exactly the failure mode that survives a package rename. Flipping
it back on produced one failure, which is the evidence that it was doing something:

```text
[ERROR] LayeringRulesTest.the_jpa_adapter_does_not_know_the_mongo_one
java.lang.AssertionError: Rule 'no classes that reside in a package '..adapter.persistence.jpa..'
should depend on classes that reside in a package '..adapter.persistence.mongo..', because
ADR-0005 §3-4: two adapters behind one port, selected by profile' failed to check any classes.
This means either that no classes have been passed to the rule at all, or that no classes passed
to the rule matched the `that()` clause. To allow rules being evaluated without checking any
classes you can either use `ArchRule.allowEmptyShould(true)` on a single rule or set the
configuration property `archRule.failOnEmptyShould = false` to change the behavior globally.
```

That rule is legitimately empty — there is no JPA adapter package until 3.3 / 4.6 — so it now
carries `.allowEmptyShould(true)` with a comment dated to those issues. The other fifteen rules are
no longer exempt. `RulesCanFailTest.empty_rules_fail_by_default` pins the property so the blanket
exemption cannot quietly come back.

**Live drill, if the panel wants one:** add `import org.springframework.stereotype.Component;` and
`@Component` to `customer/domain/Customer.java`, run
`./mvnw test -Dtest=LayeringRulesTest`, and `the_domain_is_plain_java` alone fails:

```text
Architecture Violation [Priority: MEDIUM] - Rule 'no classes that reside in a package '..domain..'
should depend on classes that reside in any package ['org.springframework..', 'jakarta.persistence..',
'jakarta.servlet..', 'org.bson..', 'com.mongodb..', 'org.thymeleaf..'], because ADR-0005 §1: domain
aggregates are plain Java with no persistence annotations, which is what lets one aggregate carry
both a Mongo and a JPA mapping' was violated (1 times):
Class <com.jucasoliveira.kitchensink.customer.domain.Customer> is annotated with <org.springframework.stereotype.Component> in (Customer.java:0)
```

The other ten layering rules stay green. Revert with `git checkout -- src/main`.

### 3.2 Testcontainers — the context is talking to the container it started

**Permanent proof: [`TestcontainersHarnessTest`](../src/test/java/com/jucasoliveira/kitchensink/TestcontainersHarnessTest.java).**
Three assertions against the *running* context: the `MongoConnectionDetails` Boot wired points at
the container's host and mapped port (not `localhost:27017`); `buildInfo` on the other end reports
a `7.0.x` server, i.e. the tag in `MONGO_IMAGE`; and `hello` reports a writable replica-set
primary, which is what `@Transactional` needs. It is the runtime twin of `ComposeConsistencyTest`,
which pins the same facts as text. Same `@Import` as the round-trip test, so it shares that cached
context and costs no extra container.

Why this matters: on a laptop with `scripts/dev-up.sh` running, a broken `@ServiceConnection`
would fall through to the compose database and every slice test would still pass — against the
developer's data. In CI the same fault is a connection refused. Both are green-or-red for the
wrong reason, and this test is what tells them apart from the real thing.

**What the wrong reason looks like.** With Docker Desktop shut down, every `@SpringBootTest` that
imports the configuration fails before any assertion runs:

```text
java.lang.IllegalStateException: Could not find a valid Docker environment. Please see logs and check configuration
```

Nothing in `src/main` is implicated by that message; `ComposeConsistencyTest` and the unit tests
still pass. That contrast is the reason the harness tests carry no Docker dependency of their own.

### 3.3 The slice — a behaviour change is a failing test that names the rule

The slice tests (`CustomerScreenTest`, `CustomerResourceTest`, `SignOnTest`,
`CustomerMongoRoundTripTest`) each carry the legacy `file:line` they preserve in the
`@DisplayName`, so the red run says *which legacy rule* just changed, not just which assertion.

**Live drill:** in `customer/adapter/web/CustomerController.java`, change the post-registration
redirect from `/customers` to `/`, run `./mvnw test -Dtest=CustomerScreenTest`, and exactly one
test fails:

```text
[ERROR] Tests run: 5, Failures: 1, Errors: 0, Skipped: 0 <<< FAILURE! -- in ...CustomerScreenTest
[ERROR] CustomerScreenTest.a_valid_registration_posts_and_is_listed:93 Redirected URL expected:</customers> but was:</>
```

The four other tests in the class stay green, the store still holds one document, and the message
names the behaviour rather than a stack frame. Revert with `git checkout -- src/main`.

### 3.4 JaCoCo — the floor is a floor

No source edit needed: run a subset of the suite so the domain and application packages are
not covered at all, and let `verify` reach the `check` goal.

```bash
./mvnw clean verify -Dtest=ComposeConsistencyTest -Dsurefire.failIfNoSpecifiedTests=false -DexcludedGroups=parity
```

```text
[INFO] --- jacoco:0.8.13:check (check-domain-and-application) @ kitchensink ---
[WARNING] Rule violated for bundle kitchensink: lines covered ratio is 0.00, but expected minimum is 0.80
[WARNING] Rule violated for bundle kitchensink: branches covered ratio is 0.00, but expected minimum is 0.70
[INFO] BUILD FAILURE
[ERROR] Failed to execute goal org.jacoco:jacoco-maven-plugin:0.8.13:check (check-domain-and-application) on project kitchensink: Coverage checks have not been met. See log for details. -> [Help 1]
```

**`clean` is not optional.** The JaCoCo agent *appends* to `jacoco.exec`, so without it the
coverage from the last full run is still in the file and the check passes. That was the first
result of this drill, and it is worth knowing before a green coverage number is trusted on a
laptop: CI starts from an empty workspace, a developer's build directory does not.

The floor is on `domain` and `application` only, so a controller with no test does *not* trip it
— by design, and `ComposeConsistencyTest` being the surviving test makes the point: it exercises
nothing in either package.

### 3.5 Parity — now a real gate

`./mvnw test -Dgroups=parity -Djacoco.skip=true` runs 85 tests: the seed characterization of 2.1,
the domain fidelity of 3.1, the repository and search tests of 3.2, and the paging and service
tests of 3.4. `-DfailIfNoTests=false` has been removed from the workflow as its own comment
instructed, so an empty tag is a failure again rather than a silent pass.

**The drill.** Break a legacy rule rather than a unit rule, and watch which job reddens. Change one
keyword in `CatalogService.keywords()` to skip the `toLowerCase`, and `CatalogServiceTest`'s
*"DIVERGENCE — keywords are lowercased…"* fails in the `parity` job with the `file:line` of the
2003 DAO in its javadoc. That is the distinction [03-migration-plan.md](03-migration-plan.md) §5
asks for: the message names the legacy behaviour that moved, not an assertion that happened to
change.

## 4. What 1.10 changed

All of it is test-side; `src/main` is untouched.

| Change | Why |
| --- | --- |
| `archunit.properties`: `failOnEmptyShould=true` | The 1.2 exemption had outlived its reason; a rule matching nothing is now an error again |
| `LayeringRulesTest.the_jpa_adapter_does_not_know_the_mongo_one`: `.allowEmptyShould(true)` | The one rule that is legitimately empty until 3.3 / 4.6, exempted on the rule with the reason beside it |
| `architecture/RulesCanFailTest` + `architecture/fixtures/leaky/**` | The rules proven to bite, permanently, against test-only fixtures |
| `TestcontainersHarnessTest` | The context proven to be connected to the container it started, on the image compose names, as a replica set |
| This page; `README.md` §"Running the tests" | The drills, so the proof is repeatable in the playback |

## 5. Deliberately not done here

- ~~**The parity job stays vacuous.** Seeding it with one `@Tag("parity")` test would be starting
  2.2 (#10) from inside 1.10. The flag comes out with 2.2.~~ **Done:** 2.2 landed the first parity
  tests and the flag came out; 3.4 took the count to 85. Left struck through rather than deleted,
  because "what 1.10 deliberately left" is a record of a decision at a date, not a to-do list.
- **Issues 2.4 (#12, ArchUnit rule set) and 2.5 (#13, Testcontainers base test + CI wiring)** are
  already satisfied by 1.2, 1.4 and this issue. They should be closed by reference to those rather
  than re-done — Lucas's call, noted here so it is not lost.
- **Three containers per run, not one.** See §1; a shared context would save ~3 s and cost the
  clarity of "this test imports the container, that one proves it does not".
- **The bounded-context rules have no fixture proof.** They match one package below the root, and
  a fixture there would be a class in a real or a deferred context. `failOnEmptyShould=true` is
  what guards them.

## 6. When two gates disagreed: coverage vs. the parity split

Everything above was written when the parity job was empty, and it hid an interaction that only
surfaced on issue 3.4. Worth keeping, because it is the one place on this page where a gate went
red and the code was fine.

**What happened.** [Run 33788866853](https://github.com/jucasoliveira/kitchensink/actions/runs/33788866853)
failed the JaCoCo floor at 0.36 line against 0.80, and 0.22 branch against 0.70. No test failed;
106 of them passed. The two classes 3.4 added, `CatalogPage` and `CatalogService`, are covered
100 % line and 100 % branch — by `CatalogPageTest` and `CatalogServiceTest`, both `@Tag("parity")`.
The `build` job ran `-DexcludedGroups=parity`, so it never executed them, and the floor it enforces
is scoped to `*/domain/**` and `*/application/**`, which is exactly where those two classes live.
The gate was measuring a third of the suite and reporting on all of it.

**Why it had never bitten.** Parity-only coverage was not new — `PasswordHashTest`,
`CustomerRegistrationTest` and `CatalogDomainLegacyFidelityTest` were all tagged. But their
subjects were either records thin enough not to move the ratio, or classes the non-parity slice
tests (`CustomerResourceTest`, `CustomerScreenTest`, `RegisterCustomerCommandTest`) covered
incidentally on their way through. `CatalogService` was the first class of real size whose *only*
tests are characterization tests. On the current plan it would not have been the last: the rest of
the catalog and all of the order workflow are specified the same way.

**The fix, and what it cost.** The `build` job stopped excluding the tag; it now runs the whole
suite. The `parity` job is untouched and is still the only place `-Dgroups=parity` runs on its own
and uploads its own report, so a parity break still has its own named gate. What it costs is that
those 85 tests run twice per pull request (~15 s) and a parity break now reddens `build` as well.
[03-migration-plan.md](03-migration-plan.md) §5 asks that *"a parity break must look different from
a unit-test break"*; it still does — a parity break reddens both jobs, a unit break reddens only
`build` — but the distinction is carried by which job fails rather than by which job ran the test.
That is a real, if small, weakening of the signal, and it was accepted in exchange for a coverage
number that is not an artefact of the split.

**The option not taken.** JaCoCo can merge execution data across jobs: the `parity` job drops
`-Djacoco.skip=true`, uploads its `jacoco.exec`, and the floor is checked after `jacoco:merge`.
That keeps both properties intact and costs artifact passing plus job ordering — the check can no
longer live inside `build`'s own `verify`. It is the right answer if the double run ever starts to
hurt; it was not worth the CI surface today.

**The lesson, stated so it is not re-learned.** A coverage floor and a test-selection filter are
the same decision looked at twice. Any tag that removes tests from the job that measures coverage
will eventually make a well-tested class look untested, and the failure arrives as a number rather
than as a name — which is the hardest kind of red to read. The gate table in §1 now says which
tests each floor is measured over, for that reason.
