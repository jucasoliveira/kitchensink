# Contributing — the mechanics

[AGENTS.md](AGENTS.md) says **who** writes what. This file says **how** a change travels from an
issue to `main`. It is short because the rules are few and CI enforces the ones that matter.

## 1. After cloning

```bash
git config core.hooksPath .githooks
```

The repository ships its hooks in `.githooks/` rather than `.git/hooks/` so they are versioned.
Today there is one, `pre-commit`, which strips the macOS `._*` sidecar files this volume scatters
through the tree. It never blocks a commit.

## 2. One issue, one branch, one PR

1. Pick an open issue in the `Pet Store -> Spring Boot` milestone. Never start anything in
   `Deferred - designed, not built` — those are closed on purpose ([ADR-0006](docs/adr/0006-deliverable-scope-kitchensink-slice.md)).
2. Create the branch **from the issue page** ("Development → Create a branch"). GitHub names it
   `<issue>-<slug>`, e.g. `7-16-repo-hygiene-labels-milestone-pr-template-branch-protection`,
   which links the branch to the issue with no extra ceremony.
3. Open a PR into `main`. The template asks for the legacy anchor and the definition-of-done
   checklist; fill both in.
4. Merge when `build` and `parity` are green. **No direct pushes to `main`.**

## 3. Commit messages — Conventional Commits

```
<type>(<scope>): <subject> (#<issue>)
```

- **type** — one of `feat`, `fix`, `test`, `docs`, `refactor`, `chore`, `ci`, `build`, `perf`.
- **scope** — optional, lower-case. Use the bounded context or the cross-cutting area the change
  lives in: `customer`, `catalog`, `shared`, `arch`, `infra`, `config`, `security`, `parity`,
  `docs`, `backlog`.
- **subject** — imperative, lower-case, no trailing full stop, ≤ 72 characters in total.
- **issue** — the GitHub number, in parentheses at the end. It is what makes the history
  traceable back to the backlog and the traceability matrix.

Examples, adapted from this repository's own history (the issue suffix is the part the early commits missed):

```
feat(infra): mongo/jpa profiles, externalised config, Actuator health (#6)
feat(arch): package layout per bounded context + ArchUnit rules (#3)
ci(infra): GitHub Actions build, ArchUnit, JaCoCo, parity job (#5)
docs: update README with build and run steps for persistence profiles (#6)
```

A body is welcome when the *why* is not obvious from the subject. Breaking changes are marked
with `!` after the scope (`feat(customer)!: …`) and explained in the body, although nothing in a
vertical slice this size should need one.

**Why this convention.** Every behaviour change must carry a test that names the legacy rule it
preserves, and every issue must map to a row in the traceability matrix
(`docs/03-migration-plan.md` §4). A commit that names its type, its bounded context and its issue
makes both of those checkable from `git log` alone, which is what the playback panel will do.

## 4. What CI checks on every PR

| Job | Runs | Fails when |
| --- | --- | --- |
| `build` | `mvn verify` minus the parity tag: unit + slice tests, ArchUnit, JaCoCo floor | a test breaks, a module boundary is crossed, or coverage on `domain`/`application` drops below the floor |
| `parity` | the `@Tag("parity")` characterization tests, as their own job | legacy behaviour changes — visibly different from an ordinary test break |

Both are required status checks on `main`. The rules they enforce are in
`docs/03-migration-plan.md` §5.

## 5. Definition of done

Copied from `docs/03-migration-plan.md` §4 so the PR template and this file agree:

1. Behaviour is covered by a test that names the legacy rule or `file:line` it preserves.
2. The legacy→new traceability matrix (Issue 2.3) has a row for it.
3. No new ArchUnit violation; module boundaries hold.
4. Works under both persistence profiles (`mongo`, `jpa`), or the gap is written down.
5. Merged via PR to `main`; CI green. No direct pushes to `main`.

## 6. Backlog and labels

`scripts/backlog.json` is the source of truth for issues, labels and the milestone.
`scripts/create-github-issues.sh` projects it onto GitHub and is idempotent:

```bash
scripts/create-github-issues.sh --dry-run   # preview
scripts/create-github-issues.sh             # create / resume
scripts/create-github-issues.sh --sync      # also push body and label changes to existing issues
```

Edit the JSON, then sync. Hand-edits on GitHub are overwritten by the next sync.

Labels: `T1` (must ship) · `T2` (should ship) · `epic` · `infra` · `slice` · `parity` ·
`persistence` · `docs` · `stretch` · `risk` · `cut-candidate`.

## 7. Decisions

A design decision that changes gets a **new** ADR in `docs/adr/`. The old one is marked
`SUPERSEDED by ADR-XXXX` and left otherwise untouched — the decision trail is part of the deliverable.
