package se.deversity.codekarta.input.parser;

import se.deversity.vibetags.annotations.AIPure;

import java.util.Set;

public final class FilterMatcher {

    /**
     * Checks if the given name matches any pattern in the patterns set.
     */
    @AIPure(reason = "Called once per node/edge candidate during parsing — deterministic string matching with no side effects; callers assume referential transparency")
    public static boolean matchesAny(String name, Set<String> patterns) {
        if (name == null || patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (String pattern : patterns) {
            if (matches(name, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a name matches a single pattern.
     *
     * <p>A pattern without {@code *} matches the whole name or its last dot-separated segments,
     * so {@code Map} matches {@code java.util.Map}. A pattern with {@code *} is a glob over the
     * whole name, each {@code *} standing for any run of characters, dots included:
     * {@code se.example.util.*}, {@code Order*Service}, {@code com.example.*.internal.*}. A glob
     * with no dot in it is also tried against the simple name, so {@code Order*} matches
     * {@code com.example.OrderService}. Only leading and trailing stars used to work; any other
     * star fell through to exact equality and never matched (issue #266).
     */
    @AIPure(reason = "Deterministic wildcard matching with no side effects")
    public static boolean matches(String name, String rawPattern) {
        if (name == null || rawPattern == null) {
            return false;
        }
        String pattern = rawPattern.trim();
        if (pattern.isEmpty()) {
            return false;
        }
        if (pattern.indexOf('*') < 0) {
            return name.equals(pattern) || name.endsWith("." + pattern);
        }
        if (glob(name, pattern)) {
            return true;
        }
        int dot = name.lastIndexOf('.');
        return pattern.indexOf('.') < 0 && dot >= 0 && glob(name.substring(dot + 1), pattern);
    }

    /**
     * Glob match with {@code *} as the only wildcard, by the greedy two-pointer method: on a
     * mismatch it resumes just after the most recent star, never further back, so the work is
     * bounded by name length times pattern length. A regex built from user input could
     * backtrack exponentially instead.
     */
    private static boolean glob(String name, String pattern) {
        int n = 0;
        int p = 0;
        int starP = -1;
        int starN = 0;
        while (n < name.length()) {
            if (p < pattern.length() && pattern.charAt(p) != '*' && pattern.charAt(p) == name.charAt(n)) {
                n++;
                p++;
            } else if (p < pattern.length() && pattern.charAt(p) == '*') {
                starP = p++;
                starN = n;
            } else if (starP >= 0) {
                p = starP + 1;
                n = ++starN;
            } else {
                return false;
            }
        }
        while (p < pattern.length() && pattern.charAt(p) == '*') {
            p++;
        }
        return p == pattern.length();
    }

    private FilterMatcher() {}
}
