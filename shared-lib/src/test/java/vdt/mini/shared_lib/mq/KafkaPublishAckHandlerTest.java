package vdt.mini.shared_lib.mq;

import org.apache.kafka.common.errors.NetworkException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class KafkaPublishAckHandlerTest {
    private final KafkaPublishAckHandler ackHandler = new KafkaPublishAckHandler();

    @Test
    void waitForAck_shouldReturnPublishInvoked_whenResultAndCapturedFuturesAreMissing() throws Exception {
        KafkaPublishAckHandler.PublishAck ack = ackHandler.waitForAck(null, List.of(), 10);

        assertThat(ack.acknowledged()).isFalse();
    }

    @Test
    void waitForAck_shouldAcknowledge_whenResultIsSendResult() throws Exception {
        KafkaPublishAckHandler.PublishAck ack = ackHandler.waitForAck(mock(SendResult.class), 10);

        assertThat(ack.acknowledged()).isTrue();
    }

    @Test
    void waitForAck_shouldAcknowledge_whenResultFutureCompletes() throws Exception {
        CompletableFuture<SendResult<?, ?>> future = CompletableFuture.completedFuture(mock(SendResult.class));

        KafkaPublishAckHandler.PublishAck ack = ackHandler.waitForAck(future, 10);

        assertThat(ack.acknowledged()).isTrue();
    }

    @Test
    void waitForAck_shouldWaitForCapturedFutures_whenResultIsNull() throws Exception {
        CompletableFuture<SendResult<?, ?>> first = CompletableFuture.completedFuture(mock(SendResult.class));
        CompletableFuture<SendResult<?, ?>> second = CompletableFuture.completedFuture(mock(SendResult.class));

        KafkaPublishAckHandler.PublishAck ack = ackHandler.waitForAck(null, List.of(first, second), 10);

        assertThat(ack.acknowledged()).isTrue();
    }

    @Test
    void waitForAck_shouldPropagateExecutionFailure_whenCapturedFutureFails() {
        CompletableFuture<SendResult<?, ?>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new NetworkException("broker unavailable"));

        assertThatThrownBy(() -> ackHandler.waitForAck(null, List.of(failed), 10))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(NetworkException.class);
    }

    @Test
    void waitForAck_shouldTimeout_whenFutureDoesNotComplete() {
        CompletableFuture<SendResult<?, ?>> future = new CompletableFuture<>();

        assertThatThrownBy(() -> ackHandler.waitForAck(future, 1))
                .isInstanceOf(TimeoutException.class);
    }
}
