package se.deversity.codekarta.input.parser;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.jspecify.annotations.Nullable;
import se.deversity.vibetags.annotations.AIArchitecture;

import java.util.Map;
import java.util.Set;

/**
 * What a call inside one method of one class reaches, for the two single-file sequence parsers
 * ({@link CallSequenceParser}, {@link ExceptionFlowParser}), which have no symbol solver and must
 * agree on every callee id.
 *
 * <p>Three expressions are calls: {@code receiver.method(...)}, a method reference
 * ({@code this::convert}, {@code processor::handle}, {@code Util::normalize}, {@code Type::new}),
 * and {@code new Type(...)} of a type declared in the same file. The last two used to draw
 * nothing at all (issue #271).
 */
@AIArchitecture(belongsTo = "input", cannotReference = {"layout", "render", "cli"})
final class SingleFileCallees {

    /** A resolved callee: its node id and the label its node takes. */
    record Callee(String id, String label) {}

    private final String className;
    private final TypeDeclaration<?> classDecl;
    private final Map<String, String> returnTypes;
    private final Map<String, String> varTypes;
    private final Map<String, TypeDeclaration<?>> fileTypes;
    private final Set<String> externalTypes;
    private final Set<String> customExcludes;

    SingleFileCallees(String className, TypeDeclaration<?> classDecl, Map<String, String> returnTypes,
                      Map<String, String> varTypes, Map<String, TypeDeclaration<?>> fileTypes,
                      Set<String> externalTypes, Set<String> customExcludes) {
        this.className = className;
        this.classDecl = classDecl;
        this.returnTypes = returnTypes;
        this.varTypes = varTypes;
        this.fileTypes = fileTypes;
        this.externalTypes = externalTypes;
        this.customExcludes = customExcludes;
    }

    /** The callee of any of the three call expressions, or {@code null} for anything else or a filtered call. */
    @Nullable Callee of(Expression expr) {
        if (expr instanceof MethodCallExpr call) {
            return ofCall(call);
        }
        if (expr instanceof MethodReferenceExpr ref) {
            return ofReference(ref);
        }
        if (expr instanceof ObjectCreationExpr creation) {
            return ofCreation(creation);
        }
        return null;
    }

    private @Nullable Callee ofCall(MethodCallExpr call) {
        String name = call.getNameAsString();
        if (FilterMatcher.matchesAny(name, customExcludes)) {
            return null;
        }
        String callee;
        if (call.getScope().isPresent()) {
            if (SequenceFilterUtil.shouldSkipScopedCall(name)) {
                return null;
            }
            String scopeName = CallSequenceParser.resolveScope(call.getScope().get(), returnTypes, className, varTypes);
            if (scopeName == null || externalTypes.contains(scopeName)) {
                return null;
            }
            callee = CallSequenceParser.calleeIn(scopeName, call, fileTypes);
        } else if (returnTypes.containsKey(name)) {
            // local method call — qualify so it joins this class's lifeline
            callee = MethodIds.forLocalCall(className, classDecl, call);
        } else {
            return null; // unscoped non-local call (static import etc.) — not attributable
        }
        return accept(callee, name);
    }

    private @Nullable Callee ofReference(MethodReferenceExpr ref) {
        String identifier = ref.getIdentifier();
        boolean constructor = MethodIds.CONSTRUCTOR.equals(identifier);
        if (!constructor && (FilterMatcher.matchesAny(identifier, customExcludes)
                || SequenceFilterUtil.shouldSkipScopedCall(identifier))) {
            return null;
        }
        String scopeName = referenceScope(ref.getScope());
        if (scopeName == null || externalTypes.contains(scopeName)) {
            return null;
        }
        TypeDeclaration<?> declared = className.equals(scopeName) ? classDecl : fileTypes.get(scopeName);
        String callee = declared != null
                ? MethodIds.forReference(scopeName, declared, identifier)
                : constructor ? null : scopeName + "." + identifier;
        return callee == null ? null : accept(callee, identifier);
    }

    /**
     * {@code this}, a variable, or a type name. JavaParser cannot tell {@code processor::handle}
     * from {@code Util::normalize} by syntax and may parse either scope as a type, so a simple
     * name is looked up as a variable first.
     */
    private @Nullable String referenceScope(Expression scope) {
        if (scope.isThisExpr()) {
            return className;
        }
        String simple = null;
        if (scope.isNameExpr()) {
            simple = scope.asNameExpr().getNameAsString();
        } else if (scope.isTypeExpr()) {
            Type type = scope.asTypeExpr().getType();
            if (type.isClassOrInterfaceType() && type.asClassOrInterfaceType().getScope().isEmpty()) {
                simple = type.asClassOrInterfaceType().getNameAsString();
            } else if (type.isClassOrInterfaceType()) {
                return type.asClassOrInterfaceType().getNameAsString();
            }
        }
        return simple == null ? null
                : CallSequenceParser.resolveScope(new NameExpr(simple), returnTypes, className, varTypes);
    }

    private @Nullable Callee ofCreation(ObjectCreationExpr creation) {
        if (creation.getAnonymousClassBody().isPresent()) {
            return null; // an anonymous class's body is walked as part of the enclosing method
        }
        String type = creation.getType().getNameAsString();
        TypeDeclaration<?> declared = fileTypes.get(type);
        if (declared == null || FilterMatcher.matchesAny(type, customExcludes)) {
            return null; // only constructors this file declares: new ArrayList<>() is not a domain call
        }
        return accept(MethodIds.forCreation(type, declared, creation.getArguments().size()), MethodIds.CONSTRUCTOR);
    }

    private @Nullable Callee accept(String callee, String label) {
        return FilterMatcher.matchesAny(callee, customExcludes) ? null : new Callee(callee, label);
    }

    /**
     * Walks a method or constructor body in source order, handing every resolved call to
     * {@code sink} before descending into it, so {@code a(b())} reports {@code a} then {@code b}.
     */
    static void forEachCall(Node body, SingleFileCallees callees, java.util.function.Consumer<Callee> sink) {
        body.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr call, Void arg) {
                report(call);
                super.visit(call, arg);
            }

            @Override
            public void visit(MethodReferenceExpr ref, Void arg) {
                report(ref);
                super.visit(ref, arg);
            }

            @Override
            public void visit(ObjectCreationExpr creation, Void arg) {
                report(creation);
                super.visit(creation, arg);
            }

            private void report(Expression expr) {
                Callee callee = callees.of(expr);
                if (callee != null) {
                    sink.accept(callee);
                }
            }
        }, null);
    }
}
