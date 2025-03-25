package examples;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.function.Supplier;

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

    public static class WriteOnlyReturn {

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

    public static class ReadWriteReturn {

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

    public static class WriteFieldAndReutnr {

        //blabla
        //more blabla

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void overrideReturn(@Advice.Return(readOnly = false) String instrumetedReturn,
                                          @Advice.FieldValue(value = "myField", readOnly = false) Class<?> classField,
                                          @Advice.FieldValue(value = "myOtherField", readOnly = false) String stringField) {

            stringField = "Hello World";
            //Some multiline
            //clarification comments
            instrumetedReturn = "foobar";
            //another clarification
            classField = String.class;
        }

    }


    public static class ComplexEnter {

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

            stringField = "Hello World"+fooLocal+barLocal;
            //Some multiline
            //clarification comments
            instrumetedReturn = "foobar";
            //another clarification
            classField = String.class;
        }

    }


    public static class SingleLocalAdvice {

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


    public static class EnterAndLocalsCombined {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static int enter(@Advice.Local("fooName") String fooLocal) {
            fooLocal = "Hello World";
            return 42;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void overrideReturn(@Advice.Return(readOnly = false) String returnVal,
                                          @Advice.Local("fooName") String fooLocal,
                                          @Advice.Enter int enter) {

            returnVal = fooLocal+enter;
        }

    }



    public static class EnterAndAssignmentsCombined {

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

}
