package vdt.mini.shared_lib.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vdt.mini.shared_lib.document.InboundEndpointDTO;
import vdt.mini.shared_lib.document.OutboundEndpointDTO;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class IdentityManager {

    private static final Logger log = LoggerFactory.getLogger(IdentityManager.class);

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final String namespace;
    private final String configuredServiceName;

    private String serviceId;
    private String serviceName;
    private String baseUrl;
    private String description;
    private Map<String, InboundEndpointDTO> inbounds;
    private Map<String, OutboundEndpointDTO> outbounds;

    @Autowired
    public IdentityManager(@Value("${app.security.namespace:default}") String namespace,
                           @Value("${app.security.service.name:my-service}") String configuredServiceName) {
        this.namespace = namespace;
        this.configuredServiceName = configuredServiceName;
        this.serviceId = resolveInitialServiceId(namespace, configuredServiceName);
        this.serviceName = null;
        this.baseUrl = null;
        this.description = null;
        this.inbounds = new LinkedHashMap<>();
        this.outbounds = new LinkedHashMap<>();
        log.debug("Initialized deterministic in-memory identity serviceId={}", serviceId);
    }

    public String getOrCreateServiceId() {
        lock.readLock().lock();
        try {
            if (!isBlank(namespace) && !isBlank(configuredServiceName)) {
                return SecurityIdGenerator.serviceId(namespace, configuredServiceName);
            }
            return serviceId;
        } finally {
            lock.readLock().unlock();
        }
    }

    public String resolveServiceId(String namespace, String serviceName) {
        return SecurityIdGenerator.serviceId(namespace, serviceName);
    }

    public InboundEndpointDTO getOrCreateInbound(String compositeKey, InboundEndpointDTO dto) {
        lock.writeLock().lock();
        try {
            InboundEndpointDTO existing = inbounds.get(compositeKey);
            if (existing != null) {
                return existing;
            }
            String newId = UUID.randomUUID().toString();
            dto.setEndpointId(newId);
            inbounds.put(compositeKey, dto);
            log.debug("Stored in-memory inbound endpointId={} for key={}", newId, compositeKey);
            return dto;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public OutboundEndpointDTO getOrCreateOutbound(String compositeKey, OutboundEndpointDTO dto) {
        lock.writeLock().lock();
        try {
            OutboundEndpointDTO existing = outbounds.get(compositeKey);
            if (existing != null) {
                return existing;
            }
            String newId = UUID.randomUUID().toString();
            dto.setEndpointId(newId);
            outbounds.put(compositeKey, dto);
            log.debug("Stored in-memory outbound endpointId={} for key={}", newId, compositeKey);
            return dto;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String buildCompositeKey(String protocol, String method, String pathOrTopic) {
        return protocol + "_" + method + "_" + (pathOrTopic != null ? pathOrTopic : "");
    }

    public Map<String, InboundEndpointDTO> getKnownInbounds() {
        lock.readLock().lock();
        try {
            Map<String, InboundEndpointDTO> snapshot = new LinkedHashMap<>();
            for (Map.Entry<String, InboundEndpointDTO> entry : inbounds.entrySet()) {
                snapshot.put(entry.getKey(), copyInbound(entry.getValue()));
            }
            return snapshot;
        } finally {
            lock.readLock().unlock();
        }
    }

    public ServiceMetadata ensureServiceMetadata(String defaultServiceName, String defaultBaseUrl, String defaultDescription) {
        lock.writeLock().lock();
        try {
            if (isBlank(serviceName) && !isBlank(defaultServiceName)) {
                serviceName = defaultServiceName;
            }
            if (isBlank(baseUrl) && !isBlank(defaultBaseUrl)) {
                baseUrl = defaultBaseUrl;
            }
            if (isBlank(description) && !isBlank(defaultDescription)) {
                description = defaultDescription;
            }
            return new ServiceMetadata(serviceName, baseUrl, description);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Map<String, OutboundEndpointDTO> getKnownOutbounds() {
        lock.readLock().lock();
        try {
            Map<String, OutboundEndpointDTO> snapshot = new LinkedHashMap<>();
            for (Map.Entry<String, OutboundEndpointDTO> entry : outbounds.entrySet()) {
                snapshot.put(entry.getKey(), copyOutbound(entry.getValue()));
            }
            return snapshot;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void saveDeterministicMetadata(String serviceId,
                                          String serviceName,
                                          String baseUrl,
                                          String description,
                                          List<InboundEndpointDTO> inbounds,
                                          List<OutboundEndpointDTO> outbounds) {
        lock.writeLock().lock();
        try {
            this.serviceId = requireText(serviceId, "serviceId");
            this.serviceName = serviceName;
            this.baseUrl = baseUrl;
            this.description = description;
            this.inbounds = new LinkedHashMap<>();
            for (InboundEndpointDTO inbound : safeList(inbounds)) {
                if (inbound == null) {
                    continue;
                }
                String destination = inbound.getProtocol() != null && inbound.getProtocol().trim().equalsIgnoreCase("MQ")
                        ? inbound.getTopic()
                        : inbound.getPath();
                this.inbounds.put(buildCompositeKey(inbound.getProtocol(), inbound.getMethod(), destination), copyInbound(inbound));
            }
            this.outbounds = new LinkedHashMap<>();
            for (OutboundEndpointDTO outbound : safeList(outbounds)) {
                if (outbound == null) {
                    continue;
                }
                String destination = outbound.getProtocol() != null && outbound.getProtocol().trim().equalsIgnoreCase("MQ")
                        ? outbound.getTopic()
                        : outbound.getTargetUrl();
                this.outbounds.put(buildCompositeKey(outbound.getProtocol(), outbound.getMethod(), destination), copyOutbound(outbound));
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private String resolveInitialServiceId(String namespace, String serviceName) {
        if (!isBlank(namespace) && !isBlank(serviceName)) {
            return SecurityIdGenerator.serviceId(namespace, serviceName);
        }
        return UUID.randomUUID().toString();
    }

    private InboundEndpointDTO copyInbound(InboundEndpointDTO source) {
        if (source == null) {
            return null;
        }
        return new InboundEndpointDTO(
                source.getEndpointId(),
                source.getName(),
                source.getPath(),
                source.getTopic(),
                source.getMethod(),
                source.getProtocol(),
                source.getDescription(),
                source.getEnabled()
        );
    }

    private OutboundEndpointDTO copyOutbound(OutboundEndpointDTO source) {
        if (source == null) {
            return null;
        }
        return new OutboundEndpointDTO(
                source.getEndpointId(),
                source.getName(),
                source.getTargetUrl(),
                source.getTopic(),
                source.getMethod(),
                source.getProtocol(),
                source.getDescription(),
                source.getEnabled()
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    public record ServiceMetadata(String serviceName, String baseUrl, String description) {
    }
}
