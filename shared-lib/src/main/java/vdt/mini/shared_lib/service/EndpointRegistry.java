package vdt.mini.shared_lib.service;

import org.springframework.stereotype.Component;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import vdt.mini.shared_lib.document.InboundEndpointDTO;
import vdt.mini.shared_lib.document.OutboundEndpointDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class EndpointRegistry {
    private final PathPatternParser parser = new PathPatternParser();
    private final CopyOnWriteArrayList<InboundHttpEndpoint> inboundHttpEndpoints = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<InboundMqEndpoint> inboundMqEndpoints = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OutboundEndpoint> outboundEndpoints = new CopyOnWriteArrayList<>();

    public void replaceAll(List<InboundEndpointDTO> inbounds, List<OutboundEndpointDTO> outbounds) {
        List<InboundHttpEndpoint> nextHttp = new ArrayList<>();
        List<InboundMqEndpoint> nextMq = new ArrayList<>();
        for (InboundEndpointDTO endpoint : safeList(inbounds)) {
            if (endpoint == null || endpoint.getEndpointId() == null || Boolean.FALSE.equals(endpoint.getEnabled())) {
                continue;
            }
            String protocol = normalize(endpoint.getProtocol());
            if ("HTTP".equals(protocol) || "WEBHOOK".equals(protocol)) {
                if (hasText(endpoint.getMethod()) && hasText(endpoint.getPath())) {
                    nextHttp.add(new InboundHttpEndpoint(endpoint.getEndpointId(), endpoint.getName(), normalize(endpoint.getMethod()),
                            endpoint.getPath(), protocol, parser.parse(endpoint.getPath())));
                }
            } else if ("MQ".equals(protocol) && hasText(endpoint.getTopic())) {
                nextMq.add(new InboundMqEndpoint(endpoint.getEndpointId(), endpoint.getName(), endpoint.getTopic(), protocol));
            }
        }
        List<OutboundEndpoint> nextOutbound = new ArrayList<>();
        for (OutboundEndpointDTO endpoint : safeList(outbounds)) {
            if (endpoint == null || endpoint.getEndpointId() == null || Boolean.FALSE.equals(endpoint.getEnabled())) {
                continue;
            }
            nextOutbound.add(new OutboundEndpoint(endpoint.getEndpointId(), endpoint.getName(), normalize(endpoint.getProtocol()),
                    normalize(endpoint.getMethod()), hasText(endpoint.getTargetUrl()) ? endpoint.getTargetUrl() : endpoint.getTopic()));
        }
        inboundHttpEndpoints.clear();
        inboundHttpEndpoints.addAll(nextHttp);
        inboundMqEndpoints.clear();
        inboundMqEndpoints.addAll(nextMq);
        outboundEndpoints.clear();
        outboundEndpoints.addAll(nextOutbound);
    }

    public Optional<InboundHttpEndpoint> findInboundHttp(String method, String path) {
        String normalizedMethod = normalize(method);
        String lookupPath = path == null || path.isBlank() ? "/" : path;
        return inboundHttpEndpoints.stream()
                .filter(endpoint -> endpoint.method().equals(normalizedMethod))
                .filter(endpoint -> endpoint.pattern().matches(PathContainer.parsePath(lookupPath)))
                .findFirst();
    }

    public Optional<InboundMqEndpoint> findInboundMq(String topic) {
        return inboundMqEndpoints.stream().filter(endpoint -> endpoint.topic().equals(topic)).findFirst();
    }

    public List<OutboundEndpoint> outboundEndpoints() {
        return List.copyOf(outboundEndpoints);
    }

    public Optional<OutboundEndpoint> findOutBoundHttp(String serviceId, String protocol, String method, String targetUrl, String name) {
        String normalizedProtocol = normalize(protocol);
        String normalizedMethod = normalize(method);
        Optional<OutboundEndpoint> exactDestinationMatch = outboundEndpoints.stream()
                .filter(endpoint -> endpoint.protocol().equals(normalizedProtocol))
                .filter(endpoint -> endpoint.method().equals(normalizedMethod))
                .filter(endpoint -> equalsText(endpoint.destination(), targetUrl))
                .findFirst();
        if (exactDestinationMatch.isPresent()) {
            return exactDestinationMatch;
        }
        return outboundEndpoints.stream()
                .filter(endpoint -> endpoint.protocol().equals(normalizedProtocol))
                .filter(endpoint -> endpoint.method().equals(normalizedMethod))
                .filter(endpoint -> equalsText(endpoint.name(), name))
                .findFirst();
    }

    public Optional<OutboundEndpoint> findOutBoundMq(String serviceId, String protocol, String method, String topic, String name) {
        String normalizedProtocol = normalize(protocol);
        String normalizedMethod = normalize(method);
        Optional<OutboundEndpoint> exactTopicMatch = outboundEndpoints.stream()
                .filter(endpoint -> endpoint.protocol().equals(normalizedProtocol))
                .filter(endpoint -> !hasText(method) || endpoint.method().equals(normalizedMethod))
                .filter(endpoint -> equalsText(endpoint.destination(), topic))
                .findFirst();
        if (exactTopicMatch.isPresent()) {
            return exactTopicMatch;
        }
        return outboundEndpoints.stream()
                .filter(endpoint -> endpoint.protocol().equals(normalizedProtocol))
                .filter(endpoint -> !hasText(method) || endpoint.method().equals(normalizedMethod))
                .filter(endpoint -> equalsText(endpoint.name(), name))
                .findFirst();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean equalsText(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }

    private static <T> List<T> safeList(List<T> value) {
        return value == null ? List.of() : value;
    }

    public record InboundHttpEndpoint(String endpointId, String name, String method, String path, String protocol,
                                      PathPattern pattern) {
    }

    public record InboundMqEndpoint(String endpointId, String name, String topic, String protocol) {
    }

    public record OutboundEndpoint(String endpointId, String name, String protocol, String method, String destination) {
    }
}
