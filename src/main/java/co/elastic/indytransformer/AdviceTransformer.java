package co.elastic.indytransformer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.JavaParserAdapter;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.visitor.GenericListVisitorAdapter;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.configuration.PrettyPrinterConfiguration;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.resolution.Context;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedAnnotationDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedTypeDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFactory;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import net.bytebuddy.asm.Advice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static co.elastic.indytransformer.Utils.hasAnnotation;

public class AdviceTransformer {

    private static final String ADVICE_ON_METHOD_ENTER = Advice.OnMethodEnter.class.getCanonicalName();
    private static final String ADVICE_ON_METHOD_EXIT = Advice.OnMethodExit.class.getCanonicalName();

    private static final Logger LOG = LoggerFactory.getLogger(AdviceTransformer.class);

    private final TypeSolver typeSolver;
    private final JavaParser parser;

    public AdviceTransformer() {
        typeSolver = new ReflectionTypeSolver(true) {
            @Override
            protected boolean filterName(String name) {
                return super.filterName(name) || name.startsWith("net.bytebuddy");
            }
        };
        parser = new JavaParser(new ParserConfiguration()
                .setSymbolResolver(new JavaSymbolSolver(typeSolver)));
    }

    public CompilationUnit transform(Path file, boolean overrideFile) {
        try {
            CompilationUnit compilationUnit = JavaParserAdapter.of(parser).parse(file);
            LexicalPreservingPrinter.setup(compilationUnit);
            boolean wasTransformed = transform(compilationUnit);
            if (overrideFile && wasTransformed) {
                Files.writeString(file, LexicalPreservingPrinter.print(compilationUnit));
            }
            return compilationUnit;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read file", e);
        }
    }

    private boolean transform(CompilationUnit input) {
        AtomicBoolean anyTransformation = new AtomicBoolean(false);
        new ModifierVisitor<Void>() {
            @Override
            public ClassOrInterfaceDeclaration visit(ClassOrInterfaceDeclaration declaration, Void arg) {
                super.visit(declaration, arg);
                Optional<AdviceTransformationPlan> adviceTransformationPlan = AdviceTransformationPlan.create(declaration, typeSolver);
                if (adviceTransformationPlan.isPresent()) {
                    if (adviceTransformationPlan.get().transform()) {
                        anyTransformation.set(true);
                    }
                    return declaration;
                }
                return declaration;
            }
        }.visit(input, null);
        return anyTransformation.get();
    }

}
