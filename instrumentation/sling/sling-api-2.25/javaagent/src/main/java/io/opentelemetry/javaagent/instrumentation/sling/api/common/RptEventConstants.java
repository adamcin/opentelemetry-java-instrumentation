package io.opentelemetry.javaagent.instrumentation.sling.api.common;

public final class RptEventConstants {

  public static final String EVT_NAME_LOG = "rpt.log";
  public static final String EVT_NAME_START_TIMER = "rpt.stm";
  public static final String EVT_NAME_LOG_TIMER = "rpt.ltm";

  public static final String EVT_ATTR_TIMER = "timr";
  public static final String EVT_ATTR_MESSAGE = "mesg";
  public static final String EVT_ATTR_ARGS = "args";

  private RptEventConstants() {
    // no construction
  }
}
