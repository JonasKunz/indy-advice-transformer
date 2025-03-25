import net.bytebuddy.asm.Advice;

public class ComplexEnter {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enter(@Advice.Argument(value = 42, readOnly = false) String arg1,
                             @Advice.Argument(value = 7, readOnly = false) int arg2,
                             @Advice.FieldValue(value = "myField", readOnly = false) Class<?> classField,
                             @Advice.FieldValue(value = "myOtherField", readOnly = false) String stringField,
                             @Advice.Local("fooName") String fooLocal,
                             @Advice.Local("barName") Number barLocal) {

        Object test = new Object();

        barLocal = 42;
        fooLocal = "Number is " + barLocal;

        arg1 = arg2 + "foobar";
        arg2 = 7;
        stringField = "Hello World";
        classField = String.class;
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void overrideReturn(@Advice.Return(readOnly = false) String instrumetedReturn,
                                      @Advice.FieldValue(value = "myField", readOnly = false) Class<?> classField,
                                      @Advice.FieldValue(value = "myOtherField", readOnly = false) String stringField,
                                      @Advice.Local("fooName") String fooLocal,
                                      @Advice.Local("barName") Number barLocal) {

        stringField = "Hello World" + fooLocal + barLocal;
        //Some multiline
        //clarification comments
        instrumetedReturn = "foobar";
        //another clarification
        classField = String.class;
    }

}
