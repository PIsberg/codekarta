# Changelog

All notable changes to code-karta are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Entries before 0.3.0 were backfilled from git history and the GitHub release notes, so they
summarise each release rather than reproducing every commit. The `Full changelog` compare link on
each version heading is the complete record.

## [Unreleased]

### Changed

- VibeTags annotation processor 1.0.3 to 1.1.1. 1.0.4 was prepared upstream but never tagged or
  published, so this repository moves from 1.0.3 straight to the 1.1.x line and picks up both sets
  of changes. 1.1.1 fixes a missing `Platform.GEMINI_GRANULAR` arm in the upstream renderer
  registry, which matters here because this repository opted into Gemini granular rules in #75.
  1.1.1 changes generated output, so the committed guardrail files under `.claude/rules/`,
  `CLAUDE.md` and `GEMINI.md` are regenerated in the same change.
- Dependency updates: JUnit 6.1.2 to 6.1.3 (#82), PIT 1.25.8 to 1.25.9 (#80), ArchUnit (#78),
  CycloneDX Maven plugin (#77), JSpecify 1.0.0 to 1.0.1 (#76), and the CodeQL and Scorecard
  GitHub Actions (#70, #69).

### Added

- This changelog. Release history previously lived only in GitHub Releases.

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

[Unreleased]: https://github.com/PIsberg/codekarta/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/PIsberg/codekarta/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/PIsberg/codekarta/releases/tag/v0.1.0
