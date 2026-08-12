#!/usr/bin/env python3
"""Fail when pom.xml and build.gradle.kts disagree about a shared version.

CI runs the Maven suite and the Gradle suite as separate jobs. Nothing makes them agree about
dependency versions, and dependabot only edits pom.xml, so the two silently test different
dependency sets. This turns that into a build failure.

A version that cannot be extracted from either file is a failure, not a skip: a renamed property
or a reformatted declaration must not read as "in agreement".

Run: python3 scripts/check-build-parity.py
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
POM = (ROOT / "pom.xml").read_text(encoding="utf-8")
KTS = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")


def pom_property(name):
    return re.search(r"<%s>([^<]+)</%s>" % (re.escape(name), re.escape(name)), POM)


def pom_project_version():
    # The first <version> after the root <artifactId>, i.e. the reactor's own version.
    return re.search(r"<artifactId>code-karta</artifactId>\s*<version>([^<]+)</version>", POM)


def pom_managed_version(group, artifact):
    pattern = (
        r"<groupId>%s</groupId>\s*<artifactId>%s</artifactId>\s*<version>([^<]+)</version>"
        % (re.escape(group), re.escape(artifact))
    )
    return re.search(pattern, POM)


def kts_val(name):
    return re.search(r'val\s+%s\s*=\s*"([^"]+)"' % re.escape(name), KTS)


def kts_allprojects_version():
    return re.search(r'^\s*version\s*=\s*"([^"]+)"', KTS, re.MULTILINE)


CHECKS = [
    ("project version", pom_project_version(), "pom.xml <version>",
     kts_allprojects_version(), "build.gradle.kts version"),
    ("vibetags", pom_property("vibetags.version"), "pom.xml <vibetags.version>",
     kts_val("vibetagsVersion"), "build.gradle.kts vibetagsVersion"),
    ("junit", pom_property("junit.version"), "pom.xml <junit.version>",
     kts_val("junitVersion"), "build.gradle.kts junitVersion"),
    ("jspecify", pom_managed_version("org.jspecify", "jspecify"), "pom.xml jspecify dependency",
     kts_val("jspecifyVersion"), "build.gradle.kts jspecifyVersion"),
]

failures = []
for label, pom_match, pom_where, kts_match, kts_where in CHECKS:
    if pom_match is None:
        failures.append("%s: could not read a version from %s" % (label, pom_where))
        continue
    if kts_match is None:
        failures.append("%s: could not read a version from %s" % (label, kts_where))
        continue
    pom_value, kts_value = pom_match.group(1).strip(), kts_match.group(1).strip()
    status = "ok" if pom_value == kts_value else "MISMATCH"
    print("%-16s maven=%-10s gradle=%-10s %s" % (label, pom_value, kts_value, status))
    if pom_value != kts_value:
        failures.append(
            "%s: %s says %s, %s says %s" % (label, pom_where, pom_value, kts_where, kts_value)
        )

if failures:
    print("\nMaven and Gradle disagree:", file=sys.stderr)
    for f in failures:
        print("  - " + f, file=sys.stderr)
    print(
        "\nUpdate build.gradle.kts to match pom.xml. Dependabot only edits pom.xml, so a bump it "
        "opens leaves the Gradle build behind until someone mirrors it.",
        file=sys.stderr,
    )
    sys.exit(1)

print("\npom.xml and build.gradle.kts agree on all %d shared versions." % len(CHECKS))
