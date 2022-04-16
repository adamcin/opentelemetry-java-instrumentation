import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.javaagent.instrumentation.sling.api.common.RptEventConstants
import org.apache.sling.api.request.RequestProgressTracker
import org.apache.sling.api.request.builder.Builders

class RequestProgressTrackerTest extends AgentInstrumentationSpecification {

  def "record log messages"() {
    setup:
    RequestProgressTracker tracker = Builders.newRequestProgressTracker()

    runWithSpan("foo") {
      tracker.log("some event message")
      tracker.log("some formatted message {0}", "suffix")
      tracker.log("some formatted message {0}", "arg1", 2)
      tracker.startTimer("a timer")
      tracker.startTimer("another timer")
      tracker.logTimer("a timer")
      tracker.logTimer("a timer", "a message")
      tracker.logTimer("a timer", "a formatted message {0}", "arg1", 2)
    }

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "foo"
          kind SpanKind.INTERNAL
          event(0) {
            eventName "${RptEventConstants.EVT_NAME_LOG}"
            attributes {
              "${RptEventConstants.EVT_ATTR_MESSAGE}" "some event message"
            }
          }
          event(1) {
            eventName "${RptEventConstants.EVT_NAME_LOG}"
            attributes {
              "${RptEventConstants.EVT_ATTR_MESSAGE}" "some formatted message {0}"
              "${RptEventConstants.EVT_ATTR_ARGS}" new String[]{ "suffix" }
            }
          }
          event(2) {
            eventName "${RptEventConstants.EVT_NAME_LOG}"
            attributes {
              "${RptEventConstants.EVT_ATTR_MESSAGE}" "some formatted message {0}"
              "${RptEventConstants.EVT_ATTR_ARGS}" new String[]{ "arg1", "2" }
            }
          }
          event(3) {
            eventName "${RptEventConstants.EVT_NAME_START_TIMER}"
            attributes {
              "${RptEventConstants.EVT_ATTR_TIMER}" "a timer"
            }
          }
          event(4) {
            eventName "${RptEventConstants.EVT_NAME_START_TIMER}"
            attributes {
              "${RptEventConstants.EVT_ATTR_TIMER}" "another timer"
            }
          }
          event(5) {
            eventName "${RptEventConstants.EVT_NAME_LOG_TIMER}"
            attributes {
              "${RptEventConstants.EVT_ATTR_TIMER}" "a timer"
            }
          }
          event(6) {
            eventName "${RptEventConstants.EVT_NAME_LOG_TIMER}"
            attributes {
              "${RptEventConstants.EVT_ATTR_TIMER}" "a timer"
              "${RptEventConstants.EVT_ATTR_MESSAGE}" "a message"
            }
          }
          event(7) {
            eventName "${RptEventConstants.EVT_NAME_LOG_TIMER}"
            attributes {
              "${RptEventConstants.EVT_ATTR_TIMER}" "a timer"
              "${RptEventConstants.EVT_ATTR_MESSAGE}" "a formatted message {0}"
              "${RptEventConstants.EVT_ATTR_ARGS}" new String[]{ "arg1", "2" }
            }
          }
        }
      }
    }
  }
}
