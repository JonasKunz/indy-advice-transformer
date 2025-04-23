import net.bytebuddy.asm.Advice;

public class SingleLocalAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enterSchedule(
            @Advice.Argument(value = 2, readOnly = false) Runnable runnable) {
        runnable = AkkaSchedulerTaskWrapper.wrap(runnable);
    }

}
