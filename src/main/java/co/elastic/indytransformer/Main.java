package co.elastic.indytransformer;

import com.github.javaparser.ast.CompilationUnit;
import org.checkerframework.checker.units.qual.A;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

public class Main {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("The directory to transform is required as program argument");
            System.exit(-1);
        }

        Path rootDir = Paths.get(args[0]);

        List<Path> javaFiles;
        try (Stream<Path> fileStream = Files.walk(rootDir)) {
            javaFiles = fileStream
                    .filter(file -> file.toString().endsWith(".java"))
                    .toList();
        };

        System.out.println("Found "+javaFiles.size()+" java source files in directory "+rootDir);

        AdviceTransformer transformer = new AdviceTransformer();
        int transformed = 0;
        for (Path javaFile : javaFiles) {
            String relativePath = rootDir.relativize(javaFile).toString();
            try {
                if (transformer.transform(javaFile)) {
                    transformed++;
                    System.out.println("Transformed Advice class(es) in "+relativePath);
                }
            } catch (Exception e) {
                System.out.println("Failed to transform "+relativePath);
                e.printStackTrace();
            }
        }

        System.out.println("Successfully transformed "+transformed+" java source files");
    }
}
