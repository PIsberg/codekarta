# Security Policy

## Supported versions

code-karta follows semantic versioning. Security fixes land on the latest minor release only.
There is no long-term support branch.

| Version | Supported |
|---|---|
| 0.3.x | Yes |
| < 0.3 | No |

## Reporting a vulnerability

Do not open a public issue for a security problem.

Report it through [GitHub private vulnerability reporting](https://github.com/PIsberg/codekarta/security/advisories/new),
which is enabled on this repository. If that is not available to you, email
`isberg.peter@gmail.com` with `code-karta security` in the subject.

Please include:

- the version, and whether you hit it through the CLI or the library API
- the input that triggers it, reduced as far as you can reasonably reduce it
- what you observed, and what you expected instead

## What to expect

code-karta is maintained by one developer outside of working hours. The timelines below are
intentions, not a contractual SLA:

| Stage | Target |
|---|---|
| Acknowledgement | 7 days |
| Initial assessment | 14 days |
| Fix or documented mitigation for a confirmed issue | 90 days |

You will be credited in the release notes unless you ask not to be.

## Threat model

code-karta reads Java source files and writes SVG files. It does not execute the code it analyses,
open network connections, or read anything outside the paths it is given. The interesting failure
modes are therefore:

- **Path traversal or unintended writes.** `KartaCli` resolves the `--output` directory and derives
  filenames from input identifiers. A crafted class or file name that escapes the output directory
  is a vulnerability. This is why `KartaCli` carries an `@AIAudit` guardrail naming exactly these
  two checks.
- **Denial of service through pathological input.** A source tree that drives the parser or layout
  engine into unbounded memory or time. Report it if you find one, though be aware that graph
  construction is O(n) per node with an index and large inputs are expected to be slow rather than
  unbounded.
- **Dependency vulnerabilities.** Reported through Dependabot and the `dependency-review` workflow.
  A CycloneDX SBOM is attached to every release. If you are reporting a transitive CVE, an issue is
  fine; that is public information already.

Analysing untrusted source code with code-karta is a supported use. Running the SVG it produces
through a browser is subject to your own sanitisation policy: the renderer escapes text content,
but the output is a file you should treat with the same care as any other generated document.
