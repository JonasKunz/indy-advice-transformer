package com.github.javaparser.printer.lexicalpreservation;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;

public class GeneratedClassCleaner {

    /**
     * The printer by default generates empty lines between field declarations - we don't want those.
     * Therefore we simply scan the text for back-to-back newlines and remove them.
     * @param clazz the generated class to cleanup the generated text for
     */
    public static void cleanup(ClassOrInterfaceDeclaration clazz) {
        LexicalPreservingPrinter.print(clazz);
        NodeText data = clazz.getData(LexicalPreservingPrinter.NODE_TEXT_DATA);
        for (int i=1; i<data.getElements().size(); i++) {
            TextElement prev = data.getTextElement(i-1);
            TextElement curr = data.getTextElement(i);
            if (curr.isNewline() && prev.isNewline()) {
                data.removeElement(i);
                i--;
            }
        }
    }
}
