package co.elastic.indytransformer;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

public class TransformTestSources {

    AdviceTransformer transformer = new AdviceTransformer();

    private void transformAndPrintDiff(String resourceName) {
        String originalContent;
        try (InputStream source = TransformTestSources.class.getResourceAsStream(resourceName)) {
            originalContent = new String(source.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (InputStream source = TransformTestSources.class.getResourceAsStream(resourceName)) {
            CompilationUnit cu = transformer.load(source);
            transformer.transform(cu);
            String transformed = transformer.print(cu);
            printDiff(originalContent, transformed);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void printDiff(String originalContent, String transformed) {

        List<String> originalLines = Arrays.asList(originalContent.split("\n", -1));
        List<String> transformedLines = Arrays.asList(transformed.split("\n", -1));

        Patch<String> patch = DiffUtils.diff(originalLines, transformedLines, true);
        for (AbstractDelta<String> delta : patch.getDeltas()) {
            switch (delta.getType()) {
                case EQUAL:
                    for (String line : delta.getSource().getLines()) {
                        System.out.println(line);
                    }
                    break;
                case DELETE, INSERT, CHANGE:
                    for (String line : delta.getSource().getLines()) {
                        System.out.println("- " + line);
                    }
                    for (String line : delta.getTarget().getLines()) {
                        System.out.println("+ " + line);
                    }
                    break;
            }
        }
    }

    @Test
    public void nestedClass() {
        transformAndPrintDiff("/NestedClass.java");
    }

    @Test
    public void readWriteReturn() {
        transformAndPrintDiff("/ReadWriteReturn.java");
    }

    @Test
    public void singleLocal() {
        transformAndPrintDiff("/SingleLocalAdvice.java");
    }

    @Test
    public void writeFieldAndReturn() {
        transformAndPrintDiff("/WriteFieldAndReturn.java");
    }

    @Test
    public void writeOnlyReturn() {
        transformAndPrintDiff("/WriteOnlyReturn.java");
    }

    @Test
    public void enterAndAssignmentsCombined() {
        transformAndPrintDiff("/EnterAndAssignmentsCombined.java");
    }

    @Test
    public void enterAndLocalsCombined() {
        transformAndPrintDiff("/EnterAndLocalsCombined.java");
    }

    @Test
    public void complexEnter() {
        transformAndPrintDiff("/ComplexEnter.java");
    }

    @Test
    public void preserveAssignmentOrderInReturn() {
        transformAndPrintDiff("/PreserveOrderInOptimizedReturns.java");
    }

}
