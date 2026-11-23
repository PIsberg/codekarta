# Interactive Diagram Viewer

A static SVG stops being enough once a diagram is large or there are many of them, as with
`--split-packages`. `--viewer` writes one self-contained HTML page next to the diagrams that
browses all of them. Open it in any modern browser: no server, no build step, no network.

```bash
# draw, then index everything under --output
java -jar code-karta-cli-all.jar --input src/main/java --output out --split-packages --viewer

# rebuild only the viewer, from whatever earlier runs left in --output
java -jar code-karta-cli-all.jar --output out --viewer
```

With the Maven plugin, set `<viewer>true</viewer>` (or `-Dkarta.viewer=true`); see
[`MAVEN-PLUGIN.md`](MAVEN-PLUGIN.md).

The page, `viewer.html` in the output directory, offers:

- **A list of every diagram**, by its path under the output directory, with a filter box.
- **Search** (press `/`): nodes whose name or text matches stay visible, everything else dims to
  15% and unrelated edges to 8%, so a dependency chain reads out of a dense graph.
- **A toggle per edge type** found in the current diagram, to isolate one structural concern.
- **Zoom and pan**: mouse wheel or the buttons, drag to pan, "Fit" to start over.
- **A dark theme**, remembered between visits where the browser allows it.
- **Deep links**: the address ends in `#<diagram>`, so a link opens that diagram.

## How it is built

Every SVG is embedded in the page as data and put into the document when it is chosen. The
earlier hand-written viewer loaded each SVG as a separate file and read into it, which browsers
block for pages opened from disk, so search and the toggles stopped working unless the page was
served over HTTP. It also listed six fixed diagrams and fetched its fonts from Google; this one
lists whatever is in the directory and uses the system's fonts.

Before a diagram is shown it is parsed as SVG and stripped of scripts, `foreignObject`
elements, event-handler attributes and `javascript:` links. The diagrams code-karta renders
carry none of these, but the viewer lists every `.svg` it finds, whoever wrote it. It reads only
regular files inside the output directory and does not follow links.

The page depends only on the diagrams, with no timestamp, so regenerating it over unchanged
diagrams gives the same bytes. [`docs/diagrams/viewer.html`](diagrams/viewer.html) is generated
this way as the last step of the diagram regeneration in `mvn verify`; see
[`BUILD.md`](BUILD.md#diagram-regeneration).
