/**
 * The code-karta intermediate representation: the data model every tier speaks and the only thing
 * that crosses a tier boundary.
 *
 * <p>{@link se.deversity.codekarta.core.model.Graph} holds
 * {@link se.deversity.codekarta.core.model.Node}s,
 * {@link se.deversity.codekarta.core.model.Edge}s and
 * {@link se.deversity.codekarta.core.model.Group}s. It has no logic, no dependencies beyond
 * Jackson annotations and JSpecify, and no knowledge of parsing, layout or rendering. The input
 * tier produces a graph, the layout tier writes coordinates onto its nodes in place, and the
 * render tier turns it into SVG.
 *
 * <h2>Type constants are strings, and the strings are the contract</h2>
 *
 * <p>{@link se.deversity.codekarta.core.model.NodeType} and
 * {@link se.deversity.codekarta.core.model.EdgeType} are holders of {@code String} constants
 * rather than enums, because their values are matched by identity in the renderer's CSS
 * class-name map and in every parser. {@code NodeType.CLASS} is the string {@code "CLASS"}, and a
 * parser is free to write that string directly. Renaming a constant is therefore not a rename: it
 * is a coordinated change across the renderer, all parsers and the integration tests, and the
 * emitted SVG changes with it. Adding one is the same shape of change, because the renderer needs
 * a class-name mapping for it or the new type renders unstyled.
 *
 * <p>{@link se.deversity.codekarta.core.model.NodeDimensions} holds the default node width and
 * height. Both layout engines and the renderer read them, so changing a value moves every node in
 * every diagram and changes the bytes of every committed SVG.
 *
 * <h2>Nullness</h2>
 *
 * <p>The package is {@code @NullMarked}: references are non-null by default. The exceptions are
 * explicit. {@code Node.x}, {@code y}, {@code width} and {@code height} are {@code null} until a
 * layout engine has run, and stay {@code null} for a node the engine could not place;
 * {@code Edge.label} is {@code null} for edge types that carry no label.
 *
 * <h2>Threading</h2>
 *
 * <p>Nothing here is thread-safe. Build and consume a graph on one thread.
 */
@NullMarked
package se.deversity.codekarta.core.model;

import org.jspecify.annotations.NullMarked;
