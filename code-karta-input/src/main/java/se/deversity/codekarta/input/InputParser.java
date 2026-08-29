package se.deversity.codekarta.input;

import se.deversity.codekarta.core.model.Graph;
import se.deversity.vibetags.annotations.AIContract;
import se.deversity.vibetags.annotations.AIExtensible;

import java.nio.file.Path;

/**
 * Turns a path of Java source into a {@link Graph}. The first of the three pipeline tiers.
 *
 * <p><strong>{@code parse} never throws.</strong> Every implementation wraps its work in a
 * try/catch, logs a warning and returns whatever it managed to build, which may be an empty
 * graph. That keeps the CLI producing output on a source tree it only partly understands, and it
 * is what makes this interface safe to call over a directory of unknown code.
 *
 * <p>It also means <strong>an empty graph is ambiguous</strong>. A caller cannot tell "this
 * directory contains no types" from "every file failed to parse" without reading the log. Code
 * that needs to know, such as a build step that should fail on a broken source tree, must check
 * the log or assert on the graph it expected.
 *
 * <p>Implementations are not required to be thread-safe and none of the bundled ones are.
 *
 * @see JavaSourceInputParser
 */
@AIContract(reason = "parse(Path) must never throw — all parsers wrap JavaParser calls in try/catch, log a warning, and return a partial (possibly empty) Graph. The fault-tolerance contract ensures the pipeline always produces some output.")
@AIExtensible(AIExtensible.Strategy.STRATEGY_PATTERN)
@FunctionalInterface
public interface InputParser {

    /**
     * Parses the given path and returns the graph it describes.
     *
     * <p>Never throws, including for a path that does not exist, is unreadable, or contains
     * source that does not compile. Failures are logged and a partial or empty graph is returned.
     *
     * @param path a Java source file or a directory of them; what an implementation accepts is
     *             its own business, and one that is handed something it cannot use returns an
     *             empty graph rather than failing
     * @return the parsed graph, never {@code null}, possibly empty
     */
    Graph parse(Path path);
}
