#!/usr/bin/env python3
"""Issue 7.4 — is the JPA run answering the same questions as the Mongo run?

The two sides are subclasses of one abstract contract, so a shared assertion has the SAME method
name on both. Comparing method names is therefore comparing the questions asked, and the diff is
the demo: every question the relational store was asked, the document store was asked too.

Not an equality check, because it should not be. The Mongo subclasses add assertions that have no
relational counterpart at all — the aggregate stored as one document in one collection, the
subdocument nesting — and a script that demanded symmetry would push someone to delete them. So
the rule is: every JPA-side method must exist on the Mongo side, and anything extra is reported
by name so it stays a deliberate, visible asymmetry rather than drift.
"""
import collections
import pathlib
import sys


def methods(path):
    by_method = collections.defaultdict(set)
    for line in pathlib.Path(path).read_text().split():
        cls, method = line.split("#", 1)
        by_method[method].add(cls)
    return by_method


mongo, jpa = methods(sys.argv[1]), methods(sys.argv[2])

shared = sorted(set(mongo) & set(jpa))
only_mongo = sorted(set(mongo) - set(jpa))
only_jpa = sorted(set(jpa) - set(mongo))

print(f"    shared contract assertions run under BOTH profiles: {len(shared)}")
for name in shared:
    print(f"      = {name}")

if only_mongo:
    print(f"\n    mongo-only ({len(only_mongo)}) — store-specific, no relational counterpart:")
    for name in only_mongo:
        print(f"      + {name}  [{', '.join(sorted(mongo[name]))}]")

if only_jpa:
    print(f"\n    jpa-only ({len(only_jpa)}) — a contract assertion the document side is NOT making:")
    for name in only_jpa:
        print(f"      ! {name}  [{', '.join(sorted(jpa[name]))}]")
    sys.exit("\nFAILED: the two profiles are not answering the same questions.")

if not shared:
    sys.exit("\nFAILED: no shared assertions at all — the contract is not being inherited.")

print(f"\nOK: {len(shared)} assertions, one source, green against MongoDB and against H2.")
