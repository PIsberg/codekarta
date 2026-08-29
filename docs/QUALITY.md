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
| `JdkCorpusTest` | every build | `$JAVA_HOME/lib/src.zip`, top level of `java.util` | ~9s |
| `run-corpus.py` | weekly, and on demand | four projects pinned by commit | ~30s plus clones |

What they assert: the run completes, the parsers log **no** warnings, the node count clears a
measured floor, and two runs produce byte-identical output.

Nothing is vendored. The JDK tier reads the `src.zip` that every JDK already ships, so there is
nothing to download and no third-party source in this repository. The external tier clones each
project at its pinned commit and throws it away, which is why the licences in `corpus.json` are
recorded but do not constrain this repository: a clone is not redistribution.

Measured on the pinned commits, with floors set roughly ten percent below so that re-pinning does
not immediately fail the build:

| Project | Why it is here | Nodes | Floor |
|---|---|---|---|
| `guava` | generics at their most brutal | 215 | 190 |
| `jackson-databind` | deep inheritance, and already a dependency | 550 | 495 |
| `junit5` | interface-first architecture | 183 | 165 |
| `picocli` | one file of 19,329 lines | 2 | 2 |

`picocli` earns its place on file size rather than node count: only two top-level types, in twenty
thousand lines that no fixture comes close to.

The external tier is deliberately off the pull-request path. It is minutes of work and a pile of
network calls, Central has rate-limited this repository once already, and nothing it finds needs
to block a merge.

Both tiers passed on the day they were written. They are regression gates, not a bug hunt.

## Coverage and mutation floors

Both are floors, not targets. Raise one when the number rises; never lower one to turn a red
build green. Measured 2026-08-29 on JDK 21:

| Module | Line coverage | Mutation score | Line floor | Mutation floor |
|---|---|---|---|---|
| `code-karta-core` | 100.0% | 92.6% (25/27) | 0.95 | 90 |
| `code-karta-layout` | 98.1% | 78.7% (70/89) | 0.90 | 70 |
| `code-karta-render` | 95.5% | 24.3% (99/407) | 0.90 | 20 |
| `code-karta-input` | 82.8% | 68.1% (346/508) | 0.78 | 62 |
| `code-karta-cli` | 45.7% | 35.9% (71/198) | 0.40 | 30 |
| `code-karta-maven-plugin` | 89.1% | 61.0% (36/59) | 0.85 | 55 |
| **Total** | **83.7%** | **50.2%** (647/1288) | | |

`code-karta-core` was at 26/26 when this table was last measured and is now 25/27: indexing
`Graph` by node id added two mutants and left one alive. It clears its floor of 90 with 2.6
points to spare, which is the margin to watch rather than a number to celebrate.

`code-karta-render` is the reason both numbers are reported. It executes 95.5% of its lines and
kills 24.3% of its mutants, which is what it looks like when tests call the renderer and assert
almost nothing about the SVG it returns. Line coverage alone would have called that module well
tested. Its floors are set at the measured value so the gap cannot widen while it is closed.

`code-karta-cli` is the other one worth attention: 45.7% line coverage in the module that owns
argument parsing and file output.

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
