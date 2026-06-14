package vdt.mini.shared_lib.mq;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Thread-local capture scope for Kafka futures produced inside an annotated outbound MQ publisher method.
 */
public final class KafkaSendCaptureContext {
    private static final ThreadLocal<List<CompletableFuture<?>>> CAPTURED_FUTURES = new ThreadLocal<>();

    private KafkaSendCaptureContext() {
    }

    public static void start() {
        CAPTURED_FUTURES.set(new ArrayList<>());
    }

    public static boolean isActive() {
        return CAPTURED_FUTURES.get() != null;
    }

    public static void capture(CompletableFuture<?> future) {
        List<CompletableFuture<?>> futures = CAPTURED_FUTURES.get();
        if (futures != null && future != null) {
            futures.add(future);
        }
    }

    public static List<CompletableFuture<?>> capturedFutures() {
        List<CompletableFuture<?>> futures = CAPTURED_FUTURES.get();
        if (futures == null || futures.isEmpty()) {
            return List.of();
        }
        return List.copyOf(futures);
    }

    public static void clear() {
        CAPTURED_FUTURES.remove();
    }
}
