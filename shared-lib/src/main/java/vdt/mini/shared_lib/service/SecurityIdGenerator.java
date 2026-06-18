package vdt.mini.shared_lib.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class SecurityIdGenerator {

    private static final int STORED_ID_LENGTH = 32;

    private SecurityIdGenerator() {
    }

    public static String serviceId(String namespace, String serviceName) {
        String normalizedNamespace = requireText(namespace, "namespace");
        String normalizedServiceName = requireText(serviceName, "serviceName");
        return sha256Hex32(normalizedNamespace + ":" + normalizedServiceName);
    }

    public static String endpointId(String serviceId,
                                    String direction,
                                    String protocol,
                                    String method,
                                    String destination,
                                    String consumerGroup) {
        return sha256Hex32(canonicalEndpointIdentity(serviceId, direction, protocol, method, destination, consumerGroup));
    }

    public static String canonicalEndpointIdentity(String serviceId,
                                                   String direction,
                                                   String protocol,
                                                   String method,
                                                   String destination,
                                                   String consumerGroup) {
        return requireText(serviceId, "serviceId") + "|"
                + requireText(direction, "direction").toUpperCase(Locale.ROOT) + "|"
                + requireText(protocol, "protocol").toUpperCase(Locale.ROOT) + "|"
                + requireText(method, "method").toUpperCase(Locale.ROOT) + "|"
                + optionalText(destination) + "|"
                + optionalText(consumerGroup);
    }

    private static String sha256Hex32(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, STORED_ID_LENGTH);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        return value == null ? "" : value.trim();
    }
}
