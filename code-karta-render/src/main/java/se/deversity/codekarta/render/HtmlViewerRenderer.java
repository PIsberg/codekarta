package se.deversity.codekarta.render;

import se.deversity.vibetags.annotations.AIArchitecture;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One self-contained HTML page that browses a set of rendered SVG diagrams: a list to pick from,
 * search that highlights matching nodes, a toggle per edge type, zoom and pan, and a dark theme.
 *
 * <p>Every SVG is carried inside the page as a JavaScript string and put into the document when
 * its entry is chosen. Loading sibling files instead (an object element and its contentDocument)
 * fails over file://, where browsers treat each file as its own origin, so search and the
 * toggles stopped working unless the page was served (issue #274). Nothing is fetched from the
 * network either: the fonts are the system's own.
 *
 * <p>The output depends on the diagrams alone, with no timestamp, so a committed viewer
 * regenerates byte-identical.
 */
@AIArchitecture(belongsTo = "render", cannotReference = {"input", "layout", "cli"})
public final class HtmlViewerRenderer {

    /** A diagram to list: the name shown for it, and its SVG markup as rendered. */
    public record Diagram(String title, String svg) {
        public Diagram {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(svg, "svg");
        }
    }

    private static final String TEMPLATE = "viewer.html";
    private static final String PLACEHOLDER = "/*@DIAGRAMS@*/[]";
    private static final String CSP_PLACEHOLDER = "@@CSP@@";

    /** The page listing {@code diagrams} in the order given. */
    public String render(List<Diagram> diagrams) {
        StringBuilder data = new StringBuilder("[");
        for (int i = 0; i < diagrams.size(); i++) {
            Diagram diagram = diagrams.get(i);
            data.append(i == 0 ? "\n" : ",\n")
                .append("{\"title\":").append(jsString(diagram.title()))
                .append(",\"svg\":").append(jsString(withoutProlog(diagram.svg())))
                .append('}');
        }
        data.append(diagrams.isEmpty() ? "]" : "\n]");
        String page = template().replace(PLACEHOLDER, data.toString());
        // Only the first occurrence, the meta tag in <head>, which comes before the data. A diagram
        // whose text happened to contain the placeholder would otherwise be rewritten after its
        // script was hashed, and the browser would refuse to run the viewer at all.
        int at = page.indexOf(CSP_PLACEHOLDER);
        return page.substring(0, at) + contentSecurityPolicy(page) + page.substring(at + CSP_PLACEHOLDER.length());
    }

    /**
     * The page's policy: nothing may load from anywhere, and the one script that may run is the
     * viewer's own, named by its SHA-256. Inline event handlers and javascript: URLs are therefore
     * refused by the browser even if one survived the viewer's SVG sanitizer, and "works offline"
     * is enforced rather than merely true. Styles stay inline, which the SVGs need.
     */
    static String contentSecurityPolicy(String page) {
        int start = page.indexOf("<script>") + "<script>".length();
        int end = page.indexOf("</script>", start);
        String script = page.substring(start, end);
        String hash;
        try {
            hash = java.util.Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(script.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("every Java platform provides SHA-256", e);
        }
        return "default-src 'none'; script-src 'sha256-" + hash + "'; style-src 'unsafe-inline'; "
                + "img-src data:; base-uri 'none'; form-action 'none'";
    }

    /** Drops the XML declaration, which is not allowed inside an HTML document. */
    static String withoutProlog(String svg) {
        if (!svg.startsWith("<?xml")) {
            return svg;
        }
        int end = svg.indexOf("?>");
        return end < 0 ? svg : svg.substring(end + 2).stripLeading();
    }

    /**
     * A JavaScript string literal for {@code s}. The angle brackets and the ampersand are written
     * as unicode escapes, so no content can close the script element that holds it or start a
     * comment or CDATA section there; line and paragraph separators, which end a JavaScript
     * string, are escaped the same way.
     */
    static String jsString(String s) {
        StringBuilder out = new StringBuilder(s.length() + 16).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || c == '<' || c == '>' || c == '&' || c == 0x2028 || c == 0x2029) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private static String template() {
        try (InputStream in = HtmlViewerRenderer.class.getResourceAsStream(TEMPLATE)) {
            if (in == null) {
                throw new IllegalStateException(TEMPLATE + " is missing from the code-karta-render jar");
            }
            // Normalised so a checkout with CRLF line endings renders the same bytes as one with LF.
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
