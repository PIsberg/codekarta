// Draws Mermaid and DOT text and returns the text of every label in the result, one string per
// drawn label, whitespace collapsed. validate.mjs compares that against the labels a sample's
// .expect file lists (#305): parsing proves a file is accepted, only drawing shows what a reader
// sees, and "run() : : void" parses fine.
//
// Mermaid draws in a browser. Here it draws in jsdom, which has no layout engine, so text
// measurement is stubbed with a width proportional to the text: positions are nonsense, labels
// are what Mermaid really writes. If a Mermaid upgrade needs a DOM API jsdom lacks, render() throws
// and the check fails with that error, which then is the harness, not code-karta's output.
import { JSDOM } from 'jsdom';

const dom = new JSDOM('<!doctype html><html><body></body></html>', { pretendToBeVisual: true });
const { window } = dom;
for (const name of ['window', 'document', 'navigator', 'Element', 'HTMLElement', 'HTMLDivElement',
  'SVGElement', 'SVGSVGElement', 'Node', 'Text', 'DocumentFragment', 'DOMParser', 'XMLSerializer',
  'MutationObserver', 'CSSStyleSheet', 'getComputedStyle']) {
  if (!(name in globalThis)) {
    Object.defineProperty(globalThis, name, { value: name === 'window' ? window : window[name], configurable: true, writable: true });
  }
}
const width = (node) => 8 * (node.textContent ?? '').length + 1;
window.SVGElement.prototype.getBBox = function () { return { x: 0, y: 0, width: width(this), height: 16 }; };
window.SVGElement.prototype.getComputedTextLength = function () { return width(this) - 1; };
window.Element.prototype.getBoundingClientRect = function () {
  const w = width(this);
  return { x: 0, y: 0, width: w, height: 16, top: 0, left: 0, right: w, bottom: 16 };
};

const collapse = (s) => s.replace(/\s+/g, ' ').trim();

/** The text of each drawn label in an SVG string: every <text> and HTML label <foreignObject>. */
function labels(svg) {
  const host = window.document.createElement('div');
  // Parsed as SVG/XML by DOMParser, never inserted into a live page: this is our own output in jsdom.
  const doc = new window.DOMParser().parseFromString(svg, 'image/svg+xml');
  host.appendChild(window.document.importNode(doc.documentElement, true));
  return [...host.querySelectorAll('text, foreignObject')]
    .map((e) => collapse(e.textContent ?? ''))
    .filter((t) => t.length > 0);
}

let counter = 0;

export async function mermaidLabels(mermaid, text) {
  const { svg } = await mermaid.render(`karta${counter++}`, text);
  return labels(svg);
}

export function dotLabels(graphviz, text) {
  return labels(graphviz.dot(text, 'svg'));
}
