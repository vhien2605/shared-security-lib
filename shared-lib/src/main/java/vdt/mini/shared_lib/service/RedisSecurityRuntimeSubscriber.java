package vdt.mini.shared_lib.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import vdt.mini.shared_lib.document.SecurityRuntimeChangeMessage;

@Component
public class RedisSecurityRuntimeSubscriber implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(RedisSecurityRuntimeSubscriber.class);

    private final ObjectMapper objectMapper;
    private final SecuritySettingsStore settingsStore;

    public RedisSecurityRuntimeSubscriber(ObjectMapper objectMapper, SecuritySettingsStore settingsStore) {
        this.objectMapper = objectMapper;
        this.settingsStore = settingsStore;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            SecurityRuntimeChangeMessage change = objectMapper.readValue(message.getBody(), SecurityRuntimeChangeMessage.class);
            settingsStore.onRuntimeChange(change);
        } catch (Exception ex) {
            log.error("Failed to process Redis runtime event", ex);
        }
    }
}
