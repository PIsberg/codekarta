package se.deversity.codekarta.render;

import org.htmlunit.BrowserVersion;
import org.htmlunit.WebClient;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlCheckBoxInput;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The viewer page driven in a headless browser, opened from a file:// URL as a user would open
 * it (issue #291). HtmlViewerRendererTest pins the page's data and offline properties; this pins
 * what its script does with them: switching diagrams, search, the edge toggles, deep links, and
 * above all the sanitizer every SVG passes through before it is shown.
 */
class HtmlViewerBrowserTest {

    private static final String SVG_NS = "xmlns=\"http://www.w3.org/2000/svg\"";

    private static final String FIRST = "<svg " + SVG_NS + " width=\"400\" height=\"200\">"
            + "<g id=\"Alpha\"><rect class=\"node-rect\" width=\"10\" height=\"10\"/><text>Alpha</text></g>"
            + "<g id=\"Beta\"><rect class=\"node-rect\" width=\"10\" height=\"10\"/><text>Beta</text></g>"
            + "<path class=\"edge-line\" data-source=\"Alpha\" data-target=\"Beta\" data-type=\"HAS\" d=\"M0 0 L1 1\"/>"
            + "<path class=\"edge-line\" data-source=\"Beta\" data-target=\"Alpha\" data-type=\"EXTENDS\" d=\"M1 1 L0 0\"/>"
            + "</svg>";

    private static final String SECOND = "<svg " + SVG_NS + " width=\"400\" height=\"200\">"
            + "<g id=\"Gamma\"><rect class=\"node-rect\" width=\"10\" height=\"10\"/><text>Gamma</text></g></svg>";

    private static final String HOSTILE = "<svg " + SVG_NS + " xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"400\" height=\"200\">"
            + "<script>alert('script')</script>"
            + "<g id=\"Safe\"><rect class=\"node-rect\" onclick=\"alert('click')\" onmouseover=\"alert('over')\" width=\"10\" height=\"10\"/></g>"
            + "<image href=\"missing.png\" onerror=\"alert('image')\"/>"
            + "<a href=\"javascript:alert('href')\"><text>a</text></a>"
            + "<a xlink:href=\" JaVaScRiPt:alert('xlink')\"><text>b</text></a>"
            + "<a href=\"https://example.com/\"><text>c</text></a>"
            + "<foreignObject width=\"10\" height=\"10\"><div xmlns=\"http://www.w3.org/1999/xhtml\" onclick=\"alert('fo')\">d</div></foreignObject>"
            + "<a id=\"target\" href=\"#Safe\"><text>e</text><set attributeName=\"href\" to=\"javascript:alert('set')\" begin=\"0s\"/></a>"
            + "</svg>";

    private final HtmlViewerRenderer renderer = new HtmlViewerRenderer();

    @Test
    void theFirstDiagramIsShownAndEveryDiagramIsListed(@TempDir Path dir) throws Exception {
        try (Browser b = open(dir, "", diagram("one", FIRST), diagram("two", SECOND))) {
            assertEquals(2, b.count("#list button"));
            assertTrue(b.exists("#canvas svg g#Alpha"), "the first diagram is drawn on load");
            assertFalse(b.exists("#canvas svg g#Gamma"));
            assertEquals("one - code-karta", b.page.getTitleText());
        }
    }

    @Test
    void choosingADiagramShowsItAndRecordsItInTheAddress(@TempDir Path dir) throws Exception {
        try (Browser b = open(dir, "", diagram("one", FIRST), diagram("two", SECOND))) {
            ((DomElement) b.page.querySelectorAll("#list button").get(1)).click();

            assertTrue(b.exists("#canvas svg g#Gamma"), "the chosen diagram replaces the first");
            assertFalse(b.exists("#canvas svg g#Alpha"));
            assertTrue(b.page.getUrl().toString().endsWith("#two"), b.page.getUrl().toString());
        }
    }

    @Test
    void aDeepLinkOpensThatDiagram(@TempDir Path dir) throws Exception {
        try (Browser b = open(dir, "#two", diagram("one", FIRST), diagram("two", SECOND))) {
            assertTrue(b.exists("#canvas svg g#Gamma"), "the address names the diagram to open");
        }
    }

    @Test
    void searchDimsEverythingThatDoesNotMatch(@TempDir Path dir) throws Exception {
        try (Browser b = open(dir, "", diagram("one", FIRST))) {
            b.js("{ const s = document.getElementById('search'); s.value = 'alp';"
                    + " s.dispatchEvent(new Event('input')); }");

            assertEquals("1", b.js("document.querySelector('#canvas g#Alpha').style.opacity"));
            assertEquals("0.15", b.js("document.querySelector('#canvas g#Beta').style.opacity"));
            assertEquals("1", b.js("document.querySelector('#canvas [data-type=HAS]').style.opacity"),
                    "an edge touching a match stays visible");

            b.js("{ const s = document.getElementById('search'); s.value = '';"
                    + " s.dispatchEvent(new Event('input')); }");
            assertEquals("", b.js("document.querySelector('#canvas g#Beta').style.opacity"), "clearing restores everything");
        }
    }

    @Test
    void anEdgeTypeToggleHidesExactlyThatType(@TempDir Path dir) throws Exception {
        try (Browser b = open(dir, "", diagram("one", FIRST))) {
            List<HtmlCheckBoxInput> boxes = new ArrayList<>();
            b.page.querySelectorAll("#toggles input").forEach(n -> boxes.add((HtmlCheckBoxInput) n));
            assertEquals(2, boxes.size(), "one toggle per edge type in the diagram");
            assertEquals("extends", boxes.get(0).getParentNode().getTextContent().trim());

            boxes.get(1).click(); // has

            assertEquals("none", b.js("document.querySelector('#canvas [data-type=HAS]').style.display"));
            assertEquals("", b.js("document.querySelector('#canvas [data-type=EXTENDS]').style.display"));
        }
    }

    @Test
    void anEmptyViewerSaysSo(@TempDir Path dir) throws Exception {
        try (Browser b = open(dir, "")) {
            assertTrue(b.page.getElementById("empty").getTextContent().contains("No diagrams"));
        }
    }

    /**
     * The viewer lists every .svg in the output directory, whoever wrote it. Nothing in such a file
     * may run or survive into the page: scripts, handlers, javascript: links in either href form,
     * foreignObject, and animation that would rewrite a link after sanitizing.
     */
    @Test
    void aHostileSvgIsDefusedBeforeItIsShown(@TempDir Path dir) throws Exception {
        try (Browser b = open(dir, "", diagram("hostile", HOSTILE))) {
            DomElement canvas = b.page.getElementById("canvas");
            String markup = canvas.asXml();

            assertTrue(b.exists("#canvas svg g#Safe rect"), "drawing elements survive");
            assertEquals(0, b.count("#canvas script"));
            assertEquals(0, b.count("#canvas image"));
            assertEquals(0, b.count("#canvas foreignObject, #canvas foreignobject"));
            assertEquals(0, b.count("#canvas set"));
            assertFalse(markup.toLowerCase(java.util.Locale.ROOT).contains("javascript:"), markup);
            assertFalse(markup.contains("onclick") || markup.contains("onmouseover") || markup.contains("onerror"), markup);
            assertTrue(markup.contains("https://example.com/"), "an http(s) link on <a> is kept");
            assertTrue(markup.contains("#Safe"), "a same-document reference is kept");

            // Dispatched from script: HtmlUnit cannot compute mouse coordinates for an SVG target,
            // and an inline handler fires on the event either way.
            b.js("document.querySelectorAll('#canvas a, #canvas rect').forEach(el => {"
                    + " el.dispatchEvent(new Event('click', { bubbles: true }));"
                    + " el.dispatchEvent(new Event('mouseover', { bubbles: true })); })");
            b.client.waitForBackgroundJavaScript(500);
            assertEquals(List.of(), b.alerts, "nothing from the diagram ran");
        }
    }

    // ------------------------------------------------------------------ harness

    private static HtmlViewerRenderer.Diagram diagram(String title, String svg) {
        return new HtmlViewerRenderer.Diagram(title, svg);
    }

    private Browser open(Path dir, String hash, HtmlViewerRenderer.Diagram... diagrams) throws Exception {
        Path page = Files.writeString(dir.resolve("viewer.html"), renderer.render(List.of(diagrams)));
        return new Browser(page, hash);
    }

    /** A headless Chrome-flavoured browser on one viewer page, collecting every alert. */
    private static final class Browser implements AutoCloseable {
        final WebClient client = new WebClient(BrowserVersion.CHROME);
        final List<String> alerts = new ArrayList<>();
        final HtmlPage page;

        Browser(Path file, String hash) throws Exception {
            client.getOptions().setThrowExceptionOnScriptError(true);
            client.getOptions().setCssEnabled(true);
            client.setAlertHandler((p, message) -> alerts.add(message));
            page = client.getPage(file.toUri().toString() + hash);
            client.waitForBackgroundJavaScript(500);
        }

        boolean exists(String selector) {
            return page.querySelector(selector) != null;
        }

        int count(String selector) {
            return page.querySelectorAll(selector).size();
        }

        String js(String script) {
            Object result = page.executeJavaScript(script).getJavaScriptResult();
            return result == null ? null : String.valueOf(result);
        }

        @Override
        public void close() {
            client.close();
        }
    }
}
