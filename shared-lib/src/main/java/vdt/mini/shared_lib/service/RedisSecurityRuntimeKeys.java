package vdt.mini.shared_lib.service;

public final class RedisSecurityRuntimeKeys {
    private static final String PREFIX = "security:runtime:v1";

    private RedisSecurityRuntimeKeys() {
    }

    public static String inboundSettings(String endpointId) { return "security:config:inbound:" + endpointId; }
    public static String outboundSettings(String endpointId) { return "security:config:outbound:" + endpointId; }
    public static String legacySettingsChannel(String serviceId) { return "security:settings:" + serviceId; }
    public static String manifest(String serviceId) { return PREFIX + ":service:" + serviceId + ":manifest"; }
    public static String inboundEndpointIds(String serviceId) { return PREFIX + ":service:" + serviceId + ":inbound-endpoints"; }
    public static String outboundEndpointIds(String serviceId) { return PREFIX + ":service:" + serviceId + ":outbound-endpoints"; }
    public static String clients(String serviceId) { return PREFIX + ":service:" + serviceId + ":clients"; }
    public static String authConfigs(String serviceId) { return PREFIX + ":service:" + serviceId + ":auth-configs"; }
    public static String permissions(String serviceId) { return PREFIX + ":service:" + serviceId + ":permissions"; }
    public static String eventsChannel(String serviceId) { return PREFIX + ":service:" + serviceId + ":events"; }
}
