package com.karta.input;

import com.karta.core.model.Graph;
import se.deversity.vibetags.annotations.AIContract;

import java.nio.file.Path;

@AIContract(reason = "parse(Path) must never throw — all parsers wrap JavaParser calls in try/catch, log a warning, and return a partial (possibly empty) Graph. The fault-tolerance contract ensures the pipeline always produces some output.")
@FunctionalInterface
public interface InputParser {
    Graph parse(Path path);
}
