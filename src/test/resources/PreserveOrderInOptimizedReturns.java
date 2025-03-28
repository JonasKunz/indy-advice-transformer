import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.function.Supplier;

import static net.bytebuddy.matcher.ElementMatchers.named;


public class MyAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enter(@Advice.FieldValue(value = "field0", readOnly = false) String field0,
                             @Advice.FieldValue(value = "myField", readOnly = false) String field1,
                             @Advice.FieldValue(value = "myOtherField", readOnly = false) String field2
    ) {
        field2 = "foo";
        field1 = "bar" + field2;
        field0 = "42";
    }

}

