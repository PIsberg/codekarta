package com.karta.input.parser;

import java.util.Set;

final class SequenceFilterUtil {

    // Method names that produce noise in sequence diagrams: stdlib operations,
    // logging, IO, stream/optional pipelines — never cross-class domain calls.
    static final Set<String> SKIP_METHODS = Set.of(
            // Object
            "toString", "hashCode", "equals", "getClass",
            // Collections
            "add", "addAll", "remove", "removeAll", "removeIf", "clear",
            "contains", "containsKey", "containsValue", "size", "isEmpty",
            "get", "set", "put", "putAll", "putIfAbsent", "getOrDefault",
            "entrySet", "keySet", "values", "iterator", "toArray", "subList",
            "sort", "computeIfAbsent",
            // Stream / pipeline
            "stream", "parallelStream", "filter", "map", "flatMap",
            "mapToInt", "mapToLong", "mapToDouble", "collect", "toList",
            "toSet", "forEach", "forEachOrdered", "reduce", "count",
            "anyMatch", "allMatch", "noneMatch", "findFirst", "findAny",
            "sorted", "distinct", "limit", "skip", "peek", "boxed",
            // Optional
            "orElse", "orElseGet", "orElseThrow", "isPresent", "ifPresent",
            "ifPresentOrElse", "empty", "ofNullable", "of",
            // String operations
            "length", "charAt", "substring", "indexOf", "lastIndexOf",
            "startsWith", "endsWith", "replace", "replaceAll", "replaceFirst",
            "split", "trim", "strip", "stripLeading", "stripTrailing",
            "toLowerCase", "toUpperCase", "valueOf", "format", "join",
            "intern", "matches", "compareTo", "compareToIgnoreCase",
            "equalsIgnoreCase", "concat", "chars", "codePoints",
            // StringBuilder / StringBuffer
            "append", "insert", "delete", "deleteCharAt", "reverse",
            // Logging
            "warning", "info", "fine", "finer", "finest", "severe",
            "config", "log", "isLoggable", "entering", "exiting", "throwing",
            // java.nio.file.Files / Path
            "readString", "writeString", "createDirectories", "createFile",
            "deleteIfExists", "copy", "move", "walk", "list",
            "exists", "isDirectory", "isRegularFile", "isReadable",
            "resolve", "resolveSibling", "toAbsolutePath", "getFileName",
            "getParent", "normalize", "relativize", "toUri", "toFile",
            "toPath",
            // System / runtime
            "println", "print", "printf", "flush", "close", "exit",
            "currentTimeMillis", "nanoTime", "gc", "arraycopy",
            // Math
            "abs", "ceil", "floor", "round", "pow", "sqrt", "min", "max",
            // Comparator / functional
            "compare", "comparing", "thenComparing", "reversed",
            "naturalOrder", "reverseOrder"
    );

    private SequenceFilterUtil() {}
}
