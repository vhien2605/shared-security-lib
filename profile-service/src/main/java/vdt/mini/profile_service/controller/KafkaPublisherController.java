package vdt.mini.profile_service.controller;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/publish")
public class KafkaPublisherController {

    private static final String TOPIC = "user.profile.create";
    private static final String HEADER_CLIENT_KEY = "X-Client-Key";
    private static final String HEADER_API_KEY = "X-Api-Key";
    private static final String HEADER_SIGNATURE = "X-Signature";
    private static final String HEADER_TIMESTAMP = "X-Timestamp";
    private static final String HEADER_NONCE = "X-Nonce";
    private static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    private static final String HEADER_TRACE_ID = "X-Trace-Id";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaPublisherController(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostMapping("/user-profile")
    public Map<String, Object> publish(@RequestBody PublishRequest request) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString();

        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, request.value());

        record.headers().add(HEADER_CLIENT_KEY, toBytes(request.clientKey()));
        if ("API_KEY".equalsIgnoreCase(request.authType())) {
            record.headers().add(HEADER_API_KEY, toBytes(request.apiKey()));
        }
        record.headers().add(HEADER_TIMESTAMP, toBytes(timestamp));
        record.headers().add(HEADER_NONCE, toBytes(nonce));
        record.headers().add(HEADER_CORRELATION_ID, toBytes(firstNonBlank(request.correlationId(), nonce)));
        record.headers().add(HEADER_TRACE_ID, toBytes(firstNonBlank(request.traceId(), UUID.randomUUID().toString())));

        if ("HMAC".equalsIgnoreCase(request.authType()) && request.secretKey() != null) {
            String payloadHash = sha256(ofNullSafe(request.value()));
            String payload = "MQ\n" + TOPIC + "\n" + timestamp + "\n" + nonce + "\n" + payloadHash;
            String signature = hmacSha256(payload, request.secretKey());
            record.headers().add(HEADER_SIGNATURE, toBytes(signature));
        }

        kafkaTemplate.send(record);

        return Map.of(
                "status", "sent",
                "topic", TOPIC,
                "timestamp", timestamp,
                "nonce", nonce,
                "authType", request.authType(),
                "clientKey", request.clientKey()
        );
    }

    private static String hmacSha256(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static byte[] toBytes(String value) {
        return value == null ? null : value.getBytes(StandardCharsets.UTF_8);
    }

    private static String ofNullSafe(String value) {
        return value == null ? "" : value;
    }

    private static String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }
}
