package io.opentelemetry.javaagent.instrumentation.sling.api.v2_25;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.javaagent.instrumentation.sling.api.common.RptEventConstants;
import net.bytebuddy.asm.Advice;

import java.util.Arrays;


@SuppressWarnings("unused")
public class RptLogTimerAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
  public static void onEnter(@Advice.Argument(0) String name,
      @Advice.Argument(value = 1, optional = true) String message,
      @Advice.Argument(value = 2, optional = true) Object... args) {

    Span span = Span.current();
    if (span.isRecording()) {
      AttributesBuilder attrs = Attributes.builder().put(RptEventConstants.EVT_ATTR_TIMER, name);
      if (message != null) {
        attrs.put(RptEventConstants.EVT_ATTR_MESSAGE, message);
        if (args != null) {
          attrs.put(RptEventConstants.EVT_ATTR_ARGS,
              Arrays.stream(args).map(String::valueOf).toArray(String[]::new));
        }
      }
      span.addEvent(RptEventConstants.EVT_NAME_LOG_TIMER, attrs.build());
    }
  }
}
