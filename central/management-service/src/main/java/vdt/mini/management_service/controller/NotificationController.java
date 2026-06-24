package vdt.mini.management_service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vdt.mini.management_service.dto.response.InAppNotificationResponse;
import vdt.mini.management_service.service.anomaly.alert.AnomalyNotificationService;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final AnomalyNotificationService notificationService;

    public NotificationController(AnomalyNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<InAppNotificationResponse> latest(@RequestParam(defaultValue = "10") int limit) {
        return notificationService.latest(limit);
    }

    @GetMapping("/unread-count")
    public long unreadCount() {
        return notificationService.unreadCount();
    }

    @PatchMapping("/{id}/read")
    public InAppNotificationResponse markRead(@PathVariable String id) {
        return notificationService.markRead(id);
    }
}
