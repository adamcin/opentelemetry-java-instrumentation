package datadog.trace

import datadog.opentracing.DDTracer
import datadog.opentracing.propagation.DatadogHttpCodec
import datadog.trace.api.Config
import datadog.trace.common.writer.DDAgentWriter
import datadog.trace.common.writer.ListWriter
import datadog.trace.common.writer.LoggingWriter
import datadog.trace.util.test.DDSpecification
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.junit.contrib.java.lang.system.RestoreSystemProperties

import static datadog.trace.api.Config.DEFAULT_SERVICE_NAME
import static datadog.trace.api.Config.HEALTH_METRICS_ENABLED
import static datadog.trace.api.Config.HEALTH_METRICS_STATSD_PORT
import static datadog.trace.api.Config.PREFIX
import static datadog.trace.api.Config.SPAN_TAGS
import static datadog.trace.api.Config.WRITER_TYPE

class DDTracerTest extends DDSpecification {

  @Rule
  public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties()
  @Rule
  public final EnvironmentVariables environmentVariables = new EnvironmentVariables()

  def setupSpec() {
    // assert that a trace agent isn't running locally as that messes up the test.
    try {
      (new Socket("localhost", 8126)).close()
      throw new IllegalStateException("An agent is already running locally on port 8126. Please stop it if you want to run tests locally.")
    } catch (final ConnectException ioe) {
      // trace agent is not running locally.
    }
  }

  def "verify defaults on tracer"() {
    when:
    def tracer = new DDTracer()

    then:
    tracer.serviceName == "unnamed-java-app"
    tracer.writer.toString() == "DDAgentWriter { api=DDApi { tracesUrl=http://localhost:8126/v0.3/traces } }"
    tracer.writer.monitor instanceof DDAgentWriter.NoopMonitor

    tracer.spanContextDecorators.size() == 12

    tracer.injector instanceof DatadogHttpCodec.Injector
    tracer.extractor instanceof DatadogHttpCodec.Extractor
  }

  def "verify enabling health monitor"() {
    setup:
    System.setProperty(PREFIX + HEALTH_METRICS_ENABLED, "true")
    System.setProperty(PREFIX + HEALTH_METRICS_STATSD_PORT, "8125")

    when:
    def tracer = new DDTracer(new Config())

    then:
    tracer.writer.toString() == "DDAgentWriter { api=DDApi { tracesUrl=http://localhost:8126/v0.3/traces }, monitor=StatsD { host=localhost:8125 } }"
    tracer.writer.monitor instanceof DDAgentWriter.StatsDMonitor
  }


  def "verify overriding writer"() {
    setup:
    System.setProperty(PREFIX + WRITER_TYPE, "LoggingWriter")

    when:
    def tracer = new DDTracer(new Config())

    then:
    tracer.writer instanceof LoggingWriter
  }

  def "verify mapping configs on tracer"() {
    setup:
    System.setProperty(PREFIX + SPAN_TAGS, mapString)

    when:
    def config = new Config()
    def tracer = new DDTracer(config)

    then:
    tracer.defaultSpanTags == map

    where:
    mapString       | map
    "a:1, a:2, a:3" | [a: "3"]
    "a:b,c:d,e:"    | [a: "b", c: "d"]
  }

  def "verify single override on #source for #key"() {
    when:
    System.setProperty(PREFIX + key, value)
    def tracer = new DDTracer(new Config())

    then:
    tracer."$source".toString() == expected

    where:

    source   | key                | value           | expected
    "writer" | "default"          | "default"       | "DDAgentWriter { api=DDApi { tracesUrl=http://localhost:8126/v0.3/traces } }"
    "writer" | "writer.type"      | "LoggingWriter" | "LoggingWriter { }"
    "writer" | "agent.host"       | "somethingelse" | "DDAgentWriter { api=DDApi { tracesUrl=http://somethingelse:8126/v0.3/traces } }"
    "writer" | "agent.port"       | "777"           | "DDAgentWriter { api=DDApi { tracesUrl=http://localhost:777/v0.3/traces } }"
    "writer" | "trace.agent.port" | "9999"          | "DDAgentWriter { api=DDApi { tracesUrl=http://localhost:9999/v0.3/traces } }"
  }

  def "verify writer constructor"() {
    setup:
    def writer = new ListWriter()

    when:
    def tracer = new DDTracer(DEFAULT_SERVICE_NAME, writer)

    then:
    tracer.serviceName == DEFAULT_SERVICE_NAME
    tracer.writer == writer
    tracer.localRootSpanTags[Config.RUNTIME_ID_TAG].size() > 0 // not null or empty
    tracer.localRootSpanTags[Config.LANGUAGE_TAG_KEY] == Config.LANGUAGE_TAG_VALUE
  }

  def "root tags are applied only to root spans"() {
    setup:
    def tracer = new DDTracer('my_service', new ListWriter(), '', ['only_root': 'value'], [:])
    def root = tracer.buildSpan('my_root').start()
    def child = tracer.buildSpan('my_child').asChildOf(root).start()

    expect:
    root.context().tags.containsKey('only_root')
    !child.context().tags.containsKey('only_root')

    cleanup:
    child.finish()
    root.finish()
  }
}
