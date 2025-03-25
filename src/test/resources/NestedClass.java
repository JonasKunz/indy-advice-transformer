import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.function.Supplier;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class NestedClass implements TypeInstrumentation {

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
        //Some comment
        return named("foo.bar.Baz");
        //Some unnecessary whitespaces below we still want to preserve

    }

    @Override
    public void transform(TypeTransformer typeTransformer) {

    }

    public static class SimpleWriteReturn {

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void overrideReturn(@Advice.Return(readOnly = false) String instrumetedReturn, @Advice.Thrown Throwable error) {
            //Some nested code whose return values MUST NOT be changed

            Supplier<String> myLambda = () -> {
                return "bar";
            };
            Object myAnonym = new Object() {
                @Override
                public String toString() {
                    return "bar";
                }
            };
            class localClass {
                @Override
                public String toString() {
                    return "bar";
                }
            }
            instrumetedReturn = "foo";
        }

    }


}
