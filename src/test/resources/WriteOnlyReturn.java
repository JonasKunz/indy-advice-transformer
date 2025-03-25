import net.bytebuddy.asm.Advice;

public class WriteOnlyReturn {

    //blabla
    //more blabla

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void overrideReturn(@Advice.Return(readOnly = false) String instrumetedReturn, @Advice.Thrown Throwable error) {
        //Some clarification comments
        if (error != null) {
            instrumetedReturn = "foo";
            return;
        } else {
            instrumetedReturn = "bar";
        }
        return;
    }

}
