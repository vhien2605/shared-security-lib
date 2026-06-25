package vdt.mini.management_service.dto.event;

public record AnomalyGroupKey(String serviceId, String endpointId, String flowType) {
    public boolean isValid() {
        return hasText(serviceId) && hasText(endpointId) && hasText(flowType);
    }

    public String asStableString() {
        return serviceId + "|" + endpointId + "|" + flowType;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
