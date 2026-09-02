Closes #

## What

<!-- One or two sentences. What does main do after this merges that it did not before? -->

## Legacy anchor

<!-- The file:line in petstore1.3.1_02/ this change preserves, replaces or deliberately drops.
     Write "none" for infra/docs. -->

## Definition of done (`docs/03-migration-plan.md` §4)

- [ ] A test names the legacy rule or `file:line` it preserves
- [ ] Traceability matrix has a row for it (or: not a behaviour change)
- [ ] No new ArchUnit violation
- [ ] Green under both `mongo` and `jpa`, or the gap is written down below
- [ ] Commits follow `type(scope): subject (#issue)` — see `CONTRIBUTING.md` §3
- [ ] Docs / ADR updated if a decision changed (new ADR, never an edited one)

## Gaps and deviations

<!-- Anything the panel should hear from you before they find it. -->
