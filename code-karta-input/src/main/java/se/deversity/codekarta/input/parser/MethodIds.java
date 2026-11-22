package se.deversity.codekarta.input.parser;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import se.deversity.vibetags.annotations.AIArchitecture;

import java.util.List;
import java.util.StringJoiner;

/**
 * Node ids for METHOD nodes, shared by the three sequence parsers so a declaration and every call
 * that reaches it agree on one id.
 *
 * <p>A method's id is {@code Owner.name}. When the owner declares more than one method of that
 * name, the id carries the parameter types as well, {@code Owner.name(String,int)}: every
 * overload sharing {@code Owner.name} merged their call sequences into one node and drew
 * delegation between overloads as a self-call (issue #265). Parameter types are reduced to their
 * simple raw names ({@code List<String>} is {@code List}, {@code a.b.C} is {@code C},
 * {@code String...} is {@code String[]}), which keeps every dot out of the suffix: the renderer
 * splits participant from method on the last dot.
 */
@AIArchitecture(belongsTo = "input", cannotReference = {"layout", "render", "cli"})
public final class MethodIds {

    private MethodIds() {
    }

    /** The id of a method declared directly in {@code owner}, whose node id is {@code ownerId}. */
    public static String of(String ownerId, TypeDeclaration<?> owner, MethodDeclaration method) {
        String name = method.getNameAsString();
        if (owner.getMethodsByName(name).size() < 2) {
            return ownerId + "." + name;
        }
        StringJoiner params = new StringJoiner(",", "(", ")");
        method.getParameters().forEach(p -> params.add(
                simpleType(p.getType()) + (p.isVarArgs() ? "[]" : "")));
        return ownerId + "." + name + params;
    }

    /**
     * The id for an unqualified or {@code this.} call to a method {@code owner} declares, picking
     * the overload by argument count. Without a symbol solver that is the best evidence there
     * is; when several overloads share the count, the first declared one is chosen.
     *
     * @return the id, or {@code null} when {@code owner} declares no method of that name
     */
    public static String forLocalCall(String ownerId, TypeDeclaration<?> owner, MethodCallExpr call) {
        List<MethodDeclaration> candidates = owner.getMethodsByName(call.getNameAsString());
        if (candidates.isEmpty()) {
            return null;
        }
        int args = call.getArguments().size();
        MethodDeclaration chosen = candidates.stream()
                .filter(m -> !isVarArgs(m) && m.getParameters().size() == args)
                .findFirst()
                .or(() -> candidates.stream()
                        .filter(m -> isVarArgs(m) && args >= m.getParameters().size() - 1)
                        .findFirst())
                .orElse(candidates.get(0));
        return of(ownerId, owner, chosen);
    }

    /**
     * The id for a call the symbol solver resolved. The overload test runs against the declaring
     * type's own methods, the same set {@link #of} counts for a type parsed from source.
     */
    public static String forResolved(ResolvedMethodDeclaration resolved) {
        String declaringType = resolved.declaringType().getQualifiedName();
        String name = resolved.getName();
        long overloads;
        try {
            overloads = resolved.declaringType().getDeclaredMethods().stream()
                    .filter(m -> name.equals(m.getName()))
                    .count();
        } catch (RuntimeException e) {
            overloads = 1;
        }
        if (overloads < 2) {
            return declaringType + "." + name;
        }
        StringJoiner params = new StringJoiner(",", "(", ")");
        for (int i = 0; i < resolved.getNumberOfParams(); i++) {
            params.add(simpleType(resolved.getParam(i).describeType()));
        }
        return declaringType + "." + name + params;
    }

    /** A declared parameter type as {@link #simpleType(String)} spells a resolved one; annotations dropped. */
    static String simpleType(Type type) {
        if (type.isArrayType()) {
            return simpleType(type.asArrayType().getComponentType()) + "[]";
        }
        if (type.isClassOrInterfaceType()) {
            return type.asClassOrInterfaceType().getNameAsString();
        }
        return simpleType(type.asString());
    }

    /** {@code java.util.Map<K, List<V>>} → {@code Map}; array and varargs brackets kept as {@code []}. */
    static String simpleType(String type) {
        StringBuilder raw = new StringBuilder();
        int depth = 0;
        for (char c : type.toCharArray()) {
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (depth == 0 && !Character.isWhitespace(c)) {
                raw.append(c);
            }
        }
        String s = raw.toString().replace("...", "[]");
        int dot = s.lastIndexOf('.');
        return dot >= 0 ? s.substring(dot + 1) : s;
    }

    private static boolean isVarArgs(MethodDeclaration method) {
        return !method.getParameters().isEmpty()
                && method.getParameters().get(method.getParameters().size() - 1).isVarArgs();
    }
}
