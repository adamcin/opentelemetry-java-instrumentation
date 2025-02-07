/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.testing.GlobalTraceUtil
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.boot.SpringApplication
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.wait.strategy.Wait
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.PRODUCER
import static io.opentelemetry.api.trace.StatusCode.ERROR

class SpringKafkaInstrumentationTest extends AgentInstrumentationSpecification {
  @Shared
  static KafkaContainer kafka
  @Shared
  static ConfigurableApplicationContext applicationContext

  def setupSpec() {
    kafka = new KafkaContainer()
      .waitingFor(Wait.forLogMessage(".*started \\(kafka.server.KafkaServer\\).*", 1))
      .withStartupTimeout(Duration.ofMinutes(1))
    kafka.start()

    def app = new SpringApplication(ConsumerConfig)
    app.setDefaultProperties([
      "spring.jmx.enabled"                         : false,
      "spring.main.web-application-type"           : "none",
      "spring.kafka.bootstrap-servers"             : kafka.bootstrapServers,
      "spring.kafka.consumer.auto-offset-reset"    : "earliest",
      "spring.kafka.consumer.linger-ms"            : 10,
      // wait 1s between poll() calls
      "spring.kafka.listener.idle-between-polls"   : 1000,
      "spring.kafka.producer.transaction-id-prefix": "test-",
    ])
    applicationContext = app.run()
  }

  def cleanupSpec() {
    kafka.stop()
    applicationContext?.stop()
  }

  def "should create spans for batch receive+process"() {
    given:
    def kafkaTemplate = applicationContext.getBean("kafkaTemplate", KafkaTemplate)

    when:
    // This test assumes that messages are sent and received as a batch. Occasionally it happens
    // that the messages are not received as a batch, but one by one. This doesn't match what the
    // assertion expects. To reduce flakiness we retry the test when messages weren't received as
    // a batch.
    def maxAttempts = 5
    for (i in 1..maxAttempts) {
      Listener.reset()

      runWithSpan("producer") {
        kafkaTemplate.executeInTransaction({ ops ->
          ops.send("testTopic", "10", "testSpan1")
          ops.send("testTopic", "20", "testSpan2")
        })
      }

      Listener.waitForMessages()
      if (Listener.getLastBatchSize() == 2) {
        break
      } else if (i < maxAttempts) {
        ignoreTracesAndClear(2)
        System.err.println("Messages weren't received as batch, retrying")
      }
    }

    then:
    assertTraces(2) {
      traces.sort(orderByRootSpanName("producer", "testTopic receive", "testTopic process"))

      SpanData producer1, producer2

      trace(0, 3) {
        span(0) {
          name "producer"
        }
        span(1) {
          name "testTopic send"
          kind PRODUCER
          childOf span(0)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "kafka"
            "$SemanticAttributes.MESSAGING_DESTINATION" "testTopic"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
          }
        }
        span(2) {
          name "testTopic send"
          kind PRODUCER
          childOf span(0)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "kafka"
            "$SemanticAttributes.MESSAGING_DESTINATION" "testTopic"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
          }
        }

        producer1 = span(1)
        producer2 = span(2)
      }
      trace(1, 3) {
        span(0) {
          name "testTopic receive"
          kind CONSUMER
          hasNoParent()
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "kafka"
            "$SemanticAttributes.MESSAGING_DESTINATION" "testTopic"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.MESSAGING_OPERATION" "receive"
          }
        }
        span(1) {
          name "testTopic process"
          kind CONSUMER
          childOf span(0)
          hasLink producer1
          hasLink producer2
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "kafka"
            "$SemanticAttributes.MESSAGING_DESTINATION" "testTopic"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.MESSAGING_OPERATION" "process"
          }
        }
        span(2) {
          name "consumer"
          childOf span(1)
        }
      }
    }
  }

  def "should handle failure in Kafka listener"() {
    given:
    def kafkaTemplate = applicationContext.getBean("kafkaTemplate", KafkaTemplate)

    when:
    runWithSpan("producer") {
      kafkaTemplate.executeInTransaction({ ops ->
        ops.send("testTopic", "10", "error")
      })
    }

    then:
    assertTraces(2) {
      traces.sort(orderByRootSpanName("producer", "testTopic receive"))

      SpanData producer

      trace(0, 2) {
        span(0) {
          name "producer"
        }
        span(1) {
          name "testTopic send"
          kind PRODUCER
          childOf span(0)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "kafka"
            "$SemanticAttributes.MESSAGING_DESTINATION" "testTopic"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
          }
        }

        producer = span(1)
      }
      trace(1, 3) {
        span(0) {
          name "testTopic receive"
          kind CONSUMER
          hasNoParent()
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "kafka"
            "$SemanticAttributes.MESSAGING_DESTINATION" "testTopic"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.MESSAGING_OPERATION" "receive"
          }
        }
        span(1) {
          name "testTopic process"
          kind CONSUMER
          childOf span(0)
          hasLink producer
          status ERROR
          errorEvent IllegalArgumentException, "boom"
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "kafka"
            "$SemanticAttributes.MESSAGING_DESTINATION" "testTopic"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.MESSAGING_OPERATION" "process"
          }
        }
        span(2) {
          name "consumer"
          childOf span(1)
        }
      }
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class ConsumerConfig {

    @Bean
    NewTopic topic() {
      return TopicBuilder.name("testTopic")
        .partitions(1)
        .replicas(1)
        .build()
    }

    @Bean
    Listener listener() {
      return new Listener()
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> batchFactory(
      ConsumerFactory<String, String> consumerFactory) {
      ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>()
      // do not retry failed records
      factory.setBatchErrorHandler(new DoNothingBatchErrorHandler())
      factory.setConsumerFactory(consumerFactory)
      factory.setBatchListener(true)
      factory.setAutoStartup(true)
      // setting interceptBeforeTx to true eliminates kafka-clients noise - otherwise spans would be created on every ConsumerRecords#iterator() call
      factory.setContainerCustomizer({ container ->
        container.setInterceptBeforeTx(true)
      })
      factory
    }
  }

  static class Listener {
    static AtomicInteger lastBatchSize = new AtomicInteger()
    static CountDownLatch messageReceived = new CountDownLatch(2)

    @KafkaListener(id = "testListener", topics = "testTopic", containerFactory = "batchFactory")
    void listener(List<ConsumerRecord<String, String>> records) {
      lastBatchSize.set(records.size())
      records.size().times {
        messageReceived.countDown()
      }

      GlobalTraceUtil.runWithSpan("consumer") {}
      records.forEach({ record ->
        if (record.value() == "error") {
          throw new IllegalArgumentException("boom")
        }
      })
    }

    static void reset() {
      messageReceived = new CountDownLatch(2)
      lastBatchSize.set(0)
    }

    static void waitForMessages() {
      messageReceived.await(30, TimeUnit.SECONDS)
    }

    static int getLastBatchSize() {
      return lastBatchSize.get()
    }
  }
}
