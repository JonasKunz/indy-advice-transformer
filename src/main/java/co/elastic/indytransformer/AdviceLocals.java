package co.elastic.indytransformer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.lexicalpreservation.GeneratedClassCleaner;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserParameterDeclaration;
import com.google.common.collect.Streams;
import net.bytebuddy.asm.Advice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AdviceLocals {

    private static final String CONTAINER_CLASS_NAME = "AdviceLocals";

    private static final String ADVICE_LOCAL = Advice.Local.class.getCanonicalName();
    private static final String ADVICE_ENTER = Advice.Enter.class.getCanonicalName();
    public static final String LOCALS_CONTAINER_VAR_NAME = "locals";

    private final Map<String, Parameter> enterLocals;
    private final Map<String, Parameter> exitLocals;

    /**
     * A parameter of the original exit method annotated with @Advice.Enter
     */
    private final Parameter exitEnterParameter;
    private final TypeSolver typeSolver;

    public static Optional<AdviceLocals> create(MethodDeclaration enterMethod, MethodDeclaration exitMethod, TypeSolver typeSolver) {
        Map<String, Parameter> enterLocals = collectAdviceLocalParameters(enterMethod, typeSolver);
        Map<String, Parameter> exitLocals = collectAdviceLocalParameters(exitMethod, typeSolver);
        Parameter exitEnterParameter = null;
        if (exitMethod != null) {
            exitEnterParameter = exitMethod.getParameters().stream()
                    .filter(param -> Utils.getAnnotation(param, ADVICE_ENTER, typeSolver).isPresent())
                    .collect(Utils.atMostOne())
                    .orElse(null);
        }

        if (enterLocals.isEmpty() && exitLocals.isEmpty() && exitEnterParameter == null) {
            return Optional.empty();
        }
        return Optional.of(new AdviceLocals(enterLocals, exitLocals, exitEnterParameter, typeSolver));
    }

    private AdviceLocals(Map<String, Parameter> enterLocals, Map<String, Parameter> exitLocals, Parameter exitEnterParameter, TypeSolver typeSolver) {
        this.enterLocals = enterLocals;
        this.exitLocals = exitLocals;
        this.exitEnterParameter = exitEnterParameter;
        this.typeSolver = typeSolver;
        if (!enterLocals.keySet().equals(exitLocals.keySet())) {
            throw new IllegalArgumentException("Advice enter and exit use a different set of advice locals!");
        }
    }

    public boolean anyAdviceLocalsFound() {
        return !enterLocals.isEmpty();
    }

    private static Map<String, Parameter> collectAdviceLocalParameters(MethodDeclaration method, TypeSolver typeSolver) {
        if (method == null) {
            return Collections.emptyMap();
        }
        return method.getParameters().stream()
                .filter(param -> Utils.getAnnotation(param, ADVICE_LOCAL, typeSolver).isPresent())
                .collect(Collectors.toMap(
                        param -> {
                            AnnotationExpr adviceLocalAnno = Utils.getAnnotation(param, ADVICE_LOCAL, typeSolver).get();
                            return Utils.extractStringLiteral(Utils.extractAnnotationArgumentValue(adviceLocalAnno, "value").get());
                        },
                        param -> param
                ));
    }

    public boolean requiresContainerClass() {
        int count = enterLocals.size();
        if (exitEnterParameter != null) {
            count++;
        }
        return count > 1;
    }

    private Type getLocalsCarrierType() {
        if (requiresContainerClass()) {
            return StaticJavaParser.parseType(CONTAINER_CLASS_NAME);
        } else {
            return Stream.concat(enterLocals.values().stream(), exitLocals.values().stream())
                    .map(Parameter::getType)
                    .findFirst()
                    .orElseGet(() -> exitEnterParameter.getType());
        }
    }

    public ClassOrInterfaceDeclaration getContainerClass() {
        ClassOrInterfaceDeclaration clazz = new ClassOrInterfaceDeclaration(new NodeList<>(
                Modifier.publicModifier(),
                Modifier.staticModifier()
        ), false, CONTAINER_CLASS_NAME);

        if (exitEnterParameter != null) {
            clazz.addMember(new FieldDeclaration(new NodeList<>(Modifier.publicModifier()), exitEnterParameter.getType(), exitEnterParameter.getNameAsString()));
        }
        for (Parameter adviceLocalParam : enterLocals.values()) {
            String fieldName = getContainerFieldNameForParameter(adviceLocalParam);
            Type type = adviceLocalParam.getType();
            clazz.addMember(new FieldDeclaration(new NodeList<>(Modifier.publicModifier()), type, fieldName));
        }

        //By default, the printer generates to many unnecessary empty lines for our style, so we remove them
        GeneratedClassCleaner.cleanup(clazz);
        return clazz;
    }

    public String getContainerFieldNameForParameter(Parameter param) {
        if (param == exitEnterParameter) {
            return exitEnterParameter.getNameAsString();
        }
        String bytebuddyVarName = Streams.concat(
                        enterLocals.entrySet().stream(),
                        exitLocals.entrySet().stream()
                ).filter(entry -> entry.getValue() == param)
                .map(Map.Entry::getKey)
                .findFirst().get();
        return enterLocals.get(bytebuddyVarName).getNameAsString();
    }

    public ValueToReturn<?> transformEnterMethod(MethodDeclaration enterMethod) {
        VariableDeclarator localDeclarator;
        if (requiresContainerClass()) {
            ClassOrInterfaceType type = (ClassOrInterfaceType) getLocalsCarrierType();
            localDeclarator = new VariableDeclarator(type, LOCALS_CONTAINER_VAR_NAME, new ObjectCreationExpr(null, type, new NodeList<>()));
            enterLocals.values().forEach(param -> {
                replaceParameterReferences(param, new FieldAccessExpr(new NameExpr(LOCALS_CONTAINER_VAR_NAME), getContainerFieldNameForParameter(param)));
                param.remove();
            });
            if (exitEnterParameter != null) {
                replaceReturnExpressionsWithAssignments(enterMethod, new FieldAccessExpr(new NameExpr(LOCALS_CONTAINER_VAR_NAME), getContainerFieldNameForParameter(exitEnterParameter)));
            }
        } else {
            // Just a single local variable, which is either a single @Advice.Local parameter
            // OR we return a value which is used via @Advice.Enter in the exit method
            // in the latter case we MUST convert it to a local variable to make sure additional enhancements work correctly
            String localVarName;
            Type varType;
            if (exitEnterParameter != null) {
                localVarName = exitEnterParameter.getNameAsString();
                varType = getLocalsCarrierType();
                replaceReturnExpressionsWithAssignments(enterMethod, new NameExpr(localVarName));
                // Replace
                // return XYZ;
                // with
                // localVar = XYZ;
                // return;
            } else {
                Parameter singleLocalParam = enterLocals.values().iterator().next();
                localVarName = singleLocalParam.getNameAsString();
                varType = singleLocalParam.getType();
                singleLocalParam.remove();
            }
            localDeclarator = new VariableDeclarator(varType, localVarName, getDefaultValue(varType));
        }
        enterMethod.getBody().get().addAndGetStatement(0, new ExpressionStmt(new VariableDeclarationExpr(localDeclarator)));
        return new ValueToReturn<>(localDeclarator, null);
    }

    private void replaceReturnExpressionsWithAssignments(MethodDeclaration method, Expression assignmentLHS) {
        List<ReturnStmt> allReturns = new ArrayList<>();
        new ScopedReturnVisitor<Void>() {
            @Override
            public void visit(ReturnStmt returnStmt, Void arg) {
                allReturns.add(returnStmt);
            }
        }.visit(method.getBody().get(), null);

        for (ReturnStmt returnStmt : allReturns) {
            //At the moment we only support returns which are in a block statement
            BlockStmt parentBlock = (BlockStmt) returnStmt.getParentNode().get();
            int insertionIndex = parentBlock.getStatements().indexOf(returnStmt);
            Expression value = returnStmt.getExpression().get();
            returnStmt.setExpression(null);

            AssignExpr assignment = new AssignExpr(assignmentLHS.clone(), value, AssignExpr.Operator.ASSIGN);
            parentBlock.addAndGetStatement(insertionIndex, new ExpressionStmt(assignment));
        }
    }

    record ExitTransformResult(Parameter adviceEnterParameter, VariableDeclarator localsUnpackingDeclaration) {
    }

    public ExitTransformResult transformExitMethod(MethodDeclaration exitMethod, boolean enterHasMultipleReturnValues) {

        Parameter adviceEnterParameter;
        VariableDeclarator localsUnpackingDeclaration = null;

        List<Parameter> exitParams = Streams.concat(
                exitLocals.values().stream(),
                Optional.ofNullable(exitEnterParameter).stream()
        ).toList();
        String name;
        Type type = getLocalsCarrierType();
        if (requiresContainerClass()) {
            name = LOCALS_CONTAINER_VAR_NAME;
        } else {
            name = exitParams.get(0).getNameAsString();
        }
        if (!enterHasMultipleReturnValues) {
            //No need to perform array unpacking
            adviceEnterParameter = new Parameter(type, name);
        } else {
            Type objectArrayType = StaticJavaParser.parseType("Object[]");
            adviceEnterParameter = new Parameter(objectArrayType, "enterResult");
            ArrayAccessExpr arrayAccess = new ArrayAccessExpr(new NameExpr("enterResult"), new IntegerLiteralExpr(String.valueOf(0)));
            localsUnpackingDeclaration = new VariableDeclarator(type, name, new CastExpr(type, arrayAccess));
            exitMethod.getBody().get().addAndGetStatement(0, new ExpressionStmt(new VariableDeclarationExpr(localsUnpackingDeclaration)));
        }
        adviceEnterParameter.addAnnotation(new MarkerAnnotationExpr("Advice.Enter"));

        if (requiresContainerClass()) {
            exitParams.forEach(param -> {
                replaceParameterReferences(param, new FieldAccessExpr(new NameExpr(LOCALS_CONTAINER_VAR_NAME), getContainerFieldNameForParameter(param)));
                param.remove();
            });
        } else {
            //Just a single local variable, we simply replace the parameter with a local variable with the same name - no other changes needed
            Parameter singleLocalParam = exitParams.get(0);
            singleLocalParam.remove();
        }
        // add the new parameter last, because it's name might conflict with existing Advice.Local parameters which need
        // to be removed prior to the addition
        exitMethod.addParameter(adviceEnterParameter);

        return new ExitTransformResult(adviceEnterParameter, localsUnpackingDeclaration);
    }


    private LiteralExpr getDefaultValue(Type type) {
        if (type.isReferenceType()) {
            return new NullLiteralExpr();
        } else if (type.isPrimitiveType()) {
            PrimitiveType primitiveType = type.asPrimitiveType();
            switch (primitiveType.getType()) {
                case BOOLEAN:
                    return new BooleanLiteralExpr(false);
                default:
                    return new IntegerLiteralExpr(String.valueOf(0));
            }
        }
        throw new IllegalArgumentException("Unhandled type: " + type);
    }


    private void replaceParameterReferences(Parameter param, Expression replacement) {
        MethodDeclaration declaringMethod = (MethodDeclaration) param.getParentNode().get();
        new ModifierVisitor<Void>() {
            @Override
            public Expression visit(NameExpr name, Void arg) {
                if (name.getNameAsString().equals(param.getNameAsString())) {
                    SymbolReference<? extends ResolvedValueDeclaration> result =
                            JavaParserFacade.get(typeSolver).solve((name));
                    if (result.isSolved()) {
                        ResolvedValueDeclaration correspondingDeclaration = result.getCorrespondingDeclaration();
                        if (correspondingDeclaration instanceof JavaParserParameterDeclaration paramDecl
                            && paramDecl.getWrappedNode() == param) {
                            return replacement.clone();
                        }
                    }
                }
                return name;
            }
        }.visit(declaringMethod, null);
    }
}
