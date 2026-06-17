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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class SecurityEndpointScanner {

    private static final Logger log = LoggerFactory.getLogger(SecurityEndpointScanner.class);

    private final ApplicationContext applicationContext;
    private final IdentityManager identityManager;
    private final KafkaPublisher kafkaPublisher;
    private final SecuritySettingsStore securitySettingsStore;
    private final EndpointRegistry endpointRegistry;

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

    @Value("${app.security.settings.sync.enabled:true}")
    private boolean syncEnabled;

    @Autowired
    public SecurityEndpointScanner(ApplicationContext applicationContext,
                                    IdentityManager identityManager,
                                    KafkaPublisher kafkaPublisher,
                                    SecuritySettingsStore securitySettingsStore,
                                    EndpointRegistry endpointRegistry) {
        this.applicationContext = applicationContext;
        this.identityManager = identityManager;
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
            String serviceId = identityManager.getOrCreateServiceId();
            log.info("Scanning for security endpoints (serviceId={})", serviceId);

            ScanResult<InboundEndpointDTO> inboundScan = scanInbounds();
            ScanResult<OutboundEndpointDTO> outboundScan = scanOutbounds();

            List<InboundEndpointDTO> inbounds = new ArrayList<>(inboundScan.endpoints());
            List<OutboundEndpointDTO> outbounds = new ArrayList<>(outboundScan.endpoints());

            appendStaleInbounds(inbounds, inboundScan.keys());
            appendStaleOutbounds(outbounds, outboundScan.keys());

            endpointRegistry.replaceAll(inbounds, outbounds);

            IdentityManager.ServiceMetadata metadata = identityManager.ensureServiceMetadata(
                    serviceName, baseUrl, serviceDescription
            );

            ServiceRegistrationEvent event = new ServiceRegistrationEvent(
                    serviceId, metadata.serviceName(), metadata.baseUrl(), metadata.description(), inbounds, outbounds
            );

            kafkaPublisher.send(registrationTopic, event);
            log.info("Registered {} inbound and {} outbound endpoints for service '{}'",
                    inbounds.size(), outbounds.size(), metadata.serviceName());

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

    private ScanResult<InboundEndpointDTO> scanInbounds() {
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
                                methodName, protocolName, annotation.description(), true));
                dto.setEnabled(true);
                result.add(dto);

                log.debug("Found @InBoundSecurity: name={}, path={}, topic={}, key={}",
                        annotation.name(), path, topic, compositeKey);
            }
        }
        return new ScanResult<>(result, seenKeys);
    }

    private ScanResult<OutboundEndpointDTO> scanOutbounds() {
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
                                methodName, protocolName, annotation.description(), true));
                dto.setEnabled(true);
                result.add(dto);

                log.debug("Found @OutBoundSecurity: name={}, targetUrl={}, topic={}, key={}",
                        annotation.name(), targetUrl, topic, compositeKey);
            }
        }
        return new ScanResult<>(result, seenKeys);
    }

    private void appendStaleInbounds(List<InboundEndpointDTO> target, Set<String> currentKeys) {
        Map<String, InboundEndpointDTO> known = new LinkedHashMap<>(identityManager.getKnownInbounds());
        for (Map.Entry<String, InboundEndpointDTO> entry : known.entrySet()) {
            if (currentKeys.contains(entry.getKey())) {
                continue;
            }
            InboundEndpointDTO dto = entry.getValue();
            if (dto == null || dto.getEndpointId() == null) {
                continue;
            }
            dto.setEnabled(false);
            target.add(dto);
        }
    }

    private void appendStaleOutbounds(List<OutboundEndpointDTO> target, Set<String> currentKeys) {
        Map<String, OutboundEndpointDTO> known = new LinkedHashMap<>(identityManager.getKnownOutbounds());
        for (Map.Entry<String, OutboundEndpointDTO> entry : known.entrySet()) {
            if (currentKeys.contains(entry.getKey())) {
                continue;
            }
            OutboundEndpointDTO dto = entry.getValue();
            if (dto == null || dto.getEndpointId() == null) {
                continue;
            }
            dto.setEnabled(false);
            target.add(dto);
        }
    }

    private record ScanResult<T>(List<T> endpoints, Set<String> keys) {
    }
}
