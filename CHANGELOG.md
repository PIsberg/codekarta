# Changelog

All notable changes to code-karta are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Entries before 0.3.0 were backfilled from git history and the GitHub release notes, so they
summarise each release rather than reproducing every commit. The `Full changelog` compare link on
each version heading is the complete record.

## [Unreleased]

## [0.3.0] - 2026-08-23

### Changed

- VibeTags annotation processor 1.0.3 to 1.1.1. 1.0.4 was prepared upstream but never tagged or
  published, so this repository moves from 1.0.3 straight to the 1.1.x line and picks up both sets
  of changes. 1.1.1 fixes a missing `Platform.GEMINI_GRANULAR` arm in the upstream renderer
  registry, which matters here because this repository opted into Gemini granular rules in #75.
  1.1.1 changes generated output, so the committed guardrail files under `.claude/rules/`,
  `CLAUDE.md` and `GEMINI.md` are regenerated in the same change.
- VibeTags 1.1.1 to 1.2.5 (#97 and this release). Generated output is byte-identical to 1.2.0:
  `mvn clean verify` regenerated `CLAUDE.md`, `GEMINI.md`, `llms.txt`, `.claude/rules/` and
  `docs/diagrams/` with no diff, and the Gradle regeneration matched.
- Dependency updates over the whole 0.2.0 to 0.3.0 range: Checkstyle 13.9.0 to 14.0.0 (#105,
  #110), Jackson 2.22.1 to 2.22.2 (#106, #109), JUnit 6.1.2 to 6.1.3 (#82), JaCoCo 0.8.13 to
  0.8.15 (#88), SpotBugs Maven plugin 4.10.3.0 to 4.10.4.0, JSpecify 1.0.0 to 1.0.1 (#76),
  PIT 1.25.8 to 1.25.9 (#80), ArchUnit (#78, #90), CycloneDX Maven plugin (#77), the Shadow
  Gradle plugin 9.6.0 to 9.6.1 (#91), and the GitHub Actions: cache (#93), setup-java (#94),
  CodeQL to v4.37.8 (#70, #104), Scorecard (#69), harden-runner (#102).
- The Gradle wrapper moves from 9.5.1 to 9.7.1. `./gradlew wrapper` rewrites
  `gradle-wrapper.properties` from its own defaults, which resets `retries` to 0. That is the
  single-CDN-blip failure this file's own Fixed entry below describes, so the bump restores
  `retries=3` and `retryBackOffMs=2000` explicitly.
- SpotBugs Maven plugin 4.10.4.0 requires Maven 3.8.9 or newer. Nothing in this repository
  declares a minimum Maven version, so on an older Maven the build now fails with the plugin's
  own message rather than a version check. See the follow-up issue linked from the release PR.
- PMD 7.26.0, Error Prone 2.50.0, JavaParser 3.28.2 and ELK 0.12.0 were already at their latest
  releases. Xtext stays at 2.43.0: the only newer version published is the 2.44.0.M3 milestone.

### Added

- An indexed VibeTags root (#74), so an agent session no longer loads every module's guardrails
  up front. A `.vibetags-root-index` marker keeps the always-on safety tier (`@AILocked`,
  `@AICore`, `@AIAudit`) inline in `CLAUDE.md` and indexes the rest into 26 per-module files
  under `<module>/.claude/rules/`, which load when a matching source file is opened. Root
  `CLAUDE.md`: 537 lines to 137. Verified against the tree: 137 lines, marker present, 26 rule
  files.
- This changelog. Release history previously lived only in GitHub Releases.
- `scripts/check-build-parity.py`, run as its own CI job, fails the build when `pom.xml` and
  `build.gradle.kts` name different versions. They had already drifted on `main`: the Gradle
  build resolved jspecify 1.0.0 and junit 6.1.2 against a pom that said 1.0.1 and 6.1.3, so the
  two CI jobs were testing different dependency sets. A version that cannot be read from either
  file fails too, so a rename cannot pass as agreement.
- A `gradle` ecosystem entry in `dependabot.yml`. Only `maven` and `github-actions` were declared,
  which is why the Gradle build had no way to keep up.
- JaCoCo line-coverage reporting and a per-module floor. Measured: core 100%, layout 96.1%,
  render 95.8%, input 82.7%, cli 45.0%, overall 83.5%.
- A mutation-score floor per module, and a CI job that runs PIT. The `mutation` profile had
  existed for months with no workflow referencing it. Measured: core 100%, layout 76.4%,
  input 68.1%, cli 34.5%, render 24.1%, overall 49.7% of 1203 mutants.
- A CI step asserting that regeneration is idempotent and committed. `KartaCli.run` is documented
  as producing byte-identical SVG, and downstream repositories commit its output, but nothing
  checked it.
- `.gitattributes`, so generated files have the same bytes regardless of which OS built them.

### Fixed

- `build.gradle.kts` no longer puts `mavenLocal()` ahead of Maven Central unconditionally; it is
  opt-in via `-PuseMavenLocal`. An artifact present only because it was built on the local
  machine could satisfy the build, which is how a bump to an unpublished VibeTags 1.0.4 passed
  locally while being unresolvable from Central.
- CI ran `mvn clean test` and then `mvn clean verify -DskipTests`, so the second clean discarded
  the first run's output and no run ever executed the tests and the static-analysis gate
  together. It is now one `mvn -B clean verify`.
- The push trigger now includes `chore/**` and `docs/**`; branches with those prefixes got no CI
  until a pull request opened.
- Source files walked from disk are sorted. `Files.walk` returns entries in a filesystem-defined
  order — NTFS by name, ext4 with `dir_index` by hash — and no walk site sorted, so the same
  source tree produced one node order on Windows and another on Linux. `KartaCli.run` was
  idempotent per machine, not across machines, which is the case that matters when downstream
  repositories commit the generated SVGs. Found by the new idempotency step on its first run.
- `gradlew` and `example-shipping-system/gradlew` are committed executable. CI was running
  `chmod +x` to compensate, which is also a papercut when cloning on Linux or macOS.
- The Gradle wrapper retries its distribution download (`retries=0` meant a single CDN blip
  failed the job) and the example job caches the distribution instead of fetching it every run.

## [0.2.0] - 2026-07-31

Five defects reported by a downstream repository that generates and commits code-karta diagrams,
plus one found while verifying the fix against it.

No published signature changed. `KartaCli.run(...)` gained a `RunOptions` overload; the existing
overloads remain and delegate to it.

### Added

- `--modules-only` reads Maven `<modules>` and Gradle `include(...)`, not just JPMS. A reactor
  without a `module-info.java` previously produced `Graph is empty`. `HAS` edges carry
  aggregation, `REQUIRES` carries dependencies on siblings inside the same reactor;
  dependencies outside it are dropped. `module-info.java` still wins when both are declared.
  Poms are parsed with entity resolution off, since the input is somebody else's build file.
- `--output-name <file>` names the output file. Every class diagram was previously called
  `class-diagram.svg`, so N diagrams needed N output directories. The name is treated as data
  rather than as a path: anything that would land outside `--output` is refused and the derived
  name is used instead.
- `--max-members <n>|all` exposes the `...(+N more)` compartment cap, previously a private
  constant fixed at 6.
- `--split-packages` emits one diagram per package.
- A warning when the rendered diagram is too large to read, measured against the rendered canvas.

### Fixed

- Generated SVGs end with a newline and carry no trailing whitespace, so `end-of-file-fixer` and
  `trailing-whitespace` hooks no longer rewrite every regenerated file. Both renderers assert this
  line by line. **This changes the bytes of every generated diagram**: regenerating after
  upgrading shows one diff per file, and is stable after that.
- A relative `--input` normalised the root pom to the bare path `pom.xml`, whose parent is `null`,
  so every `<module>` looked unreadable and the Gradle fallback quietly produced a
  plausible-looking diagram instead.
- The layout wraps a depth level instead of emitting one unbounded row.
- A state-machine diagram with no transitions is declined rather than rendered empty.
- Labels fit inside diagram boxes, and diagrams stay project-only.

## [0.1.0] - 2026-07-23

First published release, under the `se.deversity.codekarta` group on Maven Central.

### Added

- The three-tier pipeline: `code-karta-core` (IR), `code-karta-input`, `code-karta-layout`,
  `code-karta-render`, wired by `code-karta-cli`. `ArchitectureRulesTest` enforces the tier
  boundaries with ArchUnit.
- Java source parsers for class, sequence, and multi-file stitched sequence graphs, with symbol
  resolution and a stdlib and logging noise filter.
- Two layout engines: a simple built-in engine and an ELK layered engine behind `--layout`.
- SVG rendering with UML compartments, sequence lifelines, and consumer-themeable CSS classes.
- The `mvn verify` quality gate: Checkstyle, PMD with CPD, SpotBugs, Error Prone, PIT mutation
  testing behind a profile, JSpecify nullness annotations, and CycloneDX SBOM generation.
- VibeTags annotations across all modules, generating the guardrail regions in `CLAUDE.md` and
  the per-module rules.
- GitHub Actions for build, test, and Maven Central publishing.

[Unreleased]: https://github.com/PIsberg/codekarta/compare/v0.3.0...HEAD
[0.3.0]: https://github.com/PIsberg/codekarta/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/PIsberg/codekarta/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/PIsberg/codekarta/releases/tag/v0.1.0
