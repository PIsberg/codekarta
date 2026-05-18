package com.karta.input;

import com.karta.core.model.Graph;
import com.karta.input.parser.ExceptionFlowParser;
import com.karta.input.parser.ClassDiagramParser;
import com.karta.input.parser.ModuleInfoParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Facade that dispatches to the correct parser based on the path:
 *  - module-info.java  → ModuleInfoParser
 *  - directory         → ClassDiagramParser
 *  - any .java file    → CallSequenceParser
 */
public class JavaSourceInputParser implements InputParser {

    private static final Logger log = Logger.getLogger(JavaSourceInputParser.class.getName());

    private final ModuleInfoParser moduleInfoParser = new ModuleInfoParser();
    private final ClassDiagramParser classDiagramParser = new ClassDiagramParser();
    private final ExceptionFlowParser exceptionFlowParser = new ExceptionFlowParser();

    @Override
    public Graph parse(Path path) {
        if (Files.isDirectory(path)) {
            log.fine(() -> "Delegating directory to ClassDiagramParser: " + path);
            return classDiagramParser.parse(path);
        }
        String fileName = path.getFileName().toString();
        if ("module-info.java".equals(fileName)) {
            log.fine(() -> "Delegating module-info.java to ModuleInfoParser: " + path);
            return moduleInfoParser.parse(path);
        }
        if (fileName.endsWith(".java")) {
            log.fine(() -> "Delegating source file to ExceptionFlowParser: " + path);
            return exceptionFlowParser.parse(path);
        }
        log.warning("Unrecognised path, returning empty graph: " + path);
        return new Graph();
    }
}
