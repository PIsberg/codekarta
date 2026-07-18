package com.karta.input.parser;

import java.util.Set;

public final class FilterMatcher {

    /**
     * Checks if the given name matches any pattern in the patterns set.
     */
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
     * Checks if a name matches a single pattern (supporting basic wildcards like *, suffix*, *prefix, *sub*).
     */
    public static boolean matches(String name, String rawPattern) {
        if (name == null || rawPattern == null) {
            return false;
        }
        String pattern = rawPattern.trim();
        if (pattern.isEmpty()) {
            return false;
        }
        if ("*".equals(pattern)) {
            return true;
        }
        boolean startWildcard = pattern.startsWith("*");
        boolean endWildcard = pattern.endsWith("*");

        if (startWildcard && endWildcard) {
            if (pattern.length() <= 2) return true;
            String mid = pattern.substring(1, pattern.length() - 1);
            return name.contains(mid);
        } else if (startWildcard) {
            String suffix = pattern.substring(1);
            return name.endsWith(suffix);
        } else if (endWildcard) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return name.startsWith(prefix);
        } else {
            return name.equals(pattern) || name.endsWith("." + pattern);
        }
    }

    private FilterMatcher() {}
}
