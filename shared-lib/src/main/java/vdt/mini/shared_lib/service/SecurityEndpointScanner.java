package vdt.mini.shared_lib.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
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
    private final KafkaPublisher kafkaPublisher;
    private final SecuritySettingsStore securitySettingsStore;
    private final EndpointRegistry endpointRegistry;

    @Value("${app.security.service.name:my-service}")
    private String serviceName;

    @Value("${app.security.namespace:default}")
    private String namespace;

    @Value("${app.security.service.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${app.security.service.description:}")
    private String serviceDescription;

    @Value("${app.security.registration.topic:security.endpoint.registration}")
    private String registrationTopic;

    @Value("${app.security.enabled:true}")
    private boolean enabled;

    @Value("${app.security.settings.sync.enabled:true}")
    private boolean syncEnabled;

    @Value("${app.security.registration.enabled:true}")
    private boolean registrationEnabled;

    @Autowired
    public SecurityEndpointScanner(ApplicationContext applicationContext,
                                   KafkaPublisher kafkaPublisher,
                                   SecuritySettingsStore securitySettingsStore,
                                   EndpointRegistry endpointRegistry) {
        this.applicationContext = applicationContext;
        this.kafkaPublisher = kafkaPublisher;
        this.securitySettingsStore = securitySettingsStore;
        this.endpointRegistry = endpointRegistry;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!enabled) {
            log.info("Security endpoint registration is disabled");
            return;
        }

        try {
            String serviceId = SecurityIdGenerator.serviceId(namespace, serviceName);
            log.info("Scanning for security endpoints (namespace={}, serviceName={}, serviceId={}, registrationEnabled={})",
                    namespace, serviceName, serviceId, registrationEnabled);

            List<InboundEndpointDTO> inbounds = scanInbounds(serviceId);
            List<OutboundEndpointDTO> outbounds = scanOutbounds(serviceId);


            endpointRegistry.replaceAll(inbounds, outbounds);

            if (registrationEnabled) {
                ServiceRegistrationEvent event = new ServiceRegistrationEvent(
                        serviceId, serviceName, baseUrl, serviceDescription, inbounds, outbounds
                );
                kafkaPublisher.send(registrationTopic, event);
                log.info("Registered {} inbound and {} outbound endpoints for service '{}'",
                        inbounds.size(), outbounds.size(), serviceName);
            } else {
                log.info("Security registration follower mode active; skipped Kafka registration publish serviceId={}",
                        serviceId);
            }

            // Chủ động poll Redis cache để lấy settings (nếu có) — không đợi pub/sub
            if (syncEnabled) {
                List<String> inboundIds = inbounds.stream()
                        .map(InboundEndpointDTO::getEndpointId)
                        .toList();
                List<String> outboundIds = outbounds.stream()
                        .map(OutboundEndpointDTO::getEndpointId)
                        .toList();
                securitySettingsStore.pollRuntimeFromRedis(serviceId, inboundIds, outboundIds);
                log.info("Security endpoint scan completed serviceId={} inboundCount={} outboundCount={}",
                        serviceId, inboundIds.size(), outboundIds.size());
            }
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

    private List<InboundEndpointDTO> scanInbounds(String serviceId) {
        List<InboundEndpointDTO> result = new ArrayList<>();
        Set<String> seenCanonicalIdentities = new LinkedHashSet<>();
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
                String canonicalIdentity = SecurityIdGenerator.canonicalEndpointIdentity(
                        serviceId, "INBOUND", protocolName, methodName, destination, "");
                if (!seenCanonicalIdentities.add(canonicalIdentity)) {
                    log.warn("Duplicate @InBoundSecurity skipped beanName={} methodName={} endpointName={} canonicalIdentity={}",
                            beanName, method.getName(), annotation.name(), canonicalIdentity);
                    continue;
                }
                String endpointId = SecurityIdGenerator.endpointId(
                        serviceId, "INBOUND", protocolName, methodName, destination, "");
                InboundEndpointDTO dto = new InboundEndpointDTO(endpointId, annotation.name(), path, topic,
                        methodName, protocolName, annotation.description(), true);
                result.add(dto);

                log.debug("Found @InBoundSecurity: name={}, path={}, topic={}, endpointId={}",
                        annotation.name(), path, topic, endpointId);
            }
        }
        return result;
    }

    private List<OutboundEndpointDTO> scanOutbounds(String serviceId) {
        List<OutboundEndpointDTO> result = new ArrayList<>();
        Set<String> seenCanonicalIdentities = new LinkedHashSet<>();
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
                String canonicalIdentity = SecurityIdGenerator.canonicalEndpointIdentity(
                        serviceId, "OUTBOUND", protocolName, methodName, destination, "");
                if (!seenCanonicalIdentities.add(canonicalIdentity)) {
                    log.warn("Duplicate @OutBoundSecurity skipped beanName={} methodName={} endpointName={} canonicalIdentity={}",
                            beanName, method.getName(), annotation.name(), canonicalIdentity);
                    continue;
                }
                String endpointId = SecurityIdGenerator.endpointId(
                        serviceId, "OUTBOUND", protocolName, methodName, destination, "");
                OutboundEndpointDTO dto = new OutboundEndpointDTO(endpointId, annotation.name(), targetUrl, topic,
                        methodName, protocolName, annotation.description(), true);
                result.add(dto);

                log.debug("Found @OutBoundSecurity: name={}, targetUrl={}, topic={}, endpointId={}",
                        annotation.name(), targetUrl, topic, endpointId);
            }
        }
        return result;
    }
}
