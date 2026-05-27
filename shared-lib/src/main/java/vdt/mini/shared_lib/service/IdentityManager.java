package vdt.mini.shared_lib.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vdt.mini.shared_lib.document.InboundEndpointDTO;
import vdt.mini.shared_lib.document.OutboundEndpointDTO;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class IdentityManager {

    private static final Logger log = LoggerFactory.getLogger(IdentityManager.class);

    private final File identityFile;
    private final ObjectMapper objectMapper;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private String serviceId;
    private Map<String, InboundEndpointDTO> inbounds;
    private Map<String, OutboundEndpointDTO> outbounds;

    public IdentityManager(@Value("${app.security.identity-file:security-identity.json}") String identityFilePath) {
        this.identityFile = new File(identityFilePath);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        load();
    }

    private void load() {
        lock.writeLock().lock();
        try {
            if (identityFile.exists()) {
                Map<String, Object> data = objectMapper.readValue(identityFile,
                        new TypeReference<Map<String, Object>>() {});
                this.serviceId = (String) data.getOrDefault("serviceId", UUID.randomUUID().toString());

                Object inboundsRaw = data.get("inbounds");
                if (inboundsRaw instanceof Map) {
                    this.inbounds = objectMapper.convertValue(inboundsRaw,
                            new TypeReference<Map<String, InboundEndpointDTO>>() {});
                } else {
                    this.inbounds = new LinkedHashMap<>();
                }

                Object outboundsRaw = data.get("outbounds");
                if (outboundsRaw instanceof Map) {
                    this.outbounds = objectMapper.convertValue(outboundsRaw,
                            new TypeReference<Map<String, OutboundEndpointDTO>>() {});
                } else {
                    this.outbounds = new LinkedHashMap<>();
                }

                log.info("Loaded identity: serviceId={}, inbounds={}, outbounds={}",
                        serviceId, inbounds.size(), outbounds.size());
            } else {
                this.serviceId = UUID.randomUUID().toString();
                this.inbounds = new LinkedHashMap<>();
                this.outbounds = new LinkedHashMap<>();
                log.info("No identity file found, generated serviceId={}", serviceId);
                save();
            }
        } catch (IOException e) {
            log.warn("Failed to load identity file, generating new IDs", e);
            this.serviceId = UUID.randomUUID().toString();
            this.inbounds = new LinkedHashMap<>();
            this.outbounds = new LinkedHashMap<>();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String getOrCreateServiceId() {
        lock.readLock().lock();
        try {
            return serviceId;
        } finally {
            lock.readLock().unlock();
        }
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
            log.info("Generated new inbound endpointId={} for key={}", newId, compositeKey);
            save();
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
            log.info("Generated new outbound endpointId={} for key={}", newId, compositeKey);
            save();
            return dto;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String buildCompositeKey(String protocol, String method, String pathOrTopic) {
        return protocol + "_" + method + "_" + (pathOrTopic != null ? pathOrTopic : "");
    }

    private void save() {
        try {
            File parentDir = identityFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("serviceId", serviceId);
            data.put("inbounds", inbounds);
            data.put("outbounds", outbounds);
            objectMapper.writeValue(identityFile, data);
            log.debug("Saved identity file to {}", identityFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to save identity file", e);
        }
    }
}
