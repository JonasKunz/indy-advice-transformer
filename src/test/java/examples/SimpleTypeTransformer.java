package examples;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class SimpleTypeTransformer implements TypeInstrumentation {

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
        //Some comment
        return named("foo.bar.Baz");
        //Some unnecessary whitespaces below we still want to preserve

    }

    @Override
    public void transform(TypeTransformer typeTransformer) {

    }

    public static class WriteOnlyReturn {

        //blabla
        //more blabla

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void overrideReturn(@Advice.Return(readOnly = false) String instrumetedReturn, @Advice.Thrown Throwable error) {
            //Some clarification comments
            if (error != null) {
                instrumetedReturn = "foo";
            } else {
                instrumetedReturn = "bar";
            }
        }

    }

}
