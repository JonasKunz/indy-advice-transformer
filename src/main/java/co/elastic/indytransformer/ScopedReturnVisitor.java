package co.elastic.indytransformer;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public abstract class ScopedReturnVisitor<A> extends VoidVisitorAdapter<A> {

    @Override
    public abstract void visit(ReturnStmt returnStmt, A arg);


    @Override
    public void visit(ClassOrInterfaceDeclaration n, A arg) {
        // do not descend further (not calling super here)
    }

    @Override
    public void visit(ObjectCreationExpr n, A arg) {
        // do not descend further (not calling super here)
    }

    @Override
    public void visit(LambdaExpr n, A arg) {
        // do not descend further (not calling super here)
    }
}
