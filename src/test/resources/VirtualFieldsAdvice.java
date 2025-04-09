import io.opentelemetry.instrumentation.api.util.VirtualField;
import net.bytebuddy.asm.Advice;
import java.util.concurrent.Callable;

public class VirtualFieldsAdvice {

  @Advice.OnMethodEnter()
  public static void onEnter() {
    VirtualField.find(Runnable.class, Integer.class);
    VirtualField.find(Callable.class, Long.class);
    VirtualField.find(FooBar.class, Baz.class);
  }
  @Advice.OnMethodExit()
  public static void onExit() {
    VirtualField.find(Runnable.class, Integer.class);
    VirtualField.find(Callable.class, String.class);
  }
}