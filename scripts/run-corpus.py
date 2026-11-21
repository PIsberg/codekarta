#!/usr/bin/env python3
"""Parse real third-party Java and fail when the parsers quietly stop seeing it.

Parsers never throw. They log a warning and return a partial graph, which is the documented
contract, and it makes a parsing regression silent: the graph gets smaller and every unit test
still passes. JdkCorpusTest covers that for java.util on every build. This covers the shapes the
JDK does not have, against projects pinned by commit in corpus/corpus.json.

Nothing is vendored. Each project is cloned at its pinned sha and thrown away, so this repository
redistributes nobody else code.

Per project it asserts four things:
  exit 0                  the run completed
  no parser warnings      nothing was skipped on the way
  nodes >= minNodes       the graph did not collapse
  byte-identical reruns   KartaCli.run is idempotent, which docs/diagrams depends on

Run: python3 scripts/run-corpus.py --jar <fat jar> [--only guava] [--keep] [--measure]
"""

import argparse
import json
import os
import re
import shutil
import stat
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "corpus" / "corpus.json"

# Parser loggers are class-named under this package. The CLI also warns about oversized diagrams,
# which is advice to a user rather than a parse failure, so match the package and not the level
# alone.
PARSER_WARNING = re.compile(r"se\.deversity\.codekarta\.input[\w.$]*\s*$", re.MULTILINE)


def run(cmd, cwd=None):
    return subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, encoding="utf-8",
                          errors="replace")


def force_remove(path):
    """Delete a git checkout on Windows too.

    Git marks everything under .git/objects read-only, and shutil.rmtree refuses those. Passing
    ignore_errors instead would leave a directory with no .git in it, which the next run then
    fetches into, and the failure surfaces much later as a missing source path.
    """
    def clear_readonly(func, target, _exc):
        os.chmod(target, stat.S_IWRITE)
        func(target)

    if path.exists():
        shutil.rmtree(path, onerror=clear_readonly)


def head_sha(checkout):
    result = run(["git", "rev-parse", "HEAD"], cwd=checkout)
    return result.stdout.strip() if result.returncode == 0 else ""


def fetch(project, workdir):
    """Clone exactly one commit. No history, no other branches.

    Always starting from an empty directory: fetching into a half-deleted checkout produces a
    confusing failure a long way from its cause.
    """
    checkout = workdir / project["name"]
    if (checkout / ".git").is_dir() and head_sha(checkout) == project["sha"]:
        return checkout

    force_remove(checkout)
    checkout.mkdir(parents=True, exist_ok=True)
    steps = [
        ["git", "init", "--quiet"],
        ["git", "remote", "add", "origin", project["repo"]],
        ["git", "fetch", "--quiet", "--depth", "1", "origin", project["sha"]],
        ["git", "checkout", "--quiet", "FETCH_HEAD"],
    ]
    for step in steps:
        result = run(step, cwd=checkout)
        if result.returncode != 0:
            raise SystemExit("%s: %s failed | %s"
                             % (project["name"], " ".join(step), result.stderr.strip()))

    # A renamed repository still serves a redirect, so confirm we are on the commit the manifest
    # pins rather than on whatever the remote decided to send.
    actual = head_sha(checkout)
    if actual != project["sha"]:
        raise SystemExit("%s: asked for %s, checked out %s"
                         % (project["name"], project["sha"], actual))
    return checkout


def parser_warnings(output):
    return [line for line in output.splitlines() if PARSER_WARNING.search(line)]


def karta(jar, source, out, fmt="svg"):
    return run([
        "java", "-jar", str(jar),
        "--input", str(source),
        "--output", str(out),
        "--format", fmt,
    ])


def node_count(out_dir):
    files = list(out_dir.glob("*.json"))
    if not files:
        return 0
    return len(json.loads(files[0].read_text(encoding="utf-8")).get("nodes", []))


def check(project, jar, workdir, measure):
    name = project["name"]
    checkout = fetch(project, workdir)
    source = checkout / project["path"]
    if not source.is_dir():
        return name, False, "%s is not in the tree at %s" % (project["path"], project["sha"][:9]), 0

    started = time.time()
    first = workdir / (name + "-1")
    second = workdir / (name + "-2")
    graph = workdir / (name + "-json")

    svg = karta(jar, source, first)
    if svg.returncode != 0:
        return name, False, "exit %d\n%s" % (svg.returncode, svg.stderr[-800:]), 0

    warnings = parser_warnings(svg.stderr)
    if warnings:
        return name, False, "%d parser warning(s), first: %s" % (len(warnings), warnings[0]), 0

    if karta(jar, source, graph, fmt="json").returncode != 0:
        return name, False, "the json run failed where the svg run succeeded", 0
    nodes = node_count(graph)
    if nodes < project["minNodes"]:
        return name, False, "%d nodes, floor is %d" % (nodes, project["minNodes"]), nodes

    karta(jar, source, second)
    produced = sorted(p.name for p in first.glob("*"))
    for filename in produced:
        if (first / filename).read_bytes() != (second / filename).read_bytes():
            return name, False, "two runs produced different bytes for " + filename, nodes

    elapsed = time.time() - started
    detail = "%d nodes, %.0fs" % (nodes, elapsed)
    if measure:
        detail += "  <- set minNodes below %d" % nodes
    return name, True, detail, nodes


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", required=True, help="the code-karta-cli fat jar")
    parser.add_argument("--only", help="run a single project by name")
    parser.add_argument("--work", default=None, help="where to clone (default: target/corpus)")
    parser.add_argument("--keep", action="store_true", help="keep the clones for a rerun")
    parser.add_argument("--measure", action="store_true",
                        help="report node counts for setting floors")
    args = parser.parse_args()

    jar = Path(args.jar).resolve()
    if not jar.exists():
        raise SystemExit("no jar at %s. Build it with: mvn -pl code-karta-cli -am package" % jar)

    workdir = Path(args.work) if args.work else ROOT / "target" / "corpus"
    workdir.mkdir(parents=True, exist_ok=True)

    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    projects = manifest["projects"]
    if args.only:
        projects = [p for p in projects if p["name"] == args.only]
        if not projects:
            raise SystemExit("no project named %s in %s" % (args.only, MANIFEST))

    failures = []
    for project in projects:
        name, ok, detail, _ = check(project, jar, workdir, args.measure)
        print("%-18s %-5s %s" % (name, "ok" if ok else "FAIL", detail.replace("\n", " ")))
        if not ok:
            failures.append(name)

    if not args.keep:
        force_remove(workdir)

    if failures:
        print("\ncorpus failed: " + ", ".join(failures), file=sys.stderr)
        return 1
    print("\n%d project(s) parsed clean." % len(projects))
    return 0


if __name__ == "__main__":
    sys.exit(main())
