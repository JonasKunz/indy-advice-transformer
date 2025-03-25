package co.elastic.indytransformer;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.nodeTypes.NodeWithType;

public record ValueToReturn<T extends Node & NodeWithSimpleName<?> & NodeWithType<?, ?>>(T parameterOrVariable,
                                                                                         MethodAnnotationgGenerator annoGenerator) {
    interface MethodAnnotationgGenerator {
        void addAnnotation(MethodDeclaration method, int returnValueIndex);
    }
}
