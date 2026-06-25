package vdt.mini.management_service.service.anomaly.rolling;

import vdt.mini.management_service.dto.event.RollingWindowEntry;

public record RedisRollingWindowEntry(String id, RollingWindowEntry entry) {
}
