import net.bytebuddy.asm.Advice;

public class EnterAndLocalsCombined {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static int enter(@Advice.Local("fooName") String fooLocal) {
        fooLocal = "Hello World";
        return 42;
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void overrideReturn(@Advice.Return(readOnly = false) String returnVal,
                                      @Advice.Local("fooName") String fooLocal,
                                      @Advice.Enter int enter) {

        returnVal = fooLocal + enter;
    }

}
