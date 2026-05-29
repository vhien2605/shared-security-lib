package vdt.mini.shared_lib.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import vdt.mini.shared_lib.document.SettingsChangeMessage;

@Component
public class RedisSettingsSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisSettingsSubscriber.class);

    private final ObjectMapper objectMapper;
    private final SecuritySettingsStore settingsStore;

    @Autowired
    public RedisSettingsSubscriber(ObjectMapper objectMapper, SecuritySettingsStore settingsStore) {
        this.objectMapper = objectMapper;
        this.settingsStore = settingsStore;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            SettingsChangeMessage change = objectMapper.readValue(message.getBody(), SettingsChangeMessage.class);
            log.info("Received settings change: type={}, endpointId={}, serviceId={}",
                    change.getType(), change.getEndpointId(), change.getServiceId());
            settingsStore.onSettingsChange(change);
        } catch (Exception e) {
            log.error("Failed to process Redis pub/sub message", e);
        }
    }
}
