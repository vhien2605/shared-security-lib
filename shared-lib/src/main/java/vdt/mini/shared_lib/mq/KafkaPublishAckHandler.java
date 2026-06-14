package vdt.mini.shared_lib.mq;

import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Waits for Kafka producer broker acknowledgements returned by annotated outbound MQ publisher wrappers.
 */
public class KafkaPublishAckHandler {
    public PublishAck waitForAck(Object publishResult, int timeoutMs) throws Exception {
        return waitForAck(publishResult, List.of(), timeoutMs);
    }

    public PublishAck waitForAck(Object publishResult, List<CompletableFuture<?>> capturedFutures, int timeoutMs) throws Exception {
        if (publishResult == null) {
            return waitForCapturedFutures(capturedFutures, timeoutMs);
        }
        if (publishResult instanceof SendResult<?, ?>) {
            return PublishAck.brokerAcknowledged();
        }
        if (publishResult instanceof CompletableFuture<?> future) {
            Object ack = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            if (ack instanceof SendResult<?, ?>) {
                return PublishAck.brokerAcknowledged();
            }
            return PublishAck.brokerAcknowledged();
        }
        return waitForCapturedFutures(capturedFutures, timeoutMs);
    }

    private void waitForAll(List<CompletableFuture<?>> futures, int timeoutMs) throws Exception {
        for (CompletableFuture<?> future : futures) {
            future.get(timeoutMs, TimeUnit.MILLISECONDS);
        }
    }

    private PublishAck waitForCapturedFutures(List<CompletableFuture<?>> capturedFutures, int timeoutMs) throws Exception {
        if (capturedFutures != null && !capturedFutures.isEmpty()) {
            waitForAll(capturedFutures, timeoutMs);
            return PublishAck.brokerAcknowledged();
        }
        return PublishAck.publishInvoked();
    }

    public record PublishAck(boolean acknowledged) {
        static PublishAck brokerAcknowledged() {
            return new PublishAck(true);
        }

        static PublishAck publishInvoked() {
            return new PublishAck(false);
        }
    }
}
