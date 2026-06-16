package vdt.mini.management_service.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import vdt.mini.management_service.dto.event.ServiceRegistrationEvent;
import vdt.mini.management_service.service.RegistrationService;

@Component
public class RegistrationConsumer {

    private static final Logger log = LoggerFactory.getLogger(RegistrationConsumer.class);

    private final ObjectMapper objectMapper;
    private final RegistrationService registrationService;

    public RegistrationConsumer(ObjectMapper objectMapper, RegistrationService registrationService) {
        this.objectMapper = objectMapper;
        this.registrationService = registrationService;
    }

    @KafkaListener(topics = "security.endpoint.registration", groupId = "management-group")
    public void consume(String message) {
        try {
            ServiceRegistrationEvent event = objectMapper.readValue(message, ServiceRegistrationEvent.class);
            log.info("Received registration: service={}, inbounds={}, outbounds={}",
                    event.getServiceName(), event.getInbounds().size(), event.getOutbounds().size());
            registrationService.processRegistration(event);
        } catch (Exception e) {
            log.error("Failed to process registration message: {}", message, e);
        }
    }
}
