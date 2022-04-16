package io.opentelemetry.javaagent.instrumentation.sling.api.v2_25;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.javaagent.instrumentation.sling.api.common.RptEventConstants;
import net.bytebuddy.asm.Advice;


@SuppressWarnings("unused")
public class RptStartTimerAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(@Advice.Argument(0) String name) {
    Span span = Span.current();
    if (span.isRecording()) {
      span.addEvent(RptEventConstants.EVT_NAME_START_TIMER,
          Attributes.builder().put(RptEventConstants.EVT_ATTR_TIMER, name).build());
    }
  }
}
