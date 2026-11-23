# Code Quality Gate

Every `mvn verify` runs the full gate, and all of it is blocking. Mutation testing runs as its
own CI job rather than inside `verify`, because it is slow, not because it is optional.

| Check | Configuration | Enforces |
|---|---|---|
| Checkstyle | [`checkstyle.xml`](../checkstyle.xml) | Style and formatting |
| PMD + CPD | [`pmd-ruleset.xml`](../pmd-ruleset.xml) | Static analysis and copy-paste detection |
| SpotBugs | [`spotbugs-exclude.xml`](../spotbugs-exclude.xml) | Bug patterns; the exclude file documents the intentional ones |
| [Error Prone](https://errorprone.info/) | `default-compile` execution in [`pom.xml`](../pom.xml) | Compile-time bug detection, main sources only |
| ArchUnit | [`ArchitectureRulesTest`](../code-karta-cli/src/test/java/se/deversity/codekarta/cli/ArchitectureRulesTest.java) | The 3-tier boundaries, as fitness functions rather than convention |
| [JSpecify](https://jspecify.dev/) | `package-info.java` per package | All main packages are `@NullMarked` |
| [CycloneDX](https://cyclonedx.org/) | `mvn package` | SBOM at `target/bom.json` |
| [JaCoCo](https://www.jacoco.org/jacoco/) | `jacoco.line.minimum` per module | Line coverage floor; report at `target/site/jacoco/` |
| PIT | `pitest.mutation.minimum` per module | Mutation score floor, in its own CI job |
| Build parity | [`scripts/check-build-parity.py`](../scripts/check-build-parity.py) | `pom.xml` and `build.gradle.kts` name the same versions |
| Idempotent regeneration | `git diff --exit-code` step in [`build.yml`](../.github/workflows/build.yml) | Regenerated diagrams and guardrails match what is committed |
| Maven Invoker | [`code-karta-maven-plugin/src/it/`](../code-karta-maven-plugin/src/it/) | The plugin goal driven from real Maven builds, against the poms in [`MAVEN-PLUGIN.md`](MAVEN-PLUGIN.md) |
| JDK corpus | [`JdkCorpusTest`](../code-karta-cli/src/test/java/se/deversity/codekarta/cli/JdkCorpusTest.java) | The pipeline over 135 files of real `java.util`, from the JDK's own `src.zip` |
| Third-party corpus | [`corpus/corpus.json`](../corpus/corpus.json), [`run-corpus.py`](../scripts/run-corpus.py) | Four pinned projects parsed weekly by [`corpus.yml`](../.github/workflows/corpus.yml) |
| Viewer in a browser | [`HtmlViewerBrowserTest`](../code-karta-render/src/test/java/se/deversity/codekarta/render/HtmlViewerBrowserTest.java) | The `--viewer` page driven in headless HtmlUnit from a `file://` URL: switching, search, edge toggles, deep links, and the SVG sanitizer against a hostile diagram |

The ArchUnit rules are what stop the architecture from decaying quietly: the tier rule in
[`ARCHITECTURE.md`](ARCHITECTURE.md) used to be upheld only by convention, and a fitness function
turns "we agreed not to" into a failing build.

The last three rows exist for the same reason. A version split between the two builds, a diagram
that no longer regenerates to the same bytes, and a mutation profile nobody runs are all things
that were true of this repository while every check was green.

## Corpus evaluation

Parsers never throw. They log a warning and return a partial graph, so a parsing regression is
silent: the graph gets smaller and every unit test still passes. The fixtures cannot see that
either, being eleven files in `example-shipping-system` and thirty-five of our own. Two gates
close the gap, and both assert the same four things rather than what a diagram should look like,
because there is no ground truth for that.

| | Runs | Source | Cost |
|---|---|---|---|
| `JdkCorpusTest` | every build | `$JAVA_HOME/lib/src.zip`, top level of `java.util`; ELK and `--sequence-only` over a 24-file collections subset | ~35s |
| `run-corpus.py` | weekly, and on demand | four projects pinned by commit, each in class mode plus extra modes | ~8.5 min plus clones |

What they assert: the run completes, the parsers log **no** warnings, the node count clears a
measured floor, and two runs produce byte-identical output.

Nothing is vendored. The JDK tier reads the `src.zip` that every JDK already ships, so there is
nothing to download and no third-party source in this repository. The external tier clones each
project at its pinned commit and throws it away, which is why the licences in `corpus.json` are
recorded but do not constrain this repository: a clone is not redistribution.

Every project runs in class mode, and `modes` in `corpus.json` adds the others, because each fails
in its own way: `--sequence-only` through the symbol solver, `--state-machine` through enum and
switch analysis, `--split-packages` through the per-package walk, `--layout elk` through ELK. Until
2026-11-23 only class mode ran anywhere, and the first run of the others found that sequence output
is not stable between runs on the multi-file projects (#295). Measured 2026-11-23 on the pinned
commits, nodes summed over every file a mode writes, floors set roughly ten percent below so that
re-pinning does not immediately fail the build:

| Project | Why it is here | class | sequence | state | split | elk |
|---|---|---|---|---|---|---|
| `guava` | generics at their most brutal | 626 / 560 | not run (#295) | 21 / 18 | 626 / 560 | 626 / 560 |
| `jackson-databind` | deep inheritance, and already a dependency | 826 / 740 | not run (#295) | none to find | 1158 / 1040 (36 files) | 826 / 740 |
| `junit5` | interface-first architecture | 235 / 210 | not run (#295) | none to find | 246 / 220 (9 files) | 235 / 210 |
| `picocli` | one file of 19,329 lines | 191 / 170 | 2078 / 1870 | 51 / 45 | not run | 191 / 170 |

Each cell is measured / floor. "None to find" means the project has no state machine, so the CLI
rightly writes nothing; "not run (#295)" is a mode left out until its output is stable. The class
floors were 190, 495, 165 and 2 before the parsers learned the field shapes, member types and
constructors that #265 to #271 added; they rose with the measurement, as the rule below says.

`picocli` earns its place on file size: two top-level types in twenty thousand lines that no
fixture comes close to. Its 191 class-mode nodes are almost all nested types, which the class
parser has drawn since records, enums and nested declarations were taken in (#227). Being one
file, it is also the one project whose `--sequence-only` output is stable today.

The external tier is deliberately off the pull-request path. It is minutes of work and a pile of
network calls, Central has rate-limited this repository once already, and nothing it finds needs
to block a merge.

Both tiers passed on the day they were written. They are regression gates, not a bug hunt.

## Coverage and mutation floors

Both are floors, not targets. Raise one when the number rises; never lower one to turn a red
build green. Measured 2026-11-23 on JDK 21 (line coverage from JaCoCo in `mvn clean verify`,
mutation score from PIT with the command below):

| Module | Line coverage | Mutation score | Line floor | Mutation floor |
|---|---|---|---|---|
| `code-karta-core` | 100.0% | 92.6% (25/27) | 0.95 | 90 |
| `code-karta-layout` | 97.2% | 73.5% (86/117) | 0.90 | 70 |
| `code-karta-render` | 99.5% | 90.0% (377/419) | 0.95 | 85 |
| `code-karta-input` | 86.8% | 78.4% (664/847) | 0.82 | 72 |
| `code-karta-cli` | 96.7% | 92.7% (166/179) | 0.92 | 88 |
| `code-karta-maven-plugin` | 91.5% | 71.0% (49/69) | 0.88 | 65 |
| **Total** | **91.8%** (2592/2822) | **82.4%** (1367/1658) | | |

Each floor sits a few points under its measurement, so a regression fails the build while run
to run noise in PIT's timeouts does not.

`code-karta-render` is why both numbers are reported. On 2026-08-29 it executed 95.5% of its
lines and killed 24.3% of its mutants: the tests called the renderer and asserted little more than
that an `<svg` came back. `SvgRendererGeometryTest` and `SequenceDiagramGeometryTest` now pin
the computed geometry (canvas size, box and compartment heights, label placement and ellipsis,
edge anchors and bows, legend rows, frame bounds, lifelines, activation bars, arrows) and the
markup (escaping, CSS override, markers), working each expected number out from the renderer's
constants in a comment. That took the module to 90.0%. Line coverage alone would have called it
well tested before and after.

`code-karta-cli` was at 45.7% line coverage in the module that owns argument parsing and exit
codes. `main` now delegates to `KartaCli.execute(args, out, err)`, which returns the exit status
instead of calling `System.exit`, so `KartaCliTest` covers every branch of it: help (pinned
whole against `src/test/resources/usage.txt`), a bad argument (exit 1), success, a declined
diagram, `--split-packages` with and without output, and an unwritable output (exit 2).

`code-karta-layout` fell from 78.7% to 73.5% when group-aware layout landed (#272): 28 new
mutants, 8 of them killed. It clears its floor of 70; its block packing is the place to add
assertions next.

## Mutation testing

PIT is slow, so it runs as a separate CI job rather than inside `mvn verify`. It is not optional:
a module below its `pitest.mutation.minimum` fails.

```bash
mvn -B -Pmutation test-compile org.pitest:pitest-maven:mutationCoverage
```

`test-compile` runs first on purpose. A bare plugin goal resolves sibling modules from the
repository rather than building them, so without a lifecycle phase PIT can measure the last
published version instead of the working tree.

## A note on the local JDK

The gate targets Java 21 and CI runs Java 21. JaCoCo cannot instrument class files from a much
newer JDK: on Java 26 it throws instrumentation errors that read like a defect in this repository
and are not. Before treating a red static-analysis gate as a real finding, check `java -version`
and rerun on 21.

## AI guardrails

Source-level guardrails are generated by the VibeTags annotation processor during compilation and
land in `CLAUDE.md` and `llms.txt`. They are not a build gate — they are instructions to coding
agents. See [`VIBETAGS.md`](VIBETAGS.md).
