## What is it?

This project provides a standalone, CLI utility for automatically transforming [ByteBuddy Advice classes](https://javadoc.io/doc/net.bytebuddy/byte-buddy/latest/index.html) in source code from inlined advice to delegating advice. This is especially relevant with the OpenTelemetry Java agent moving to invokedynamic-based, delegating advice weaving instead of inlining.

This tool is implemented using the awesome lexically preserving printing feature of [javaparser](https://github.com/javaparser/javaparser). This means your source will remain untouched, except for the lines modified by the tool.

## How to use it

You don't even need to check out this repository. Simply run the following command with docker installed on your system:

```
docker run --rm -v <dir-to-transform>:/srcdir ghcr.io/jonaskunz/indy-advice-transformer:latest
```

Replace `<dir-to-transform>` with the directory containing the source files you want to transform.
The tool will traverse that directory looking for `*.java` files and transform them if they contain bytebuddy advice annotations.

Alternatively you can checkout this repository to build and run the tool yourself.

## Performed transformations

This tool automatically performs the transformations described in the [OpenTelemetry guide](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/contributing/writing-instrumentation-module.md#use-non-inlined-advice-code-with-invokedynamic).

### Assignments

ByteBuddy advice classes allow you to perform assignments to fields, parameters or the return values of the instrumented method.
When using inlining, this is usually done by assigning values to advice parameters and setting the annotation parameter `readOnly` to false. When using delegating advice, the value to assign needs to be returned from the advice class instead and ByteBuddy needs to be instructed what to do with it using the [Advice.AssignReturned](https://javadoc.io/static/net.bytebuddy/byte-buddy/1.17.2/net/bytebuddy/asm/Advice.AssignReturned.html) annotations.

Here is an example diff of a performed transformation:

```diff
public class WriteOnlyReturn {

+     @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
-     public static void overrideReturn(@Advice.Return(readOnly = false) String instrumetedReturn, @Advice.Thrown Throwable error) {
+     public static String overrideReturn(@Advice.Return String instrumetedReturn, @Advice.Thrown Throwable error) {
        if (error != null) {
-             instrumetedReturn = "foo";
-             return;
+             return "foo";
        }
-        instrumetedReturn = instrumetedReturn + "bar";
+        return instrumetedReturn + "bar";
    }

}
```

### Advice Local Variables

With inlined advice, it is possible to pass state from the method enter callback to the exit callback using local variables in the instrumented method via [@Advice.Local](https://javadoc.io/static/net.bytebuddy/byte-buddy/1.17.2/net/bytebuddy/asm/Advice.Local.html). This is not possible for delegating advice, the state needs to be passed as a return value from the enter callback and looked up in the exit callback using [@Advice.Enter](https://javadoc.io/static/net.bytebuddy/byte-buddy/1.17.2/net/bytebuddy/asm/Advice.Enter.html).

The tool replaces `@Advice.Local` usages correspondingly. If multiple local variables are used, a helper class is generated to package them as a single return value.

Here is an example of this transformation:

``` diff
public class SingleLocalAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
-     public static void enter(@Advice.Local("fooName") String fooLocal) {
-         fooLocal = "Hello World";
+     public static String enter() {
+        return "Hello World";
    }

+     @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
-     public static void overrideReturn(@Advice.Return(readOnly = false) String returnVal,
-                                       @Advice.Local("fooName") String fooLocal) {
+     public static String overrideReturn(@Advice.Enter String fooLocal
+     ) {
-         returnVal = fooLocal;
+         return fooLocal;
    }

}
```

### Packaging multiple return values

Of course, multiple of the transformations can be required at the same time, but an advice callback can only have a single return value. For this reason, bytebuddy supports wrapping the values in an array and unpacking the values from it to make use of them. The tool makes use of this feature automatically when necessary:

``` diff
public class WriteFieldAndReturn {

+     @AssignReturned.ToReturned(index = 0)
+     @AssignReturned.ToFields({ @ToField(value = "myOtherField", index = 1) })
    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
-     public static void overrideReturn(@Advice.Return(readOnly = false) String instrumetedReturn,
-                                       @Advice.FieldValue(value = "myOtherField", readOnly = false) String stringField) {
-         stringField = "Hello World";
-         instrumetedReturn = "foobar";
+     public static Object[] overrideReturn() {
+         return new Object[] { "foobar", "Hello World" };
    }
}
```

## Caveats

This is not a fire-and-forget tool. It is an assistant.
While the tool performs some very basic code style optimizations on the modified code, it doesn't do comprehensive static code analysis. So you'll have to manually review the generated code and e.g. eliminate dead code yourself.
However, for simple advice classes you'll likely have to just adjust the formatting to your liking.

