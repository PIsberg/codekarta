# Interactive Diagram Viewer

Large or highly coupled class systems outgrow a static SVG. code-karta ships a self-contained
viewer at [`docs/diagrams/viewer.html`](diagrams/viewer.html) — open it in any modern browser, no
server and no build step.

- 🔍 **Real-time search and highlight** — highlights matching classes, methods, or packages and
  dims everything else to 15% opacity, so a dependency chain reads clearly out of a dense graph.
- ⚙️ **Relationship toggles** — show or hide individual edge types (method calls, composition,
  exceptions, inheritance) to isolate one structural concern at a time.
- 🔬 **Zoom and pan** — mouse wheel, touchpad, or the HUD buttons; click-and-drag to pan.
- 🔄 **Tab switcher** — all six generated diagrams in a single page.

The viewer reads the SVGs in `docs/diagrams/`, so it reflects whatever the last diagram
regeneration produced — see [`BUILD.md`](BUILD.md#diagram-regeneration).
