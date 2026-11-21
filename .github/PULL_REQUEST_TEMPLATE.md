## What this changes

<!-- One or two sentences. The diff shows what changed; say what it does. -->

## Why

<!-- Which failure does this prevent, or which need does it serve? Link the issue. -->

Closes #

## How it was verified

<!-- Be specific. "mvn clean verify passes" plus anything you did beyond the gate.
     If a test was written after the fix, say whether you broke the fix to confirm the test
     goes red. If something could not be verified, say so here rather than leaving it implied. -->

- [ ] `./mvnw clean verify` passes locally on JDK 21
- [ ] Behaviour changes have a test in the existing suite
- [ ] `CHANGELOG.md` updated under `## [Unreleased]` if a consumer would notice this
- [ ] Generated output (`docs/diagrams/`, `CLAUDE.md`, `GEMINI.md`, `llms.txt`) regenerated and
      committed, or unaffected
- [ ] Public API change, if any, is reflected in `COMPATIBILITY.md`

## Work left behind

<!-- Anything deferred, out of scope, or a known limitation gets a linked issue.
     A merged PR body is not a backlog. Write "None" if there is none. -->
