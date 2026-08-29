package se.deversity.codekarta.input;

import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.input.parser.CallSequenceParser;
import se.deversity.codekarta.input.parser.ExceptionFlowParser;
import se.deversity.codekarta.input.parser.ClassDiagramParser;
import se.deversity.codekarta.input.parser.ModuleInfoParser;
import se.deversity.vibetags.annotations.AIArchitecture;
import se.deversity.vibetags.annotations.AIContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.logging.Logger;

/**
 * The normal entry point to the input tier. Picks a parser from the shape of the path and
 * delegates to it.
 *
 * <table border="1">
 *   <caption>Dispatch</caption>
 *   <tr><th>Path</th><th>Parser</th><th>Produces</th></tr>
 *   <tr><td>{@code module-info.java}</td><td>{@code ModuleInfoParser}</td>
 *       <td>JPMS requires and exports</td></tr>
 *   <tr><td>a directory</td><td>{@code ClassDiagramParser}</td>
 *       <td>inheritance and composition, stdlib types filtered out</td></tr>
 *   <tr><td>a {@code .java} file</td><td>{@code ExceptionFlowParser}</td>
 *       <td>call graph plus exception routes and try/catch regions</td></tr>
 *   <tr><td>a {@code .java} file, {@code sequenceOnly}</td><td>{@code CallSequenceParser}</td>
 *       <td>the call graph without exception edges</td></tr>
 * </table>
 *
 * <p>Two modes are deliberately not reachable from here and are wired by the CLI instead:
 * multi-file stitched sequences ({@code MultiFileSequenceParser}) and state machines
 * ({@code StateMachineParser}). Call those directly.
 *
 * <p>Inherits the {@link InputParser} contract: {@link #parse} never throws, and an empty graph
 * can mean either "nothing to draw" or "nothing parsed".
 *
 * <p>Not thread-safe: the delegate parsers are created lazily and cached in fields.
 */
@AIContext(
    focus = "Dispatch logic is path-type-based (isDirectory, filename == 'module-info.java', .java extension). All parsers are lazily initialised. The sequenceOnly flag selects between CallSequenceParser (no exception edges) and ExceptionFlowParser (call graph + try/catch Groups).",
    avoids = "Adding new dispatch conditions without updating the InputParser contract javadoc and KartaCliTest expected output filenames."
)
@AIArchitecture(belongsTo = "input", cannotReference = {"layout", "render", "cli"})
public class JavaSourceInputParser implements InputParser {

    private static final Logger log = Logger.getLogger(JavaSourceInputParser.class.getName());

    private ModuleInfoParser moduleInfoParser;
    private ClassDiagramParser classDiagramParser;
    private ExceptionFlowParser exceptionFlowParser;
    private CallSequenceParser callSequenceParser;
    private final boolean sequenceOnly;
    private final Set<String> customExcludes;
    private final int maxMembers;

    /** Parses with exception flow included and no extra exclusions. The usual choice. */
    public JavaSourceInputParser() {
        this(false);
    }

    /**
     * @param sequenceOnly {@code true} to emit the call graph without exception edges or
     *                     try/catch regions. Only affects single-file input.
     */
    public JavaSourceInputParser(boolean sequenceOnly) {
        this(sequenceOnly, java.util.Collections.emptySet());
    }

    /**
     * @param sequenceOnly   see {@link #JavaSourceInputParser(boolean)}
     * @param customExcludes type names to leave out of a class diagram, on top of the built-in
     *                       stdlib skip list; {@code null} is treated as empty
     */
    public JavaSourceInputParser(boolean sequenceOnly, Set<String> customExcludes) {
        this(sequenceOnly, customExcludes, ClassDiagramParser.DEFAULT_MAX_MEMBERS);
    }

    /**
     * @param sequenceOnly   see {@link #JavaSourceInputParser(boolean)}
     * @param customExcludes see {@link #JavaSourceInputParser(boolean, Set)}
     * @param maxMembers     compartment lines kept per class before "…(+N more)";
     *                       {@link ClassDiagramParser#UNLIMITED_MEMBERS} keeps all of them
     */
    public JavaSourceInputParser(boolean sequenceOnly, Set<String> customExcludes, int maxMembers) {
        this.sequenceOnly = sequenceOnly;
        this.customExcludes = customExcludes != null ? customExcludes : java.util.Collections.emptySet();
        this.maxMembers = maxMembers;
    }

    @Override
    public Graph parse(Path path) {
        if (Files.isDirectory(path)) {
            log.fine(() -> "Delegating directory to ClassDiagramParser: " + path);
            if (classDiagramParser == null) classDiagramParser = new ClassDiagramParser(customExcludes, maxMembers);
            return classDiagramParser.parse(path);
        }
        String fileName = String.valueOf(path.getFileName());
        if ("module-info.java".equals(fileName)) {
            log.fine(() -> "Delegating module-info.java to ModuleInfoParser: " + path);
            if (moduleInfoParser == null) moduleInfoParser = new ModuleInfoParser();
            return moduleInfoParser.parse(path);
        }
        if (fileName.endsWith(".java")) {
            if (sequenceOnly) {
                log.fine(() -> "Delegating source file to CallSequenceParser (sequence-only): " + path);
                if (callSequenceParser == null) callSequenceParser = new CallSequenceParser(customExcludes);
                return callSequenceParser.parse(path);
            }
            log.fine(() -> "Delegating source file to ExceptionFlowParser: " + path);
            if (exceptionFlowParser == null) exceptionFlowParser = new ExceptionFlowParser(customExcludes);
            return exceptionFlowParser.parse(path);
        }
        log.warning("Unrecognised path, returning empty graph: " + path);
        return new Graph();
    }
}
