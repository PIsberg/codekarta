package se.deversity.codekarta.cli;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RunOptions.builder() (issue #278): a ten-component record had grown two deprecated constructors,
 * and every new flag would have added another. The builder is the construction API that a new
 * option extends without breaking anyone.
 */
class RunOptionsBuilderTest {

    @Test
    void anEmptyBuilderGivesTheDefaults() {
        assertEquals(RunOptions.defaults(), RunOptions.builder().build());
    }

    @Test
    void everySetterReachesItsComponent() {
        RunOptions options = RunOptions.builder()
                .sequenceOnly(true)
                .layout("ELK")
                .stateMachine(true)
                .customExcludes(Set.of("*Test"))
                .maxDepth(3)
                .modulesOnly(true)
                .outputName("x.svg")
                .maxMembers(9)
                .format("json")
                .includeTests(true)
                .build();

        assertEquals(new RunOptions(true, "elk", true, Set.of("*Test"), 3, true, "x.svg", 9, "json", true), options);
    }

    @Test
    void buildValidatesLikeTheConstructor() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> RunOptions.builder().format("png").build());
        assertTrue(e.getMessage().contains("format"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> RunOptions.builder().layout("elkk").build());
    }

    @Test
    void toBuilderCopiesEverythingAndChangesOnlyWhatIsSet() {
        RunOptions original = RunOptions.builder().stateMachine(true).maxDepth(4).outputName("a.svg").build();

        RunOptions changed = original.toBuilder().layout(RunOptions.LAYOUT_ELK).build();

        assertEquals(RunOptions.LAYOUT_ELK, changed.layout());
        assertEquals(original, changed.toBuilder().layout(RunOptions.LAYOUT_SIMPLE).build(),
                "every other component survives the round trip");
    }

    @Test
    void theExcludesAreCopiedNotShared() {
        Set<String> patterns = new HashSet<>(Set.of("*Test"));
        RunOptions.Builder builder = RunOptions.builder().customExcludes(patterns);
        RunOptions options = builder.build();

        patterns.add("*Impl");

        assertEquals(Set.of("*Test"), options.customExcludes());
        assertEquals(Set.of("*Test"), builder.build().customExcludes(), "nor does the builder hold the caller's set");
    }
}
