#!/usr/bin/env python3
"""Issue 7.4 — read Surefire's XML and write "Class#method" for every test it ran.

Surefire is the only honest source for this: parsing the Java would list methods that exist,
which is a different claim from methods that executed. A contract subclass that stopped
inheriting, or a @Disabled that crept in, shows up here and nowhere else.
"""
import pathlib
import sys
import xml.etree.ElementTree as ET

out, root = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])

names, failures = [], []
for report in sorted(root.glob("TEST-*.xml")):
    for case in ET.parse(report).getroot().iter("testcase"):
        simple = case.get("classname", "").rsplit(".", 1)[-1]
        names.append(f"{simple}#{case.get('name')}")
        if case.find("failure") is not None or case.find("error") is not None:
            failures.append(f"{simple}#{case.get('name')}")

if not names:
    sys.exit(f"no Surefire reports under {root}")
if failures:
    sys.exit("tests failed: " + ", ".join(failures))

out.write_text("\n".join(sorted(set(names))) + "\n")
print(f"    {len(set(names))} tests, all green -> {out}")
