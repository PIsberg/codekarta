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
 * Facade that dispatches to the correct parser based on the path:
 *  - module-info.java          → ModuleInfoParser
 *  - directory                 → ClassDiagramParser
 *  - any .java file (default)  → ExceptionFlowParser (call graph + exception flow)
 *  - any .java file (sequenceOnly=true) → CallSequenceParser (call graph only)
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

    public JavaSourceInputParser() {
        this(false);
    }

    public JavaSourceInputParser(boolean sequenceOnly) {
        this(sequenceOnly, java.util.Collections.emptySet());
    }

    public JavaSourceInputParser(boolean sequenceOnly, Set<String> customExcludes) {
        this.sequenceOnly = sequenceOnly;
        this.customExcludes = customExcludes != null ? customExcludes : java.util.Collections.emptySet();
    }

    @Override
    public Graph parse(Path path) {
        if (Files.isDirectory(path)) {
            log.fine(() -> "Delegating directory to ClassDiagramParser: " + path);
            if (classDiagramParser == null) classDiagramParser = new ClassDiagramParser(customExcludes);
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
