package co.elastic.indytransformer;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.resolution.Context;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedTypeDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFactory;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class Utils {

    public static Optional<MethodDeclaration> findAnnotatedMethod(ClassOrInterfaceDeclaration type, String annoType, TypeSolver typeSolver) {
        return type.getMethods().stream()
                .filter(method -> hasAnnotation(method, annoType, typeSolver))
                .collect(atMostOne());
    }

    public static boolean hasAnnotation(NodeWithAnnotations<?> node, String annotationTypeName, TypeSolver typeSolver) {
        return getAnnotation(node, annotationTypeName, typeSolver).isPresent();
    }

    public static Optional<AnnotationExpr> getAnnotation(NodeWithAnnotations<?> node, String annotationTypeName, TypeSolver typeSolver) {
        return getAnnotation(node, annotationTypeName::equals, typeSolver);
    }

    public static Optional<AnnotationExpr> getAnnotation(NodeWithAnnotations<?> node, Predicate<String> annotationTypeNamePredicate, TypeSolver typeSolver) {
        for (AnnotationExpr anno : node.getAnnotations()) {
            Context context = JavaParserFactory.getContext(anno, typeSolver);
            SymbolReference<ResolvedTypeDeclaration> resolvedType = context.solveType(anno.getNameAsString(), null);
            if (resolvedType.isSolved() && annotationTypeNamePredicate.test(resolvedType.getCorrespondingDeclaration().getQualifiedName())) {
                return Optional.of(anno);
            }
        }
        return Optional.empty();
    }

    public static AnnotationExpr removeAnnotationArgumentValue(AnnotationExpr annotation, String argName) {
        if (annotation instanceof MarkerAnnotationExpr) {
            return annotation;
        }
        if (annotation instanceof SingleMemberAnnotationExpr) {
            SingleMemberAnnotationExpr sam = (SingleMemberAnnotationExpr) annotation;
            if (!"value".equals(argName)) {
                return annotation;
            }
            return new MarkerAnnotationExpr(annotation.getName());
        }
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr anno = ((NormalAnnotationExpr) annotation);
            NodeList<MemberValuePair> filteredPairs = anno.getPairs().stream()
                    .filter(pair -> !pair.getNameAsString().equals(argName))
                    .collect(Collectors.toCollection(NodeList::new));
            if (filteredPairs.isEmpty()) {
                return new MarkerAnnotationExpr(annotation.getName());
            } else if (filteredPairs.size() == 1 && filteredPairs.get(0).getNameAsString().equals("value")) {
                return new SingleMemberAnnotationExpr(anno.getName(), filteredPairs.get(0).getValue());
            } else {
                anno.setPairs(filteredPairs);
                return anno;
            }
        }
        throw new IllegalStateException("unexpected annotation type: " + annotation);
    }

    public static Optional<Expression> extractAnnotationArgumentValue(AnnotationExpr annotation, String argName) {
        if (annotation instanceof MarkerAnnotationExpr) {
            return Optional.empty();
        }
        if (annotation instanceof SingleMemberAnnotationExpr) {
            SingleMemberAnnotationExpr sam = (SingleMemberAnnotationExpr) annotation;
            if (!"value".equals(argName)) {
                return Optional.empty();
            }
            return Optional.of(sam.getMemberValue());
        }
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr anno = ((NormalAnnotationExpr) annotation);
            return anno.getPairs().stream()
                    .filter(pair -> pair.getNameAsString().equals(argName))
                    .map(MemberValuePair::getValue)
                    .findFirst();
        }
        throw new IllegalStateException("unexpected annotation type: " + annotation);
    }

    public static boolean resolveBooleanLiteral(Expression constExpr) {
        if (!constExpr.isBooleanLiteralExpr()) {
            throw new IllegalArgumentException("Expected boolean literal expression: " + constExpr);
        }
        return constExpr.asBooleanLiteralExpr().getValue();
    }

    public static boolean isLHSOfAssignment(NameExpr name) {
        Optional<Node> parent = name.getParentNode();
        if (parent.isEmpty()) {
            return false;
        }
        if (parent.get() instanceof AssignExpr) {
            AssignExpr assignment = (AssignExpr) parent.get();
            return assignment.getTarget() == name;
        }
        return false;
    }

    public static <T> Collector<T, ?, Optional<T>> atMostOne() {
        return Collectors.collectingAndThen(Collectors.toList(), elements -> {
            if (elements.isEmpty()) {
                return Optional.empty();
            }
            if (elements.size() == 1) {
                return Optional.of(elements.get(0));
            }
            throw new IllegalStateException("Expected at most one element: " + elements);
        });
    }

    public static String extractStringLiteral(Expression expr) {
        if (expr.isNullLiteralExpr()) {
            return null;
        }
        if (expr.isStringLiteralExpr()) {
            return expr.asStringLiteralExpr().asString();
        }
        throw new IllegalStateException("Not a string literal expression: " + expr);
    }

    public static int extractIntLiteral(Expression expr) {
        if (expr.isIntegerLiteralExpr()) {
            return expr.asIntegerLiteralExpr().asNumber().intValue();
        }
        throw new IllegalStateException("Not a int literal expression: " + expr);
    }

    public static void addImports(MethodDeclaration method, Class<?>... classesToImport) {
        CompilationUnit compilationUnit = method.findCompilationUnit().get();
        for (Class<?> clazz : classesToImport) {
            compilationUnit.addImport(clazz);
        }
    }
}
