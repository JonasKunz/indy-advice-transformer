package co.elastic.indytransformer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TransformTestSources {

    @Test
    public void transform() throws IOException {
        Files.list(Paths.get("src/test/java/examples"))
                .forEach(System.out::println);
    }
}
