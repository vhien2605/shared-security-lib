package vdt.mini.user_service.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vdt.mini.shared_lib.annotation.InBoundSecurity;
import vdt.mini.shared_lib.enums.EndpointMethod;
import vdt.mini.shared_lib.enums.EndpointProtocol;
import vdt.mini.user_service.client.ProfileClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/anomaly-test")
public class AnomalyTestController {

    private final ProfileClient profileClient;

    public AnomalyTestController(ProfileClient profileClient) {
        this.profileClient = profileClient;
    }

    @PostMapping("/inbound")
    @InBoundSecurity(
            name = "anomaly-inbound",
            path = "/anomaly-test/inbound",
            protocol = EndpointProtocol.HTTP,
            method = EndpointMethod.POST)
    public Map<String, Object> inbound(@RequestParam(defaultValue = "normal") String scenario,
                                       @RequestParam(defaultValue = "3000") long delayMs,
                                       @RequestParam(defaultValue = "Simulated inbound failure") String message,
                                       @RequestBody(required = false) String body) throws InterruptedException {
        return switch (scenario.trim().toLowerCase()) {
            case "latency" -> {
                Thread.sleep(Math.max(0, delayMs));
                yield Map.of("scenario", "INBOUND_LATENCY", "delayMs", delayMs, "bodySize", body == null ? 0 : body.length());
            }
            case "failure" -> throw new IllegalStateException(message);
            default -> Map.of("scenario", "INBOUND_NORMAL", "bodySize", body == null ? 0 : body.length());
        };
    }

    @PostMapping("/outbound")
    public Map<String, Object> outbound(@RequestParam(defaultValue = "normal") String scenario,
                                        @RequestParam(defaultValue = "1") int count,
                                        @RequestParam(defaultValue = "500") String failureStatus,
                                        @RequestBody(required = false) String body) {
        String simulate = switch (scenario.trim().toLowerCase()) {
            case "latency", "timeout" -> "timeout";
            case "failure" -> failureStatus;
            default -> "success";
        };
        return callProfile(count, body, simulate);
    }

    private Map<String, Object> callProfile(int count, String body, String simulate) {
        int safeCount = Math.max(1, Math.min(count, 20));
        List<String> results = new ArrayList<>();
        for (int i = 0; i < safeCount; i++) {
            try {
                String result = profileClient.profile(body == null ? "{}" : body, simulate);
                results.add(result == null ? "null" : result);
            } catch (RuntimeException exception) {
                results.add(exception.getClass().getSimpleName() + ": " + exception.getMessage());
            }
        }
        return Map.of(
                "scenario", "OUTBOUND_" + simulate.toUpperCase(),
                "simulate", simulate,
                "count", safeCount,
                "results", results);
    }
}
