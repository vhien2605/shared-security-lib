package vdt.mini.shared_lib.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import vdt.mini.shared_lib.document.InboundSettingsDTO;
import vdt.mini.shared_lib.document.OutboundSettingsDTO;
import vdt.mini.shared_lib.document.SettingsChangeMessage;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SecuritySettingsStore {

    private static final Logger log = LoggerFactory.getLogger(SecuritySettingsStore.class);

    private final ConcurrentHashMap<String, InboundSettingsDTO> inboundSettings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, OutboundSettingsDTO> outboundSettings = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public SecuritySettingsStore(
            @Qualifier("securityRedisTemplate") StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public InboundSettingsDTO getInboundSettings(String endpointId) {
        return inboundSettings.get(endpointId);
    }

    public OutboundSettingsDTO getOutboundSettings(String endpointId) {
        return outboundSettings.get(endpointId);
    }

    public void pollFromRedis(List<String> inboundIds, List<String> outboundIds) {
        for (String id : inboundIds) {
            try {
                String json = redisTemplate.opsForValue().get("security:config:inbound:" + id);
                if (json != null) {
                    InboundSettingsDTO dto = objectMapper.readValue(json, InboundSettingsDTO.class);
                    inboundSettings.put(id, dto);
                    log.debug("Loaded inbound settings from Redis: endpointId={}", id);
                }
            } catch (Exception e) {
                log.warn("Failed to poll inbound settings from Redis for endpointId={}", id, e);
            }
        }
        for (String id : outboundIds) {
            try {
                String json = redisTemplate.opsForValue().get("security:config:outbound:" + id);
                if (json != null) {
                    OutboundSettingsDTO dto = objectMapper.readValue(json, OutboundSettingsDTO.class);
                    outboundSettings.put(id, dto);
                    log.debug("Loaded outbound settings from Redis: endpointId={}", id);
                }
            } catch (Exception e) {
                log.warn("Failed to poll outbound settings from Redis for endpointId={}", id, e);
            }
        }
    }

    public void onSettingsChange(SettingsChangeMessage message) {
        if (message == null) {
            log.warn("Received null settings change message");
            return;
        }
        if ("INBOUND".equals(message.getType())) {
            InboundSettingsDTO config = objectMapper.convertValue(message.getConfig(), InboundSettingsDTO.class);
            if (config != null) {
                inboundSettings.put(message.getEndpointId(), config);
                log.info("Updated inbound settings from pub/sub: endpointId={}, config={}", message.getEndpointId(), config);
            }
        } else if ("OUTBOUND".equals(message.getType())) {
            OutboundSettingsDTO config = objectMapper.convertValue(message.getConfig(), OutboundSettingsDTO.class);
            if (config != null) {
                outboundSettings.put(message.getEndpointId(), config);
                log.info("Updated outbound settings from pub/sub: endpointId={}, config={}", message.getEndpointId(), config);
            }
        } else {
            log.warn("Unknown settings change message type: {}", message.getType());
        }
    }
}
