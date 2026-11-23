package se.deversity.codekarta.render;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The viewer page (issue #274). The hand-written viewer loaded each SVG through an object element
 * and read its contentDocument, which browsers block over file://, listed six fixed diagrams, and
 * fetched its fonts from Google. This one must work opened straight from disk, offline.
 */
class HtmlViewerRendererTest {

    private static final String SVG = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<svg xmlns=\"http://www.w3.org/2000/svg\"><g id=\"A\"><rect class=\"node-rect\"/></g>"
            + "<script>\n<![CDATA[ if (a < b && c > d) {} ]]>\n</script></svg>\n";

    private final HtmlViewerRenderer renderer = new HtmlViewerRenderer();

    @Test
    void everyDiagramIsEmbeddedInOrderWithItsTitle() {
        String html = renderer.render(List.of(
                new HtmlViewerRenderer.Diagram("module-diagram", SVG),
                new HtmlViewerRenderer.Diagram("com/example/alpha/class-diagram", SVG)));

        int first = html.indexOf("\"module-diagram\"");
        int second = html.indexOf("\"com/example/alpha/class-diagram\"");
        assertTrue(first > 0 && second > first, "titles appear in the order given");
        assertTrue(html.startsWith("<!DOCTYPE html>"));
    }

    /**
     * The SVGs are data inside a script element. A raw closing script tag in one (every SVG
     * carries its own hover script) would end the viewer's script early, so markup characters
     * are written as JavaScript escapes, and the XML prolog, which is not allowed inline, goes.
     */
    @Test
    void embeddedSvgCannotBreakOutOfTheScriptThatHoldsIt() {
        String html = renderer.render(List.of(new HtmlViewerRenderer.Diagram("d", SVG)));

        assertEquals(count(html, "<script"), count(html, "</script>"), "only the viewer's own script tags are raw");
        assertTrue(html.contains("\\u003csvg xmlns="), "the SVG is carried as escaped data");
        assertFalse(html.contains("<?xml"), "the XML prolog is dropped");
        assertFalse(html.contains("<![CDATA["), "no raw CDATA marker survives in the page");
    }

    @Test
    void theEmbeddedDataDecodesBackToTheSvg() {
        String html = renderer.render(List.of(new HtmlViewerRenderer.Diagram("d", SVG)));

        Matcher m = Pattern.compile("\\{\"title\":\"d\",\"svg\":\"((?:[^\"\\\\]|\\\\.)*)\"\\}").matcher(html);
        assertTrue(m.find(), "one data entry per diagram");
        assertEquals(SVG.substring(SVG.indexOf("<svg")), unescape(m.group(1)));
    }

    @Test
    void thePageFetchesNothingFromTheNetwork() {
        String html = renderer.render(List.of(new HtmlViewerRenderer.Diagram("d", SVG)));

        assertFalse(html.contains("googleapis"), "no web fonts");
        assertFalse(html.contains("<link"), "no external stylesheets");
        assertFalse(html.contains("@import"));
        assertFalse(Pattern.compile("src=\"https?:").matcher(html).find(), "no external scripts or images");
        assertFalse(html.contains("<object"), "SVGs are inlined, not loaded through object elements");
    }

    @Test
    void titlesAreDataNotMarkup() {
        String html = renderer.render(List.of(new HtmlViewerRenderer.Diagram("a</script><b>\"x", SVG)));

        assertEquals(count(html, "<script"), count(html, "</script>"));
        assertTrue(html.contains("a\\u003c/script\\u003e\\u003cb\\u003e\\\"x"));
    }

    @Test
    void anSvgWithoutAPrologOrWithABrokenOneIsKeptWhole() {
        String plain = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>";
        assertEquals(plain, HtmlViewerRenderer.withoutProlog(plain));
        String unterminated = "<?xml version=\"1.0\" <svg/>";
        assertEquals(unterminated, HtmlViewerRenderer.withoutProlog(unterminated));
        assertEquals("<svg/>", HtmlViewerRenderer.withoutProlog("<?xml version=\"1.0\"?>\n\n<svg/>"));
    }

    @Test
    void theSameDiagramsGiveTheSameBytes() {
        List<HtmlViewerRenderer.Diagram> diagrams = List.of(new HtmlViewerRenderer.Diagram("d", SVG));
        assertEquals(renderer.render(diagrams), renderer.render(diagrams), "committed viewers must regenerate without a diff");
    }

    @Test
    void anEmptyListStillGivesAWorkingPage() {
        String html = renderer.render(List.of());
        assertTrue(html.contains("const DIAGRAMS = [];"));
        assertTrue(html.endsWith("</html>\n"));
    }

    private static int count(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }

    /** Reverses the JavaScript string escaping the renderer applies. */
    private static String unescape(String js) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < js.length(); i++) {
            char c = js.charAt(i);
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char next = js.charAt(++i);
            switch (next) {
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    out.append((char) Integer.parseInt(js.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                default -> out.append(next);
            }
        }
        return out.toString();
    }
}
