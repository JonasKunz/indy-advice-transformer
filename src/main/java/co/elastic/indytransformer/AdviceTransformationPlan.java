package co.elastic.indytransformer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.ArrayCreationLevel;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserParameterDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserVariableDeclaration;
import com.google.common.collect.Streams;
import net.bytebuddy.asm.Advice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AdviceTransformationPlan {

    private static final String ADVICE_ON_METHOD_ENTER = Advice.OnMethodEnter.class.getCanonicalName();
    private static final String ADVICE_ON_METHOD_EXIT = Advice.OnMethodExit.class.getCanonicalName();
    private static final String ADVICE_RETURN = Advice.Return.class.getCanonicalName();
    private static final String ADVICE_FIELDVALUE = Advice.FieldValue.class.getCanonicalName();
    private static final String ADVICE_ARGUMENT = Advice.Argument.class.getCanonicalName();
    public static final String ASSIGNRETURNED_CLASS = Advice.AssignReturned.class.getCanonicalName();

    private final TypeSolver typeSolver;
    private final ClassOrInterfaceDeclaration adviceClass;
    private final MethodDeclaration enterMethod;
    private final MethodDeclaration exitMethod;

    private final List<Parameter> enterWrittenArguments;
    private final List<Parameter> enterWrittenFieldValues;

    private final Parameter assignedReturnParam;
    private final List<Parameter> exitWrittenFieldValues;

    private final AdviceLocals locals;

    private AdviceTransformationPlan(ClassOrInterfaceDeclaration adviceClass, TypeSolver typeSolver) {
        this.typeSolver = typeSolver;
        this.adviceClass = adviceClass;
        enterMethod = Utils.findAnnotatedMethod(adviceClass, ADVICE_ON_METHOD_ENTER, typeSolver).orElse(null);
        exitMethod = Utils.findAnnotatedMethod(adviceClass, ADVICE_ON_METHOD_EXIT, typeSolver).orElse(null);

        locals = AdviceLocals.create(enterMethod, exitMethod, typeSolver).orElse(null);

        if (enterMethod != null) {
            enterWrittenArguments = findNonReadOnlyParameters(enterMethod, ADVICE_ARGUMENT, typeSolver)
                    .toList();
            enterWrittenFieldValues = findNonReadOnlyParameters(enterMethod, ADVICE_FIELDVALUE, typeSolver)
                    .toList();
        } else {
            enterWrittenArguments = Collections.emptyList();
            enterWrittenFieldValues = Collections.emptyList();
        }

        if (exitMethod != null) {
            assignedReturnParam = findNonReadOnlyParameters(exitMethod, ADVICE_RETURN, typeSolver)
                    .collect(Utils.atMostOne())
                    .orElse(null);

            exitWrittenFieldValues = findNonReadOnlyParameters(exitMethod, ADVICE_FIELDVALUE, typeSolver)
                    .toList();
        } else {
            assignedReturnParam = null;
            exitWrittenFieldValues = Collections.emptyList();
        }
    }

    public static Optional<AdviceTransformationPlan> create(ClassOrInterfaceDeclaration maybeAdviceClass, TypeSolver typeSolver) {

        if (maybeAdviceClass.isInterface()) {
            return Optional.empty();
        }

        Optional<MethodDeclaration> enterMethod = Utils.findAnnotatedMethod(maybeAdviceClass, ADVICE_ON_METHOD_ENTER, typeSolver);
        Optional<MethodDeclaration> exitMethod = Utils.findAnnotatedMethod(maybeAdviceClass, ADVICE_ON_METHOD_EXIT, typeSolver);
        if (!enterMethod.isPresent() && !exitMethod.isPresent()) {
            return Optional.empty();
        }

        boolean isAlreadyMigrated = Streams.concat(enterMethod.stream(), exitMethod.stream())
                .anyMatch(method -> Utils.getAnnotation(method, anno -> anno.startsWith(ASSIGNRETURNED_CLASS), typeSolver).isPresent());

        if (isAlreadyMigrated) {
            return Optional.empty();
        }

        return Optional.of(new AdviceTransformationPlan(maybeAdviceClass, typeSolver));
    }


    public boolean transform() {

        if (enterWrittenArguments.isEmpty()
            && enterWrittenFieldValues.isEmpty()
            && assignedReturnParam == null
            && exitWrittenFieldValues.isEmpty()
            && (locals == null || !locals.anyAdviceLocalsFound())
        ) {
            return false;
        }

        if (locals != null && locals.requiresContainerClass()) {
            ClassOrInterfaceDeclaration adviceClass = (ClassOrInterfaceDeclaration) enterMethod.getParentNode().get();
            NodeList<BodyDeclaration<?>> members = adviceClass.getMembers();
            int insertionIndex = members.indexOf(enterMethod);
            members.add(insertionIndex, locals.getContainerClass());
        }

        List<ValueToReturn<?>> enterReturnValues = new ArrayList<>();
        if (locals != null) {
            enterReturnValues.add(locals.transformEnterMethod(enterMethod));
        }
        enterReturnValues.addAll(transformFieldAssignments(enterWrittenFieldValues));
        enterReturnValues.addAll(transformArgumentAssignments(enterWrittenArguments));

        replaceReturnValueAndAddAnnotations(enterMethod, enterReturnValues);
        enterReturnValues.forEach(val -> removeParameterOrVariableIfNeverUsed(enterMethod, val.parameterOrVariable()));

        Parameter adviceEnterParameter = null;
        VariableDeclarator localsUnpackingDeclaration = null;
        if (locals != null) {
            AdviceLocals.ExitTransformResult exitTransformResult = locals.transformExitMethod(exitMethod, enterReturnValues.size() > 1);
            adviceEnterParameter = exitTransformResult.adviceEnterParameter();
            localsUnpackingDeclaration = exitTransformResult.localsUnpackingDeclaration();
        }

        List<ValueToReturn<?>> exitReturnValues = new ArrayList<>();

        if (assignedReturnParam != null) {
            exitReturnValues.add(transformReturnValueAssignment(assignedReturnParam));
        }

        exitReturnValues.addAll(transformFieldAssignments(exitWrittenFieldValues));

        replaceReturnValueAndAddAnnotations(exitMethod, exitReturnValues);
        if (localsUnpackingDeclaration != null) {
            removeParameterOrVariableIfNeverUsed(exitMethod, localsUnpackingDeclaration);
        }
        if (adviceEnterParameter != null) {
            removeParameterOrVariableIfNeverUsed(exitMethod, adviceEnterParameter);
        }
        exitReturnValues.forEach(val -> removeParameterOrVariableIfNeverUsed(exitMethod, val.parameterOrVariable()));

        return true;
    }

    private static Stream<Parameter> findNonReadOnlyParameters(MethodDeclaration method, String annotationFqn, TypeSolver typeSolver) {
        return method.getParameters().stream()
                .filter(param -> Utils.getAnnotation(param, annotationFqn, typeSolver)
                        .stream()
                        .flatMap(anno -> Utils.extractAnnotationArgumentValue(anno, "readOnly").stream())
                        .anyMatch(readonlyValue -> Utils.resolveBooleanLiteral(readonlyValue) == false)
                );
    }

    private List<ValueToReturn<?>> transformArgumentAssignments(List<Parameter> enterWrittenArguments) {
        List<ValueToReturn<?>> returns = new ArrayList<>();
        for (Parameter writtenArg : enterWrittenArguments) {
            AnnotationExpr anno = Utils.getAnnotation(writtenArg, ADVICE_ARGUMENT, typeSolver).get();
            int argIndex = Utils.extractIntLiteral(Utils.extractAnnotationArgumentValue(anno, "value").get());
            anno.replace(Utils.removeAnnotationArgumentValue(anno, "readOnly"));

            returns.add(new ValueToReturn<>(writtenArg, (method, index) -> {
                Utils.addImports(method, Advice.AssignReturned.class, Advice.AssignReturned.ToArguments.ToArgument.class);
                SingleMemberAnnotationExpr toArguments = findOrCreateRepeatableWrapperAnnotation(method, "AssignReturned.ToArguments");
                ArrayInitializerExpr toArgumentsInitializerList = (ArrayInitializerExpr) (toArguments).getMemberValue();

                Name annoName = new Name("ToArgument");
                IntegerLiteralExpr argIndexExpr = new IntegerLiteralExpr(String.valueOf(argIndex));
                if (index == -1) {
                    toArgumentsInitializerList.getValues().add(new SingleMemberAnnotationExpr(annoName, argIndexExpr));
                } else {
                    toArgumentsInitializerList.getValues().add(new NormalAnnotationExpr(
                            annoName, new NodeList<>(
                            new MemberValuePair("value", argIndexExpr),
                            new MemberValuePair("index", new IntegerLiteralExpr(String.valueOf(index)))
                    )));
                }
            }));
        }
        return returns;
    }

    private ValueToReturn<?> transformReturnValueAssignment(Parameter returnValueParameter) {
        AnnotationExpr assignReturnedExpr = Utils.getAnnotation(returnValueParameter, ADVICE_RETURN, typeSolver).get();
        assignReturnedExpr.replace(Utils.removeAnnotationArgumentValue(assignReturnedExpr, "readOnly"));

        return new ValueToReturn<>(returnValueParameter, (method, index) -> {
            Utils.addImports(method, Advice.AssignReturned.class);
            Name annoName = new Name("AssignReturned.ToReturned");
            AnnotationExpr result;
            if (index == -1) {
                result = new MarkerAnnotationExpr(annoName);
            } else {
                result = new NormalAnnotationExpr(
                        annoName, new NodeList<>(
                        new MemberValuePair("index", new IntegerLiteralExpr(String.valueOf(index)))
                ));
            }
            insertBeforeMethodEnterOrExitAnnotation(method, result);
        });
    }

    private SingleMemberAnnotationExpr findOrCreateRepeatableWrapperAnnotation(MethodDeclaration method, String wrapperAnnotationName) {
        SingleMemberAnnotationExpr toArguments = method.getAnnotations().stream()
                .filter(existing -> existing.getNameAsString().equals(wrapperAnnotationName))
                .map(SingleMemberAnnotationExpr.class::cast)
                .findFirst()
                .orElseGet(() -> {
                    SingleMemberAnnotationExpr toArgs = new SingleMemberAnnotationExpr(new Name(wrapperAnnotationName), new ArrayInitializerExpr());
                    insertBeforeMethodEnterOrExitAnnotation(method, toArgs);
                    return toArgs;
                });
        return toArguments;
    }

    private void insertBeforeMethodEnterOrExitAnnotation(MethodDeclaration method, AnnotationExpr toInsert) {
        NodeList<AnnotationExpr> methodAnnotations = method.getAnnotations();
        //Insert in order before OnMethodExit or OnMethodEnter
        int indexToInsert = methodAnnotations.size();
        for (int i = 0; i < methodAnnotations.size(); i++) {
            String annoName = methodAnnotations.get(i).getNameAsString();
            if (annoName.endsWith("OnMethodExit") || annoName.endsWith("OnMethodEnter")) {
                indexToInsert = i;
            }
        }
        methodAnnotations.add(indexToInsert, toInsert);
    }


    private List<ValueToReturn<Parameter>> transformFieldAssignments(List<Parameter> fieldAssignmentParameters) {
        return fieldAssignmentParameters.stream()
                .map(writtenField -> {
                    AnnotationExpr anno = Utils.getAnnotation(writtenField, ADVICE_FIELDVALUE, typeSolver).get();
                    Expression fieldNameExpression = Utils.extractAnnotationArgumentValue(anno, "value").get();
                    anno.replace(Utils.removeAnnotationArgumentValue(anno, "readOnly"));


                    return new ValueToReturn<>(writtenField, (method, index) -> {
                        Utils.addImports(method, Advice.AssignReturned.class, Advice.AssignReturned.ToFields.ToField.class);

                        SingleMemberAnnotationExpr toFields = findOrCreateRepeatableWrapperAnnotation(method, "AssignReturned.ToFields");
                        ArrayInitializerExpr toFieldsInitializerList = (ArrayInitializerExpr) (toFields).getMemberValue();

                        AnnotationExpr result;
                        Name annoName = new Name("ToField");
                        if (index == -1) {
                            result = new SingleMemberAnnotationExpr(annoName, fieldNameExpression);
                        } else {
                            result = new NormalAnnotationExpr(
                                    annoName, new NodeList<>(
                                    new MemberValuePair("value", fieldNameExpression),
                                    new MemberValuePair("index", new IntegerLiteralExpr(String.valueOf(index)))
                            ));
                        }
                        toFieldsInitializerList.getValues().add(result);
                    });
                })
                .toList();
    }


    private void removeParameterOrVariableIfNeverUsed(MethodDeclaration method, Node paramOrVarDeclaration) {
        AtomicBoolean isUsed = new AtomicBoolean(false);
        String paramOrVarName;
        if (paramOrVarDeclaration instanceof Parameter param) {
            paramOrVarName = param.getNameAsString();
        } else {
            paramOrVarName = ((VariableDeclarator) paramOrVarDeclaration).getNameAsString();
        }
        new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(NameExpr name, Void arg) {
                super.visit(name, arg);
                if (name.getNameAsString().equals(paramOrVarName) && !Utils.isLHSOfAssignment(name)) {
                    SymbolReference<? extends ResolvedValueDeclaration> result =
                            JavaParserFacade.get(typeSolver).solve((name));
                    if (result.isSolved()) {
                        ResolvedValueDeclaration correspondingDeclaration = result.getCorrespondingDeclaration();
                        if (correspondingDeclaration instanceof JavaParserParameterDeclaration paramDecl
                            && paramDecl.getWrappedNode() == paramOrVarDeclaration) {
                            isUsed.set(true);
                        } else if (correspondingDeclaration instanceof JavaParserVariableDeclaration varDecl
                                   && varDecl.getWrappedNode().getVariable(0) == paramOrVarDeclaration) {
                            isUsed.set(true);
                        }
                    }
                }
            }
        }.visit(method, null);
        if (!isUsed.get()) {
            if (paramOrVarDeclaration instanceof VariableDeclarator) {
                VariableDeclarationExpr containingExpression = (VariableDeclarationExpr) paramOrVarDeclaration.getParentNode().get();
                ExpressionStmt containingStatement = (ExpressionStmt) containingExpression.getParentNode().get();
                containingStatement.remove();
            } else {
                paramOrVarDeclaration.remove();
            }
        }
    }

    private void replaceReturnValueAndAddAnnotations(MethodDeclaration method, List<ValueToReturn<?>> exitReturnValues) {
        if (exitReturnValues.isEmpty()) {
            return;
        }
        for (int i = 0; i < exitReturnValues.size(); i++) {
            ValueToReturn<?> val = exitReturnValues.get(i);
            int index = exitReturnValues.size() == 1 ? -1 : i;
            if (val.annoGenerator() != null) {
                val.annoGenerator().addAnnotation(method, index);
            }
        }

        Expression returnExpression;
        Type returnType;
        if (exitReturnValues.size() == 1) {
            NodeWithSimpleName<?> returnedValue = exitReturnValues.get(0).parameterOrVariable();
            returnExpression = returnedValue.getNameAsExpression();
            returnType = ((NodeWithType<?, ?>) returnedValue).getType();
        } else {
            //return value is new Object[]{val1, val2, ...}
            NodeList<Expression> returnValueNames = exitReturnValues.stream()
                    .map(value -> value.parameterOrVariable().getNameAsExpression())
                    .collect(Collectors.toCollection(NodeList::new));
            Type objectType = StaticJavaParser.parseType("Object");
            returnExpression = new ArrayCreationExpr(objectType, new NodeList<>(new ArrayCreationLevel()), new ArrayInitializerExpr(returnValueNames));
            returnType = new ArrayType(objectType);
        }

        method.setType(returnType);
        BlockStmt methodBody = method.getBody().get();
        // first insert a return at the end of the body if there is none, then replace all return values
        // this ensures early-out returns are adjusted properly
        Optional<Statement> lastStatement = methodBody.getStatements().getLast();
        if (lastStatement.isEmpty() || !(lastStatement.get() instanceof ReturnStmt)) {
            methodBody.addAndGetStatement(new ReturnStmt());
        }
        replaceReturnValues(methodBody, returnExpression);
    }

    private void replaceReturnValues(BlockStmt methodBody, Expression returnExpression) {
        List<ReturnStmt> modifiedReturns = new ArrayList<>();
        new ScopedReturnVisitor<Void>() {
            @Override
            public void visit(ReturnStmt returnStmt, Void arg) {
                returnStmt.setExpression(returnExpression.clone());
                modifiedReturns.add(returnStmt);
            }
        }.visit(methodBody, null);
        modifiedReturns.forEach(AdviceTransformationPlan::simplifyReturnStatement);
    }

    private static void simplifyReturnStatement(ReturnStmt returnStmt) {
        Node parent = returnStmt.getParentNode().get();
        if (parent instanceof BlockStmt) {
            BlockStmt containingBlock = (BlockStmt) parent;

            List<Comment> additionalOrphanComments = new ArrayList<>();
            List<NameExpr> returnedVariables = new ArrayList<>();
            returnStmt.getExpression().get().walk(NameExpr.class, returnedVariables::add);
            boolean continueOptimizing;
            do {
                continueOptimizing = false;
                NodeList<Statement> statements = containingBlock.getStatements();
                int prevStatementIndex = statements.indexOf(returnStmt) - 1;
                if (prevStatementIndex >= 0) {
                    Statement prevStatement = statements.get(prevStatementIndex);
                    if (prevStatement instanceof ExpressionStmt expressionStmt
                        && expressionStmt.getExpression() instanceof AssignExpr assignExpr
                        && assignExpr.getTarget() instanceof NameExpr assignedVar
                        && assignExpr.getOperator() == AssignExpr.Operator.ASSIGN) {
                        String assignedName = assignedVar.getNameAsString();
                        List<NameExpr> matchingReturnValues = returnedVariables.stream()
                                .filter(expr -> expr.getNameAsString().equals(assignedName))
                                .toList();

                        if (matchingReturnValues.size() == 1) {
                            NameExpr replacedReturnExpression = matchingReturnValues.get(0);
                            Set<String> otherVarNames = returnedVariables.stream()
                                    .filter(name -> name != replacedReturnExpression)
                                    .map(name -> name.getNameAsString())
                                    .collect(Collectors.toSet());

                            AtomicBoolean valueReferencesAnyOtherReturnValue = new AtomicBoolean(false);
                            assignExpr.getValue().walk(NameExpr.class, name -> {
                                if ( otherVarNames.contains(name.getNameAsString()) ) {
                                    valueReferencesAnyOtherReturnValue.set(true);
                                }
                            });
                            if (!valueReferencesAnyOtherReturnValue.get()) {

                                if (prevStatement.getComment().isPresent()) {
                                    Comment comment = prevStatement.getComment().get();
                                    additionalOrphanComments.add(0, comment);
                                }
                                prevStatement.remove();
                                replacedReturnExpression.replace(assignExpr.getValue());
                                returnedVariables.remove(replacedReturnExpression);
                                continueOptimizing = true;
                            }
                        }
                    }
                }
            } while (continueOptimizing);
            additionalOrphanComments.forEach(returnStmt::addOrphanComment);
        }
    }

}
