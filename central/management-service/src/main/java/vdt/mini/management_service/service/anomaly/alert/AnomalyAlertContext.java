package vdt.mini.management_service.service.anomaly.alert;

import vdt.mini.management_service.dto.event.AnomalyEvent;
import vdt.mini.management_service.entity.AlertConfig;

public record AnomalyAlertContext(AnomalyEvent event,
                                  String title,
                                  String content,
                                  AlertConfig alertConfig,
                                  boolean emailEnabled) {
}
