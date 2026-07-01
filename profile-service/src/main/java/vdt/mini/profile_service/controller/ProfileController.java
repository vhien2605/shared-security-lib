package vdt.mini.profile_service.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/profile")
public class ProfileController {

    private static final String HEADER_SIMULATE = "X-Simulate";

    @PostMapping("/api/info")
    public ResponseEntity<String> info(@RequestBody String body, HttpServletRequest request) {
        String simulate = request.getHeader(HEADER_SIMULATE);
        if (simulate == null) {
            simulate = "success";
        }
        return switch (simulate.trim().toLowerCase()) {
            case "400" -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("{\"error\":\"Simulated 400 Bad Request\"}");
            case "500" -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\":\"Simulated 500 Internal Server Error\"}");
            case "timeout" -> {
                try {
                    Thread.sleep(15000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                yield ResponseEntity.ok("{\"result\":\"after timeout\"}");
            }
            default -> ResponseEntity.ok("{\"result\":\"success\",\"body\":\"" + escape(body) + "\"}");
        };
    }

    private static String escape(String value) {
        return value == null ? ""
                : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
