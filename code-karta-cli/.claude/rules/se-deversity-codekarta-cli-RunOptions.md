---
paths: ["**/RunOptions.java"]
---

<!-- VIBETAGS-START -->
# Rules for RunOptions

## Immutable Type
- **Rule**: This type is immutable. Never introduce non-final fields, setters, or mutating methods.
- **Note**: The compact constructor defensively copies customExcludes and defaults layout; withOutputName returns a new instance rather than mutating. Callers pass the same RunOptions down the whole pipeline, so a mutator here would let one stage change another stage's inputs.

## Mirrored — Keep In Sync
- **Rule**: Free to change, but every mirror must change in the same commit.
- **Mirrors**: KartaCli.printUsage(), docs/CLI.md flag table, docs/SKILL.md flag list
- **Reason**: Every component here is a user-facing CLI flag. Adding or renaming one without updating the usage text and the docs tables leaves the flag undiscoverable — nothing in the build catches the gap.
- **Enforced by**: nothing — a partial edit desyncs silently
<!-- VIBETAGS-END -->
