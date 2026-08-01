# AI Guardrails (VibeTags)

code-karta annotates its own source with [VibeTags](https://github.com/PIsberg/vibetags), a
compile-time Java annotation processor that turns source annotations into AI-platform guardrail
files. The annotations are `RetentionPolicy.SOURCE` — zero runtime cost, nothing on the classpath
at run time.

The generated regions live between `<!-- VIBETAGS-START -->` and `<!-- VIBETAGS-END -->` in:

- `CLAUDE.md` — the always-loaded agent briefing
- `llms.txt` — the llms.txt-standard orientation file

**Never hand-edit inside those markers.** The next compile overwrites the region. Change the
annotation in the Java source instead, then recompile.

A local copy of the upstream annotation reference is vendored at
`.claude/skills/vibetags-usage/SKILL.md` and loads on demand.

## What is tagged, and why

| Annotation | Where | What it protects |
|---|---|---|
| `@AIArchitecture` | every main class in input / layout / render / core | The tier boundaries, as machine-readable `cannotReference` lists |
| `@AIContext` | parsers, layout engines, renderers | The design decision a reader cannot infer from the code — why the two-pass exception walk exists, why `rawType()` runs before the skip-list lookup |
| `@AILocked` | `NodeType`, `EdgeType`, `NodeDimensions` | Enum string values used as CSS selectors and matched by identity across all parsers |
| `@AICore`, `@AISchemaSafe`, `@AIDomainModel`, `@AIStrictTypes` | `Graph`, `Node`, `Edge`, `Group` | The IR: serialised schema, framework-free purity, precise types |
| `@AIContract` | `LayoutEngine`, `InputParser`, `KartaCli` | Signatures other code and tests bind to |
| `@AILoadBearing` | `SvgRenderer.renderNode`, `ParserSupport` | Code that *looks* wrong and is deliberate — the silent null-coordinate skip, and the per-parser try/catch that must not be "deduplicated" into the shared helper |
| `@AIKeepInSync` | `RunOptions` | The CLI flag list, duplicated in `printUsage()` and two docs tables with nothing checking them |
| `@AIParallelTests` | `ClassDiagramParser`, `BuildReactorParser`, `SvgRenderer`, `SequenceDiagramRenderer` | Test isolation for the classes whose suites must stay fork-safe |
| `@AIIdempotent` | `KartaCli.run` | Byte-identical SVG on re-run — the verify-phase diagram regeneration depends on it |

## Test sources are not processed

Test-scoped guardrails such as `@AIParallelTests` go on the **main class the tests cover**, not on
the test class. The annotation reads the same way — "tests for this element must be parallel-safe" —
and it keeps one module's guardrails coming from one place.

This began as a workaround for a processor bug. `compile` and `test-compile` are two javac rounds
over disjoint sources that both mapped to the *same* module id, and the processor rewrote that
module's whole region from whatever the current round saw. With test sources processed, the test
round erased every main-source guardrail for `code-karta-input` and `code-karta-render`, and
`mvn compile` and `mvn test` produced different `CLAUDE.md` files — silently, with a green build.

**Fixed upstream in 1.0.0-RC8** ([#330](https://github.com/PIsberg/vibetags/issues/330)). Each
source set now owns its own sidecar (`.vibetags-mod-code-karta-input__test`) while sharing the
module's region id, so the two rounds no longer collide. Verified here: with `<proc>full</proc>` on
`default-testCompile` and `@AIParallelTests` on `BuildReactorParserTest`, `mvn clean test-compile`
left `CLAUDE.md` byte-identical and *added* the test's rule file alongside the twelve main-source
ones instead of replacing them.

Maven still sets `<proc>none</proc>` on `default-testCompile` (see
[`BUILD.md`](BUILD.md#annotation-processing)) and Gradle still declares no `testAnnotationProcessor`
— now a scoping choice rather than a workaround. Guardrails describe the shipped API, so test
classes have nothing to add to them. Enabling it is safe if that ever stops being true.

## Maven is the writer, by choice

Gradle used to be incapable of it. It compiles with the processor on the classpath but regenerated
nothing, reporting `0 active services`, because it resolved its output relative to the compiler's
working directory — under Gradle the subproject, not the repository root. Pointing it at the root
with `-Avibetags.root=${rootDir}` made it write, and made it write *wrongly*: it identified each
module by a content hash (`<!-- VIBETAGS-MODULE: 163e79de -->`) rather than by the module name Maven
uses, so it appended a second copy of all five regions instead of replacing them. The ghost outlived
the build that caused it, restored on every later run from a gitignored `.vibetags-mod-<hash>`
sidecar that a `git checkout` of `CLAUDE.md` could not remove.

**Fixed upstream in 1.0.0-RC8** ([#331](https://github.com/PIsberg/vibetags/issues/331)). The root
cause was that VibeTags declares itself an `aggregating` incremental processor, so Gradle hands it a
wrapped `ProcessingEnvironment` and `Trees.instance` accepts only javac's own — module resolution
returned nothing for every module and they all collapsed onto the JVM working directory. Identity
now falls back to `Elements.getFileObjectOf`. Verified here: with `-Avibetags.root=${rootDir}`
restored, Gradle resolved the root correctly, logged `[multi-module: 5 modules]`, named all five
regions exactly as Maven does, created no hash sidecar, and produced a byte-identical `CLAUDE.md`.

The option is still absent from `build.gradle.kts`, now for a smaller reason: a second writer buys
nothing when the two agree, and the Trees API remains unavailable under Gradle, so its rounds log
`Trees API not available for AST architectural import scanning` and skip the import scan behind
`@AIArchitecture`'s `cannotReference` lists. Maven's rounds do that scan. **Regenerate guardrails
with Maven.**

## Layout: indexed root + per-module scoped rules

code-karta uses the "reactor, lean" tier layout from the VibeTags README:

| Tier | File | Loaded |
|---|---|---|
| 1 | root `CLAUDE.md` + `.vibetags-root-index` | always |
| 3 | `code-karta-<module>/.claude/rules/*.md` | when a matching source file is opened |

`.vibetags-root-index` is an empty marker file — its presence is what makes the root a lean index
instead of a merged block. Each module's `.claude/rules/` directory is likewise an opt-in marker:
create it and the module's per-element detail moves there, delete it and the detail moves back
inline. Both are committed, with a `.gitkeep` so the empty directory survives a fresh clone.

The effect on always-loaded context is the whole point:

```text
merged root      537 lines in CLAUDE.md, every guardrail loaded every session
indexed root     137 lines in CLAUDE.md + 26 scoped files loaded on demand
```

A **root** `.claude/rules/` used to be the wrong tool here: it is a single-module mechanism, and
in a reactor each module's compile orphan-cleaned the files the previous module had written, so the
last module won (upstream [#295](https://github.com/PIsberg/vibetags/issues/295), closed as
by-design) — a build left 1 rule file on disk and the rest of the pointers in `CLAUDE.md` dangling.

RC8's cross-module cleanup exclusion ([#330](https://github.com/PIsberg/vibetags/issues/330),
[#319](https://github.com/PIsberg/vibetags/issues/319)) ends that: every sidecar records the stems
it wrote and each cleanup pass spares every other sidecar's. Verified here — `mkdir .claude/rules &&
mvn clean compile` now leaves all 26 rule files at the root, one per annotated element across all
five modules, with nothing dangling.

Per-module scoped rules stay the layout anyway: they keep each module's guardrails next to the code
they constrain, and a root directory would now duplicate all 26 files rather than corrupt them.

The indexed root also used to keep *nothing* inline, so the always-on safety guardrails —
`@AILocked` on `NodeType` / `EdgeType` / `NodeDimensions`, `@AICore` on the IR classes, `@AIAudit`
on `KartaCli` — only loaded once an agent opened the very file they protect, by which point they
have become a comment. **Fixed upstream in 1.0.0-RC8**
([#332](https://github.com/PIsberg/vibetags/issues/332)): each module now contributes its safety
digest inline, followed by the pointer to its scoped rules. That is the whole 85 → 137 line
difference above. The invariants section of `CLAUDE.md` still restates the load-bearing ones in
prose, which is now belt and braces rather than the only copy.

## Adding a guardrail

1. Add the annotation to the main-source class or method.
2. `mvn -o compile` — the processor rewrites the generated regions and logs what it saw to
   `vibetags.log`.
3. Check `vibetags.log` for `[WARNING]` lines; contradictory or empty annotations are reported
   there, not as build failures.
4. Commit the source change **and** the regenerated `CLAUDE.md` / `llms.txt` together.

The processor never creates files — it only updates ones that already exist. Deleting
`CLAUDE.md` or `llms.txt` opts the project out of that platform permanently.
