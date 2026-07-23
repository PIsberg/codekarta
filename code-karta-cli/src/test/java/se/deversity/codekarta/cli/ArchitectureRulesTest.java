package se.deversity.codekarta.cli;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Architecture fitness functions for the code-karta 3-tier pipeline.
 *
 * <p>These tests encode the structural invariants documented in CLAUDE.md and
 * docs/ARCHITECTURE.md that were previously upheld only by convention:
 *
 * <ul>
 *   <li>Tiers communicate only through the Core IR ({@code se.deversity.codekarta.core.model}) —
 *       no tier may import from a tier beside it (input ↔ layout ↔ render).</li>
 *   <li>{@code code-karta-core} is a pure data model: it depends on nothing but the
 *       JDK and Jackson annotations.</li>
 *   <li>Only the CLI wires the three tiers together.</li>
 *   <li>The package hierarchy is cycle-free.</li>
 * </ul>
 *
 * <p>This test lives in {@code code-karta-cli} because it is the only module with
 * all tiers on its classpath.
 */
@AnalyzeClasses(
        packages = "se.deversity.codekarta",
        importOptions = ImportOption.DoNotIncludeTests.class
)
public class ArchitectureRulesTest {

    /** Tier 1 must not know about layout or rendering. */
    @ArchTest
    static final ArchRule INPUT_DOES_NOT_DEPEND_ON_SIBLING_TIERS =
            noClasses()
                    .that().resideInAPackage("se.deversity.codekarta.input..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("se.deversity.codekarta.layout..", "se.deversity.codekarta.render..", "se.deversity.codekarta.cli..")
                    .as("code-karta-input must depend only on the Core IR, never on sibling tiers or the CLI");

    /** Tier 2 must not know about parsing or rendering. */
    @ArchTest
    static final ArchRule LAYOUT_DOES_NOT_DEPEND_ON_SIBLING_TIERS =
            noClasses()
                    .that().resideInAPackage("se.deversity.codekarta.layout..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("se.deversity.codekarta.input..", "se.deversity.codekarta.render..", "se.deversity.codekarta.cli..")
                    .as("code-karta-layout must depend only on the Core IR, never on sibling tiers or the CLI");

    /** Tier 3 must not know about parsing or layout. */
    @ArchTest
    static final ArchRule RENDER_DOES_NOT_DEPEND_ON_SIBLING_TIERS =
            noClasses()
                    .that().resideInAPackage("se.deversity.codekarta.render..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("se.deversity.codekarta.input..", "se.deversity.codekarta.layout..", "se.deversity.codekarta.cli..")
                    .as("code-karta-render must depend only on the Core IR, never on sibling tiers or the CLI");

    /** The Core IR is a pure data model — no logic, no dependencies on any tier. */
    @ArchTest
    static final ArchRule CORE_DEPENDS_ON_NOTHING_ABOVE_IT =
            classes()
                    .that().resideInAPackage("se.deversity.codekarta.core..")
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage("se.deversity.codekarta.core..", "java..", "com.fasterxml.jackson..",
                            "org.jspecify..", "se.deversity.vibetags.annotations..")
                    .as("code-karta-core is a pure data model: JDK plus source-level annotations "
                            + "(Jackson, JSpecify, vibetags) only");

    /** The whole se.deversity.codekarta hierarchy must stay free of package cycles. */
    @ArchTest
    static final ArchRule NO_PACKAGE_CYCLES =
            slices()
                    .matching("se.deversity.codekarta.(**)")
                    .should().beFreeOfCycles()
                    .as("se.deversity.codekarta packages must be free of circular dependencies");
}
