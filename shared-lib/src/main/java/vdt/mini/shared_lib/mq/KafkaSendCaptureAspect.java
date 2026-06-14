package vdt.mini.shared_lib.mq;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import vdt.mini.shared_lib.enums.EndpointProtocol;
import vdt.mini.shared_lib.web.OutboundContext;
import vdt.mini.shared_lib.web.OutboundContextHolder;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Captures Kafka send futures only while an outbound MQ annotation scope is active; policy stays in OutBoundSecurityAspect.
 */
@Aspect
@Component
public class KafkaSendCaptureAspect {
    private static final Set<String> CONTROL_PLANE_TOPICS = Set.of("security.endpoint.registration");

    private final OutboundContextHolder contextHolder;

    public KafkaSendCaptureAspect(OutboundContextHolder contextHolder) {
        this.contextHolder = contextHolder;
    }

    @Around("execution(* org.springframework.kafka.core.KafkaTemplate.send(..))")
    public Object captureSendFuture(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        if (shouldCapture(joinPoint.getArgs(), result)) {
            KafkaSendCaptureContext.capture((CompletableFuture<?>) result);
        }
        return result;
    }

    private boolean shouldCapture(Object[] args, Object result) {
        if (!(result instanceof CompletableFuture<?>)) {
            return false;
        }
        if (!KafkaSendCaptureContext.isActive()) {
            return false;
        }
        OutboundContext context = contextHolder.get();
        if (context == null || !EndpointProtocol.MQ.name().equalsIgnoreCase(context.protocol())) {
            return false;
        }
        String topic = topicFromArgs(args);
        return topic == null || !CONTROL_PLANE_TOPICS.contains(topic);
    }

    private static String topicFromArgs(Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) {
            return null;
        }
        if (args[0] instanceof String topic) {
            return topic;
        }
        if (args[0] instanceof ProducerRecord<?, ?> record) {
            return record.topic();
        }
        return null;
    }
}
