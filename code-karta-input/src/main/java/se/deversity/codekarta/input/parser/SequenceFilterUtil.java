package se.deversity.codekarta.input.parser;

import java.util.Set;
import se.deversity.vibetags.annotations.AIContext;

@AIContext(
    focus = "SKIP_METHODS is a scoped-call filter only — it applies to receiver.method() calls. Unscoped calls (direct local method invocations) are never filtered because they represent intra-class domain logic. shouldSkipScopedCall() is the single decision point used by all parsers.",
    avoids = "Adding method names that could be legitimate domain operations (e.g. 'process', 'execute', 'run') — only add names that are unambiguously stdlib/infrastructure noise regardless of context."
)
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
            "naturalOrder", "reverseOrder",
            // Throwable / Exception — catch-block noise (e.getMessage(), t.getCause(), …)
            "getMessage", "getLocalizedMessage", "getCause", "getStackTrace",
            "printStackTrace", "fillInStackTrace", "getSuppressed", "addSuppressed",
            // Getter/setter noise for infrastructure objects
            "getLogger", "getName", "getSimpleName", "getCanonicalName"
    );

    /**
     * Returns true when a scoped call should be excluded from sequence diagrams.
     */
    static boolean shouldSkipScopedCall(String methodName) {
        return SKIP_METHODS.contains(methodName);
    }

    // java.lang types are importable without an import statement, so they can't be
    // detected via the compilation unit's import list.
    private static final Set<String> JAVA_LANG_TYPES = Set.of(
            "Object", "Class", "String", "StringBuilder", "StringBuffer",
            "Integer", "Long", "Short", "Byte", "Double", "Float", "Boolean",
            "Character", "Number", "Void", "Math", "StrictMath", "System",
            "Thread", "Runtime", "Process", "ProcessBuilder", "Runnable",
            "Throwable", "Exception", "Error", "RuntimeException",
            "IllegalArgumentException", "IllegalStateException",
            "UnsupportedOperationException", "NullPointerException",
            "Iterable", "Comparable", "CharSequence", "Enum", "Record");

    private static final Set<String> EXTERNAL_PACKAGE_PREFIXES = Set.of(
            "java.", "javax.", "jdk.", "sun.", "com.sun.");

    /**
     * Simple names of types the compilation unit imports from the JDK/platform,
     * plus the implicit java.lang types. Used by the single-file parsers (which
     * have no symbol resolution) to keep stdlib receivers such as
     * {@code System}, {@code Files}, or {@code MemorySegment} out of diagrams.
     */
    static Set<String> externalTypeNames(com.github.javaparser.ast.CompilationUnit cu) {
        Set<String> names = new java.util.HashSet<>(JAVA_LANG_TYPES);
        cu.getImports().forEach(imp -> {
            String qualified = imp.getNameAsString();
            for (String prefix : EXTERNAL_PACKAGE_PREFIXES) {
                if (qualified.startsWith(prefix)) {
                    names.add(qualified.substring(qualified.lastIndexOf('.') + 1));
                    break;
                }
            }
        });
        return names;
    }

    private SequenceFilterUtil() {}
}
