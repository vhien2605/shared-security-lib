package vdt.mini.management_service.service.anomaly.baseline;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class BaselineVersionGenerator {
    private final Clock clock;

    public BaselineVersionGenerator() {
        this(Clock.systemUTC());
    }

    BaselineVersionGenerator(Clock clock) {
        this.clock = clock;
    }

    public String nextVersion(String prefix) {
        String normalized = prefix == null || prefix.isBlank() ? "baseline" : prefix.trim();
        return normalized + "-" + Instant.now(clock).toEpochMilli() + "-" + UUID.randomUUID();
    }
}
