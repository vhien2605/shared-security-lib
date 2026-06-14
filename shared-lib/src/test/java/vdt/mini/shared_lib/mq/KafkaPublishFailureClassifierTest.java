package vdt.mini.shared_lib.mq;

import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.NetworkException;
import org.apache.kafka.common.errors.RecordBatchTooLargeException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.Test;
import vdt.mini.shared_lib.enums.OutboundErrorCode;

import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaPublishFailureClassifierTest {
    private final KafkaPublishFailureClassifier classifier = new KafkaPublishFailureClassifier();

    @Test
    void isRetryable_shouldReturnTrue_whenExecutionExceptionWrapsRetryableKafkaFailure() {
        Throwable failure = new ExecutionException(new NetworkException("broker unavailable"));

        assertThat(classifier.isRetryable(failure)).isTrue();
        assertThat(classifier.classify(failure)).isEqualTo(OutboundErrorCode.BROKER_UNAVAILABLE);
    }

    @Test
    void isRetryable_shouldReturnFalse_whenExecutionExceptionWrapsNonRetryableKafkaFailure() {
        Throwable failure = new ExecutionException(new SerializationException("bad value"));

        assertThat(classifier.isRetryable(failure)).isFalse();
        assertThat(classifier.classify(failure)).isEqualTo(OutboundErrorCode.SERIALIZATION_ERROR);
    }

    @Test
    void classify_shouldReturnAuthenticationAuthorizationError_whenAuthenticationFails() {
        assertThat(classifier.isRetryable(new AuthenticationException("denied"))).isFalse();
        assertThat(classifier.classify(new AuthenticationException("denied")))
                .isEqualTo(OutboundErrorCode.AUTHORIZATION_ERROR);
    }

    @Test
    void classify_shouldReturnRecordTooLarge_whenRecordBatchTooLarge() {
        assertThat(classifier.isRetryable(new RecordBatchTooLargeException("too large"))).isFalse();
        assertThat(classifier.classify(new RecordBatchTooLargeException("too large")))
                .isEqualTo(OutboundErrorCode.RECORD_TOO_LARGE);
    }

    @Test
    void classify_shouldReturnProducerException_whenKafkaExceptionIsNotMoreSpecific() {
        KafkaException failure = new KafkaException("producer failed");

        assertThat(classifier.isRetryable(failure)).isFalse();
        assertThat(classifier.classify(failure)).isEqualTo(OutboundErrorCode.PRODUCER_EXCEPTION);
    }

    @Test
    void classify_shouldReturnPublishFailed_whenFailureIsNotKafkaSpecific() {
        RuntimeException failure = new RuntimeException("unknown failure");

        assertThat(classifier.isRetryable(failure)).isFalse();
        assertThat(classifier.classify(failure)).isEqualTo(OutboundErrorCode.PUBLISH_FAILED);
    }

    @Test
    void classify_shouldPreferSpecificErrorsBeforeGenericKafkaException() {
        assertThat(classifier.classify(new KafkaException(new InvalidTopicException("bad topic"))))
                .isEqualTo(OutboundErrorCode.INVALID_TOPIC);
        assertThat(classifier.classify(new KafkaException(new RecordTooLargeException("too large"))))
                .isEqualTo(OutboundErrorCode.RECORD_TOO_LARGE);
        assertThat(classifier.classify(new KafkaException(new SerializationException("bad value"))))
                .isEqualTo(OutboundErrorCode.SERIALIZATION_ERROR);
        assertThat(classifier.classify(new KafkaException(new ConfigException("bad config"))))
                .isEqualTo(OutboundErrorCode.CONFIG_ERROR);
    }
}
