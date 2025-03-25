package co.elastic.indytransformer;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TransformTestSources {

    @Test
    public void transform() throws IOException {
        Files.list(Paths.get("src/test/java/examples"))
                .forEach(file -> {
                    CompilationUnit transformed = new AdviceTransformer().transform(file, false);
                    System.out.println("LEXICALLY PRESERVER ENDRESULT-------------");
                    System.out.println(LexicalPreservingPrinter.print(transformed));
                });
    }

    @Test
    public void transformAgent() throws IOException {
        Files.walk(Paths.get("/Users/jonas/git/otel/opentelemetry-java-instrumentation/instrumentation"))
                .filter(file -> file.toString().endsWith(".java"))
                .forEach(file -> {
                    System.out.println("Transforming "+file);
                    try {
                        CompilationUnit transformed = new AdviceTransformer().transform(file, true);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
    }
}
