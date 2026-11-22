package se.deversity.codekarta.input.parser;

import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
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

    /** The name part of a constructor's id and its node label: {@code Owner.new}. A keyword, so no method can clash. */
    public static final String CONSTRUCTOR = "new";

    /**
     * The id of a method or constructor declared directly in {@code owner}, whose node id is
     * {@code ownerId}. A constructor is {@code Owner.new}, suffixed like an overloaded method when
     * the type declares more than one.
     */
    public static String of(String ownerId, TypeDeclaration<?> owner, CallableDeclaration<?> callable) {
        boolean constructor = callable instanceof ConstructorDeclaration;
        String name = constructor ? CONSTRUCTOR : callable.getNameAsString();
        int siblings = constructor ? owner.getConstructors().size() : owner.getMethodsByName(name).size();
        if (siblings < 2) {
            return ownerId + "." + name;
        }
        StringJoiner params = new StringJoiner(",", "(", ")");
        callable.getParameters().forEach(p -> params.add(
                simpleType(p.getType()) + (p.isVarArgs() ? "[]" : "")));
        return ownerId + "." + name + params;
    }

    /** The node label: the method's name, or {@link #CONSTRUCTOR} for a constructor. */
    public static String labelOf(CallableDeclaration<?> callable) {
        return callable instanceof ConstructorDeclaration ? CONSTRUCTOR : callable.getNameAsString();
    }

    /** The methods and constructors {@code owner} declares itself, in declaration order. */
    public static List<CallableDeclaration<?>> callables(TypeDeclaration<?> owner) {
        List<CallableDeclaration<?>> callables = new java.util.ArrayList<>();
        owner.getMembers().forEach(member -> {
            if (member instanceof MethodDeclaration || member instanceof ConstructorDeclaration) {
                callables.add((CallableDeclaration<?>) member);
            }
        });
        return callables;
    }

    /** The id for {@code new Owner(...)} with {@code args} arguments; {@code Owner.new} when no constructor is declared. */
    public static String forCreation(String ownerId, TypeDeclaration<?> owner, int args) {
        List<ConstructorDeclaration> constructors = owner.getConstructors();
        return constructors.isEmpty() ? ownerId + "." + CONSTRUCTOR : of(ownerId, owner, pick(constructors, args));
    }

    /**
     * The id for a method reference {@code Owner::identifier}, or {@code Owner::new}. A reference
     * carries no argument count, so of several overloads the first declared one is named.
     *
     * @return the id, or {@code null} when {@code owner} declares no method of that name
     */
    public static String forReference(String ownerId, TypeDeclaration<?> owner, String identifier) {
        if (CONSTRUCTOR.equals(identifier)) {
            List<ConstructorDeclaration> constructors = owner.getConstructors();
            return constructors.isEmpty() ? ownerId + "." + CONSTRUCTOR : of(ownerId, owner, constructors.get(0));
        }
        List<MethodDeclaration> methods = owner.getMethodsByName(identifier);
        return methods.isEmpty() ? null : of(ownerId, owner, methods.get(0));
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
        return of(ownerId, owner, pick(candidates, call.getArguments().size()));
    }

    /** The overload taking exactly {@code args} arguments, else a varargs one that accepts them, else the first. */
    private static <T extends CallableDeclaration<?>> T pick(List<T> candidates, int args) {
        return candidates.stream()
                .filter(m -> !isVarArgs(m) && m.getParameters().size() == args)
                .findFirst()
                .or(() -> candidates.stream()
                        .filter(m -> isVarArgs(m) && args >= m.getParameters().size() - 1)
                        .findFirst())
                .orElse(candidates.get(0));
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

    /** The id for a constructor call the symbol solver resolved, matching {@link #of} for its declaration. */
    public static String forResolvedConstructor(ResolvedConstructorDeclaration resolved) {
        String declaringType = resolved.declaringType().getQualifiedName();
        int constructors;
        try {
            constructors = resolved.declaringType().getConstructors().size();
        } catch (RuntimeException e) {
            constructors = 1;
        }
        if (constructors < 2) {
            return declaringType + "." + CONSTRUCTOR;
        }
        StringJoiner params = new StringJoiner(",", "(", ")");
        for (int i = 0; i < resolved.getNumberOfParams(); i++) {
            params.add(simpleType(resolved.getParam(i).describeType()));
        }
        return declaringType + "." + CONSTRUCTOR + params;
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

    private static boolean isVarArgs(CallableDeclaration<?> method) {
        return !method.getParameters().isEmpty()
                && method.getParameters().get(method.getParameters().size() - 1).isVarArgs();
    }
}
