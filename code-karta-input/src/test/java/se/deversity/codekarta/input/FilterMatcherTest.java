package se.deversity.codekarta.input;

import org.junit.jupiter.api.Test;
import se.deversity.codekarta.input.parser.FilterMatcher;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterMatcherTest {

    @Test
    void theDocumentedShapesKeepWorking() {
        assertTrue(FilterMatcher.matches("OrderTest", "*Test"));
        assertTrue(FilterMatcher.matches("OrderService", "Order*"));
        assertTrue(FilterMatcher.matches("BigOrderThing", "*Order*"));
        assertTrue(FilterMatcher.matches("java.util.Map", "Map"), "a bare name matches the last segment");
        assertFalse(FilterMatcher.matches("java.util.HashMap", "Map"));
        assertTrue(FilterMatcher.matches("anything", "*"));
    }

    /** Internal wildcards fell through to the exact-equality branch and never matched (#266). */
    @Test
    void aWildcardInsideThePatternMatches() {
        assertTrue(FilterMatcher.matches("OrderPaymentService", "Order*Service"));
        assertFalse(FilterMatcher.matches("OrderPaymentServiceImpl", "Order*Service"));
        assertTrue(FilterMatcher.matches("com.example.billing.internal.Ledger", "com.example.*.internal.*"));
        assertFalse(FilterMatcher.matches("com.example.billing.api.Ledger", "com.example.*.internal.*"));
    }

    @Test
    void aPackagePatternMatchesQualifiedNames() {
        assertTrue(FilterMatcher.matches("se.deversity.codekarta.util.Helper", "se.deversity.codekarta.util.*"));
        assertFalse(FilterMatcher.matches("se.deversity.codekarta.app.Helper", "se.deversity.codekarta.util.*"));
    }

    @Test
    void aDotlessWildcardPatternAlsoMatchesTheSimpleNameOfAQualifiedOne() {
        assertTrue(FilterMatcher.matches("com.example.OrderPaymentService", "Order*Service"));
        assertTrue(FilterMatcher.matches("com.example.OrderService", "Order*"));
    }

    /** Patterns are user input: many stars against a long name must not backtrack exponentially. */
    @Test
    void manyWildcardsAgainstALongNameFinishQuickly() {
        String name = "a".repeat(20_000);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                assertFalse(FilterMatcher.matches(name, "*a*a*a*a*a*a*a*a*a*a*a*b")));
    }
}
