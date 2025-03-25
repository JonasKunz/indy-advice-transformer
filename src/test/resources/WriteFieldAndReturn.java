import net.bytebuddy.asm.Advice;

public class WriteFieldAndReturn {

    //blabla
    //more blabla

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void overrideReturn(@Advice.Return(readOnly = false) String instrumetedReturn,
                                      @Advice.FieldValue(value = "myField", readOnly = false) Class<?> classField,
                                      @Advice.FieldValue(value = "myOtherField", readOnly = false) String stringField) {

        stringField = "Hello World";
        //Some multiline
        //clarification comments
        instrumetedReturn = "foobar";
        //another clarification
        classField = String.class;
    }

}
