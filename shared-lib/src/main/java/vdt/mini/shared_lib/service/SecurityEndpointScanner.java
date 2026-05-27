package vdt.mini.shared_lib.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import vdt.mini.shared_lib.annotation.InBoundSecurity;
import vdt.mini.shared_lib.annotation.OutBoundSecurity;
import vdt.mini.shared_lib.document.InboundEndpointDTO;
import vdt.mini.shared_lib.document.OutboundEndpointDTO;
import vdt.mini.shared_lib.document.ServiceRegistrationEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class SecurityEndpointScanner {

    private static final Logger log = LoggerFactory.getLogger(SecurityEndpointScanner.class);

    private final ApplicationContext applicationContext;
    private final IdentityManager identityManager;
    private final KafkaPublisher kafkaPublisher;

    @Value("${app.security.service.name:my-service}")
    private String serviceName;

    @Value("${app.security.service.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${app.security.service.description:}")
    private String serviceDescription;

    @Value("${app.security.registration.topic:security.endpoint.registration}")
    private String registrationTopic;

    @Value("${app.security.enabled:true}")
    private boolean enabled;

    @Autowired
    public SecurityEndpointScanner(ApplicationContext applicationContext,
                                   IdentityManager identityManager,
                                   KafkaPublisher kafkaPublisher) {
        this.applicationContext = applicationContext;
        this.identityManager = identityManager;
        this.kafkaPublisher = kafkaPublisher;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!enabled) {
            log.info("Security endpoint registration is disabled");
            return;
        }

        try {
            String serviceId = identityManager.getOrCreateServiceId();
            log.info("Scanning for security endpoints (serviceId={})", serviceId);

            List<InboundEndpointDTO> inbounds = scanInbounds();
            List<OutboundEndpointDTO> outbounds = scanOutbounds();

            ServiceRegistrationEvent event = new ServiceRegistrationEvent(
                    serviceId, serviceName, baseUrl, serviceDescription, inbounds, outbounds
            );

            kafkaPublisher.send(registrationTopic, event);
            log.info("Registered {} inbound and {} outbound endpoints for service '{}'",
                    inbounds.size(), outbounds.size(), serviceName);
        } catch (Exception e) {
            log.error("Failed to register security endpoints", e);
        }
    }

    private Set<Method> collectMethods(Object bean) {
        Set<Method> methods = new LinkedHashSet<>();
        Class<?> targetClass = org.springframework.util.ClassUtils.getUserClass(bean);
        methods.addAll(Arrays.asList(targetClass.getMethods()));
        for (Class<?> iface : bean.getClass().getInterfaces()) {
            if (!iface.getName().startsWith("org.springframework")) {
                methods.addAll(Arrays.asList(iface.getMethods()));
            }
        }
        return methods;
    }

    private List<InboundEndpointDTO> scanInbounds() {
        List<InboundEndpointDTO> result = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        String[] beanNames = applicationContext.getBeanDefinitionNames();

        for (String beanName : beanNames) {
            Object bean = applicationContext.getBean(beanName);
            Set<Method> methods = collectMethods(bean);

            for (Method method : methods) {
                InBoundSecurity annotation = method.getAnnotation(InBoundSecurity.class);
                if (annotation == null) {
                    continue;
                }

                String path = annotation.path();
                String topic = annotation.topic();
                String methodName = annotation.method().name();
                String protocolName = annotation.protocol().name();

                String destination = annotation.protocol().name().equals("MQ") ? topic : path;
                String compositeKey = identityManager.buildCompositeKey(protocolName, methodName, destination);
                if (!seenKeys.add(compositeKey)) {
                    continue;
                }
                InboundEndpointDTO dto = identityManager.getOrCreateInbound(compositeKey,
                        new InboundEndpointDTO(null, annotation.name(), path, topic,
                                methodName, protocolName, annotation.description()));
                result.add(dto);

                log.debug("Found @InBoundSecurity: name={}, path={}, topic={}, key={}",
                        annotation.name(), path, topic, compositeKey);
            }
        }
        return result;
    }

    private List<OutboundEndpointDTO> scanOutbounds() {
        List<OutboundEndpointDTO> result = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        String[] beanNames = applicationContext.getBeanDefinitionNames();

        for (String beanName : beanNames) {
            Object bean = applicationContext.getBean(beanName);
            Set<Method> methods = collectMethods(bean);

            for (Method method : methods) {
                OutBoundSecurity annotation = method.getAnnotation(OutBoundSecurity.class);
                if (annotation == null) {
                    continue;
                }

                String targetUrl = annotation.targetUrl();
                String topic = annotation.topic();
                String methodName = annotation.method().name();
                String protocolName = annotation.protocol().name();

                String destination = annotation.protocol().name().equals("MQ") ? topic : targetUrl;
                String compositeKey = identityManager.buildCompositeKey(protocolName, methodName, destination);
                if (!seenKeys.add(compositeKey)) {
                    continue;
                }
                OutboundEndpointDTO dto = identityManager.getOrCreateOutbound(compositeKey,
                        new OutboundEndpointDTO(null, annotation.name(), targetUrl, topic,
                                methodName, protocolName, annotation.description()));
                result.add(dto);

                log.debug("Found @OutBoundSecurity: name={}, targetUrl={}, topic={}, key={}",
                        annotation.name(), targetUrl, topic, compositeKey);
            }
        }
        return result;
    }
}
