import net.bytebuddy.asm.Advice;

public class EnterAndAssignmentsCombined {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static int enter(@Advice.FieldValue(readOnly = false, value = "foo") String fooField) {
        fooField = "Hello World";
        return 42;
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void overrideReturn(@Advice.Return(readOnly = false) String returnVal,
                                      @Advice.Enter int enter) {

        returnVal = "Hello " + enter;
    }

}
