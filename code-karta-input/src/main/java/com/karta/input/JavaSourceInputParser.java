package com.karta.input;

import com.karta.core.model.Graph;
import com.karta.input.parser.CallSequenceParser;
import com.karta.input.parser.ExceptionFlowParser;
import com.karta.input.parser.ClassDiagramParser;
import com.karta.input.parser.ModuleInfoParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Facade that dispatches to the correct parser based on the path:
 *  - module-info.java          → ModuleInfoParser
 *  - directory                 → ClassDiagramParser
 *  - any .java file (default)  → ExceptionFlowParser (call graph + exception flow)
 *  - any .java file (sequenceOnly=true) → CallSequenceParser (call graph only)
 */
public class JavaSourceInputParser implements InputParser {

    private static final Logger log = Logger.getLogger(JavaSourceInputParser.class.getName());

    private ModuleInfoParser moduleInfoParser;
    private ClassDiagramParser classDiagramParser;
    private ExceptionFlowParser exceptionFlowParser;
    private CallSequenceParser callSequenceParser;
    private final boolean sequenceOnly;

    public JavaSourceInputParser() {
        this(false);
    }

    public JavaSourceInputParser(boolean sequenceOnly) {
        this.sequenceOnly = sequenceOnly;
    }

    @Override
    public Graph parse(Path path) {
        if (Files.isDirectory(path)) {
            log.fine(() -> "Delegating directory to ClassDiagramParser: " + path);
            if (classDiagramParser == null) classDiagramParser = new ClassDiagramParser();
            return classDiagramParser.parse(path);
        }
        String fileName = path.getFileName().toString();
        if ("module-info.java".equals(fileName)) {
            log.fine(() -> "Delegating module-info.java to ModuleInfoParser: " + path);
            if (moduleInfoParser == null) moduleInfoParser = new ModuleInfoParser();
            return moduleInfoParser.parse(path);
        }
        if (fileName.endsWith(".java")) {
            if (sequenceOnly) {
                log.fine(() -> "Delegating source file to CallSequenceParser (sequence-only): " + path);
                if (callSequenceParser == null) callSequenceParser = new CallSequenceParser();
                return callSequenceParser.parse(path);
            }
            log.fine(() -> "Delegating source file to ExceptionFlowParser: " + path);
            if (exceptionFlowParser == null) exceptionFlowParser = new ExceptionFlowParser();
            return exceptionFlowParser.parse(path);
        }
        log.warning("Unrecognised path, returning empty graph: " + path);
        return new Graph();
    }
}
