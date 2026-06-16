package vdt.mini.shared_lib.mq;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import vdt.mini.shared_lib.aspect.KafkaSendCaptureAspect;
import vdt.mini.shared_lib.web.OutboundContext;
import vdt.mini.shared_lib.web.OutboundContextHolder;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaSendCaptureAspectTest {
    private final OutboundContextHolder contextHolder = new OutboundContextHolder();
    private final KafkaSendCaptureAspect aspect = new KafkaSendCaptureAspect(contextHolder);

    @AfterEach
    void tearDown() {
        contextHolder.clear();
        KafkaSendCaptureContext.clear();
    }

    @Test
    void captureSendFuture_shouldCaptureCompletableFuture_whenMqContextAndCaptureScopeActive() throws Throwable {
        CompletableFuture<?> future = CompletableFuture.completedFuture("ack");
        ProceedingJoinPoint joinPoint = joinPoint(new Object[]{"user.created", "key", "value"}, future);
        contextHolder.set(mqContext());
        KafkaSendCaptureContext.start();

        Object result = aspect.captureSendFuture(joinPoint);

        assertThat(result).isSameAs(future);
        assertThat(KafkaSendCaptureContext.capturedFutures()).containsExactly(future);
    }

    @Test
    void captureSendFuture_shouldNotCapture_whenNoMqContext() throws Throwable {
        CompletableFuture<?> future = CompletableFuture.completedFuture("ack");
        ProceedingJoinPoint joinPoint = joinPoint(new Object[]{"user.created", "key", "value"}, future);
        KafkaSendCaptureContext.start();

        aspect.captureSendFuture(joinPoint);

        assertThat(KafkaSendCaptureContext.capturedFutures()).isEmpty();
    }

    @Test
    void captureSendFuture_shouldNotCaptureControlPlaneTopic() throws Throwable {
        CompletableFuture<?> future = CompletableFuture.completedFuture("ack");
        ProducerRecord<String, String> record = new ProducerRecord<>("security.endpoint.registration", "value");
        ProceedingJoinPoint joinPoint = joinPoint(new Object[]{record}, future);
        contextHolder.set(mqContext());
        KafkaSendCaptureContext.start();

        aspect.captureSendFuture(joinPoint);

        assertThat(KafkaSendCaptureContext.capturedFutures()).isEmpty();
    }

    @Test
    void captureSendFuture_shouldNotCaptureSecurityAuditTopic() throws Throwable {
        CompletableFuture<?> future = CompletableFuture.completedFuture("ack");
        ProceedingJoinPoint joinPoint = joinPoint(new Object[]{"security.logs", "trace-1", "payload"}, future);
        contextHolder.set(mqContext());
        KafkaSendCaptureContext.start();

        aspect.captureSendFuture(joinPoint);

        assertThat(KafkaSendCaptureContext.capturedFutures()).isEmpty();
    }

    @Test
    void captureSendFuture_shouldCaptureCompletableFuture_whenTopicComesFromProducerRecord() throws Throwable {
        CompletableFuture<?> future = CompletableFuture.completedFuture("ack");
        ProducerRecord<String, String> record = new ProducerRecord<>("user.created", "value");
        ProceedingJoinPoint joinPoint = joinPoint(new Object[]{record}, future);
        contextHolder.set(mqContext());
        KafkaSendCaptureContext.start();

        Object result = aspect.captureSendFuture(joinPoint);

        assertThat(result).isSameAs(future);
        assertThat(KafkaSendCaptureContext.capturedFutures()).containsExactly(future);
    }

    private static ProceedingJoinPoint joinPoint(Object[] args, Object result) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(args);
        when(joinPoint.proceed()).thenReturn(result);
        return joinPoint;
    }

    private static OutboundContext mqContext() {
        return new OutboundContext("service-1", "endpoint-1", "User Created", null,
                "PUB", "MQ", "trace-1", "corr-1", Instant.now(), "nonce-1");
    }
}
