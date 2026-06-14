package vdt.mini.shared_lib.mq;

import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.errors.*;
import vdt.mini.shared_lib.enums.OutboundErrorCode;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Classifies Kafka producer exceptions so outbound MQ retry and compensation do not expose vendor failures directly.
 */
public class KafkaPublishFailureClassifier {
    public boolean isRetryable(Throwable failure) {
        if (failure == null) {
            return false;
        }
        if (contains(failure, TimeoutException.class)
                || contains(failure, RetriableException.class)
                || contains(failure, NetworkException.class)
                || contains(failure, DisconnectException.class)
                || contains(failure, LeaderNotAvailableException.class)
                || contains(failure, NotLeaderOrFollowerException.class)
                || contains(failure, SocketTimeoutException.class)
                || contains(failure, SocketException.class)
                || contains(failure, ConnectException.class)
                || contains(failure, NoRouteToHostException.class)) {
            return true;
        }
        Throwable unwrapped = unwrapExecutionException(failure);
        return unwrapped != failure && isRetryable(unwrapped);
    }

    public OutboundErrorCode classify(Throwable failure) {
        if (contains(failure, TimeoutException.class) || contains(failure, SocketTimeoutException.class)) {
            return OutboundErrorCode.TIMEOUT_EXCEEDED;
        }
        if (contains(failure, AuthorizationException.class) || contains(failure, AuthenticationException.class)) {
            return OutboundErrorCode.AUTHORIZATION_ERROR;
        }
        if (contains(failure, InvalidTopicException.class) || contains(failure, UnknownTopicOrPartitionException.class)) {
            return OutboundErrorCode.INVALID_TOPIC;
        }
        if (contains(failure, RecordTooLargeException.class) || contains(failure, RecordBatchTooLargeException.class)) {
            return OutboundErrorCode.RECORD_TOO_LARGE;
        }
        if (contains(failure, SerializationException.class)) {
            return OutboundErrorCode.SERIALIZATION_ERROR;
        }
        if (contains(failure, ConfigException.class) || contains(failure, InvalidConfigurationException.class)) {
            return OutboundErrorCode.CONFIG_ERROR;
        }
        if (isRetryable(failure)) {
            return OutboundErrorCode.BROKER_UNAVAILABLE;
        }
        if (contains(failure, KafkaException.class)) {
            return OutboundErrorCode.PRODUCER_EXCEPTION;
        }
        return OutboundErrorCode.PUBLISH_FAILED;
    }

    private static Throwable unwrapExecutionException(Throwable failure) {
        Throwable cursor = failure;
        while (cursor != null) {
            if (cursor instanceof ExecutionException && cursor.getCause() != null) {
                return cursor.getCause();
            }
            cursor = cursor.getCause();
        }
        return failure;
    }

    private static boolean contains(Throwable failure, Class<? extends Throwable> type) {
        Throwable cursor = failure;
        while (cursor != null) {
            if (type.isInstance(cursor)) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }
}
