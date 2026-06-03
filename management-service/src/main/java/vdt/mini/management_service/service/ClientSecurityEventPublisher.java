package vdt.mini.management_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import vdt.mini.management_service.dto.event.ClientSecurityConfigEvent;

@Service
public class ClientSecurityEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(ClientSecurityEventPublisher.class);
    private static final String CHANNEL = "client-security-config-events";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ClientSecurityEventPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(ClientSecurityConfigEvent event) {
        try {
            redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(event));
            log.info("Published client security event eventType={}, clientId={}, accessRuleId={}",
                    event.getEventType(), event.getClientId(), event.getAccessRuleId());
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize client security event eventType={}, clientId={}, accessRuleId={}",
                    event.getEventType(), event.getClientId(), event.getAccessRuleId(), ex);
        } catch (RuntimeException ex) {
            log.error("Failed to publish client security event eventType={}, clientId={}, accessRuleId={}",
                    event.getEventType(), event.getClientId(), event.getAccessRuleId(), ex);
        }
    }
}
