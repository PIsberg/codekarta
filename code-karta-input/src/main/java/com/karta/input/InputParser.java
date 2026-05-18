package com.karta.input;

import com.karta.core.model.Graph;

import java.nio.file.Path;

public interface InputParser {
    Graph parse(Path path);
}
