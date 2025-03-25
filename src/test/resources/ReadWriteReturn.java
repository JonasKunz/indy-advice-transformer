import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public class ReadWriteReturn {

    //blabla
    //more blabla

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void overrideReturn(@Advice.Return(typing = Assigner.Typing.DYNAMIC, readOnly = false) String instrumetedReturn, @Advice.Thrown Throwable error) {
        //Some clarification comments
        if (error != null) {
            instrumetedReturn = instrumetedReturn + "foo";
            return;
        } else {
            instrumetedReturn = "bar";
        }
    }

}
