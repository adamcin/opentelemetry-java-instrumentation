/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.sling.api.v2_25;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.sling.api.common.RequestProgressTrackerInstrumentation;
import java.util.Collections;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class SlingApiInstrumentationModule extends InstrumentationModule {

  public SlingApiInstrumentationModule() {
    super("sling", "sling-api-2.25");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("org.apache.sling.api.request.RequestProgressTracker");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Collections.singletonList(new RequestProgressTrackerInstrumentation(
        SlingApiInstrumentationModule.class.getPackage().getName() + ".RptLogAdvice",
        SlingApiInstrumentationModule.class.getPackage().getName() + ".RptStartTimerAdvice",
        SlingApiInstrumentationModule.class.getPackage().getName() + ".RptLogTimerAdvice"));
  }
}
