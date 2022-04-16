package io.opentelemetry.javaagent.instrumentation.sling.api.common;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class RequestProgressTrackerInstrumentation implements TypeInstrumentation {
  private final String logAdviceClassName;
  private final String startTimerAdviceClassName;
  private final String logTimerAdviceClassName;

  public RequestProgressTrackerInstrumentation(String logAdviceClassName,
      String startTimerAdviceClassName, String logTimerAdviceClassName) {
    this.logAdviceClassName = logAdviceClassName;
    this.startTimerAdviceClassName = startTimerAdviceClassName;
    this.logTimerAdviceClassName = logTimerAdviceClassName;
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.sling.api.request.RequestProgressTracker");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.apache.sling.api.request.RequestProgressTracker"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("log")
            .and(takesArgument(0, named("java.lang.String")))
            .and(isPublic()),
        logAdviceClassName);
    transformer.applyAdviceToMethod(
        named("startTimer")
            .and(takesArgument(0, named("java.lang.String")))
            .and(isPublic()),
        startTimerAdviceClassName);
    transformer.applyAdviceToMethod(
        named("logTimer")
            .and(takesArgument(0, named("java.lang.String")))
            .and(isPublic()),
        logTimerAdviceClassName);
  }
}
