import net.bytebuddy.asm.Advice;

public class SingleLocalAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enter(@Advice.Local("fooName") String fooLocal) {
        fooLocal = "Hello World";
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void overrideReturn(@Advice.Return(readOnly = false) String returnVal,
                                      @Advice.Local("fooName") String fooLocal) {

        returnVal = fooLocal;
    }

}
