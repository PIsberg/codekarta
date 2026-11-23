// Parses every .mmd and .dot file under the given directories with the real Mermaid and Graphviz
// parsers, and exits non-zero if any file is rejected (#293). code-karta's own tests pin the text
// the renderers write; only the parsers can say whether a Mermaid or Graphviz version accepts it.
//
//   npm ci --ignore-scripts && node validate.mjs <dir>...
//
// A file with a <file>.expect beside it is also drawn, and every line of the .expect file must be
// the full text of some drawn label (#305).
//
// A run that finds nothing to check fails too, and so does one that misses any of the four Mermaid
// diagram kinds code-karta writes: a check that passes because it saw no input is not a check.
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
// Sets up the DOM Mermaid needs to load and to draw; must come before mermaid is imported.
import { dotLabels, mermaidLabels } from './drawn.mjs';

const { default: mermaid } = await import('mermaid');
const { Graphviz } = await import('@hpcc-js/wasm-graphviz');
// Read from disk: @hpcc-js/wasm-graphviz does not export its package.json.
const installed = (pkg) =>
  JSON.parse(readFileSync(join(import.meta.dirname, 'node_modules', pkg, 'package.json'), 'utf8')).version;
const versions = { mermaid: installed('mermaid'), graphviz: installed('@hpcc-js/wasm-graphviz') };

const dirs = process.argv.slice(2);
if (dirs.length === 0) {
  console.error('usage: node validate.mjs <dir>...');
  process.exit(2);
}

function walk(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    return statSync(path).isDirectory() ? walk(path) : [path];
  });
}

const files = dirs.flatMap(walk).filter((f) => f.endsWith('.mmd') || f.endsWith('.dot')).sort();
mermaid.initialize({ startOnLoad: false });
const graphviz = await Graphviz.load();

const failures = [];
const kinds = new Map();
let mmd = 0;
let dot = 0;
const drawn = { mmd: 0, dot: 0 };
for (const file of files) {
  const text = readFileSync(file, 'utf8');
  const name = relative(process.cwd(), file);
  try {
    if (file.endsWith('.mmd')) {
      const result = await mermaid.parse(text);
      kinds.set(result.diagramType, (kinds.get(result.diagramType) ?? 0) + 1);
      mmd++;
    } else {
      // dot() throws on a syntax error; layout to SVG is the same path "dot -Tsvg" takes.
      graphviz.dot(text, 'svg');
      dot++;
    }
    const expectFile = `${file}.expect`;
    if (existsSync(expectFile)) {
      const isMermaid = file.endsWith('.mmd');
      const seen = isMermaid ? await mermaidLabels(mermaid, text) : dotLabels(graphviz, text);
      const expected = readFileSync(expectFile, 'utf8').split('\n').filter((l) => l.length > 0);
      const missing = expected.filter((l) => !seen.includes(l.replace(/\s+/g, ' ').trim()));
      if (missing.length > 0) {
        throw new Error(`drawn without the expected label(s):\n${missing.map((m) => `  want ${JSON.stringify(m)}`).join('\n')}`
          + `\ndrawn labels:\n${seen.map((s) => `  ${JSON.stringify(s)}`).join('\n')}`);
      }
      drawn[isMermaid ? 'mmd' : 'dot']++;
    }
    console.log(`ok    ${name}`);
  } catch (e) {
    const message = String(e?.message ?? e).trim();
    failures.push(name);
    console.log(`FAIL  ${name}\n      ${message.replace(/\n/g, '\n      ')}`);
  }
}

// The names mermaid.parse reports for classDiagram, sequenceDiagram, stateDiagram-v2 and flowchart.
const expectedKinds = ['classDiagram', 'sequence', 'stateDiagram', 'flowchart-v2'];
const missing = expectedKinds.filter((k) => !kinds.has(k));
console.log(`\nmermaid ${versions.mermaid}: ${mmd} parsed; @hpcc-js/wasm-graphviz ${versions.graphviz} (Graphviz ${graphviz.version()}): ${dot} parsed`);
console.log(`labels checked by drawing: ${drawn.mmd} Mermaid, ${drawn.dot} DOT`);
console.log(`mermaid kinds: ${[...kinds].map(([k, n]) => `${k}=${n}`).join(', ') || 'none'}`);

let status = 0;
// docs/CLI.md tells users which Mermaid the output is checked against; a Dependabot bump must
// update it, or the documented version silently goes stale.
const cliDoc = readFileSync(join(import.meta.dirname, '..', '..', 'docs', 'CLI.md'), 'utf8');
if (!cliDoc.includes(`Mermaid ${versions.mermaid} `)) {
  console.error(`docs/CLI.md does not name the Mermaid version checked against, ${versions.mermaid}`);
  status = 1;
}
if (failures.length > 0) {
  console.error(`${failures.length} file(s) rejected: ${failures.join(', ')}`);
  status = 1;
}
if (mmd === 0 || dot === 0) {
  console.error(`expected .mmd and .dot files under ${dirs.join(', ')}; found ${mmd} and ${dot}`);
  status = 1;
}
if (drawn.mmd === 0 || drawn.dot === 0) {
  console.error(`expected .expect files for both formats; drew ${drawn.mmd} Mermaid and ${drawn.dot} DOT`);
  status = 1;
}
if (missing.length > 0) {
  console.error(`no accepted Mermaid diagram of kind: ${missing.join(', ')}`);
  status = 1;
}
process.exit(status);
