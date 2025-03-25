package co.elastic.indytransformer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.JavaParserAdapter;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class AdviceTransformer {

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

    public boolean transform(Path file) {
        try (InputStream stream = Files.newInputStream(file)) {
            CompilationUnit compilationUnit = load(stream);
            boolean wasTransformed = transform(compilationUnit);
            if (wasTransformed) {
                Files.writeString(file, print(compilationUnit));
                return true;
            }
            return false;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read file", e);
        }
    }

    public String print(CompilationUnit compilationUnit) {
        return LexicalPreservingPrinter.print(compilationUnit);
    }

    public CompilationUnit load(InputStream file) {
        CompilationUnit compilationUnit = JavaParserAdapter.of(parser).parse(file);
        LexicalPreservingPrinter.setup(compilationUnit);
        return compilationUnit;
    }

    public boolean transform(CompilationUnit input) {
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
