package vdt.mini.management_service.service;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import vdt.mini.management_service.dto.request.SecurityLogSearchRequest;
import vdt.mini.management_service.dto.response.SecurityLogPageResponse;
import vdt.mini.management_service.dto.response.SecurityLogResponse;
import vdt.mini.management_service.entity.SecurityEventLog;
import vdt.mini.management_service.exception.AppException;
import vdt.mini.management_service.util.enums.ErrorCode;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SecurityLogQueryService {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final Set<String> SORT_DIRECTIONS = Set.of("ASC", "DESC");
    private static final Map<String, Set<String>> ALLOWED_VALUES = Map.of(
            "flowType", Set.of("INBOUND_HTTP", "INBOUND_MQ_LISTENER", "OUTBOUND_HTTP", "OUTBOUND_MQ"),
            "direction", Set.of("INBOUND", "OUTBOUND"),
            "protocol", Set.of("HTTP", "HTTPS", "MQ", "KAFKA", "RABBITMQ", "GRPC"),
            "method", Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "CONNECT", "TRACE", "PUBLISH", "CONSUME"),
            "status", Set.of("SUCCESS", "FAILED", "DENIED", "ERROR", "TIMEOUT", "RETRY", "ROLLBACK"),
            "alertSeverity", Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL", "INFO", "WARNING")
    );
    private static final Set<String> KEYWORD_SUBFIELD_FILTERS = Set.of("serviceName", "endpointName");

    private final ElasticsearchOperations elasticsearchOperations;

    public SecurityLogPageResponse search(SecurityLogSearchRequest request) {
        NormalizedRequest normalized = normalizeAndValidate(request);
        List<Query> filters = buildFilters(normalized);
        Query query = filters.isEmpty()
                ? Query.of(q -> q.matchAll(m -> m))
                : Query.of(q -> q.bool(b -> b.filter(filters)));
        PageRequest pageable = PageRequest.of(
                normalized.page(),
                normalized.size(),
                Sort.by(Sort.Direction.fromString(normalized.sortDirection()), "timestamp")
        );
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(query)
                .withPageable(pageable)
                .build();

        SearchHits<SecurityEventLog> hits = elasticsearchOperations.search(nativeQuery, SecurityEventLog.class);
        List<SecurityLogResponse> content = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(this::toResponse)
                .toList();
        long totalElements = hits.getTotalHits();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / normalized.size());

        return SecurityLogPageResponse.builder()
                .content(content)
                .page(normalized.page())
                .size(normalized.size())
                .totalElements(totalElements)
                .totalPages(totalPages)
                .first(normalized.page() == 0)
                .last(totalPages == 0 || normalized.page() + 1 >= totalPages)
                .build();
    }

    private List<Query> buildFilters(NormalizedRequest request) {
        List<Query> filters = new ArrayList<>();
        addRangeFilter(filters, request.from(), request.to());
        addTermFilter(filters, "serviceId", request.serviceId());
        addTermFilter(filters, "serviceName", request.serviceName());
        addTermFilter(filters, "endpointId", request.endpointId());
        addTermFilter(filters, "endpointName", request.endpointName());
        addTermFilter(filters, "flowType", request.flowType());
        addTermFilter(filters, "direction", request.direction());
        addTermFilter(filters, "protocol", request.protocol());
        addTermFilter(filters, "method", request.method());
        addTermFilter(filters, "status", request.status());
        addTermFilter(filters, "resultCode", request.resultCode());
        addTermFilter(filters, "errorCode", request.errorCode());
        addTermFilter(filters, "clientId", request.clientId());
        addTermFilter(filters, "clientKey", request.clientKey());
        addTermFilter(filters, "traceId", request.traceId());
        addTermFilter(filters, "correlationId", request.correlationId());
        addTermFilter(filters, "alertSeverity", request.alertSeverity());
        addTargetFilter(filters, request.target());
        return filters;
    }

    private void addRangeFilter(List<Query> filters, String from, String to) {
        if (from == null && to == null) return;
        filters.add(Query.of(q -> q.range(r -> r.date(d -> {
            d.field("timestamp");
            if (from != null) d.gte(from);
            if (to != null) d.lte(to);
            return d;
        }))));
    }

    private void addTermFilter(List<Query> filters, String field, String value) {
        if (value == null) return;
        String esField = KEYWORD_SUBFIELD_FILTERS.contains(field) ? field + ".keyword" : field;
        filters.add(Query.of(q -> q.term(t -> t.field(esField).value(FieldValue.of(value)))));
    }

    private void addTargetFilter(List<Query> filters, String target) {
        if (target == null) return;
        String wildcardValue = "*" + escapeWildcard(target) + "*";
        List<Query> targetQueries = List.of(
                wildcardQuery("path.keyword", wildcardValue),
                wildcardQuery("targetUrl.keyword", wildcardValue),
                wildcardQuery("topic", wildcardValue)
        );
        filters.add(Query.of(q -> q.bool(b -> b.should(targetQueries).minimumShouldMatch("1"))));
    }

    private Query wildcardQuery(String field, String value) {
        return Query.of(q -> q.wildcard(w -> w.field(field).value(value).caseInsensitive(true)));
    }

    private String escapeWildcard(String value) {
        return value.replace("\\", "\\\\").replace("*", "\\*").replace("?", "\\?");
    }

    private NormalizedRequest normalizeAndValidate(SecurityLogSearchRequest request) {
        int page = request.getPage() == null ? DEFAULT_PAGE : request.getPage();
        int size = request.getSize() == null ? DEFAULT_SIZE : request.getSize();
        if (page < 0) throw invalidInput("page must be greater than or equal to 0");
        if (size <= 0) throw invalidInput("size must be greater than 0");
        if (size > MAX_SIZE) throw invalidInput("size must not be greater than " + MAX_SIZE);

        String sortDirection = normalizeUpper(request.getSortDirection());
        sortDirection = sortDirection == null ? "DESC" : sortDirection;
        if (!SORT_DIRECTIONS.contains(sortDirection)) throw invalidInput("sortDirection must be ASC or DESC");

        String from = normalizeInstant(request.getFrom(), "from");
        String to = normalizeInstant(request.getTo(), "to");
        if (from != null && to != null && Instant.parse(from).isAfter(Instant.parse(to))) {
            throw invalidInput("from must not be after to");
        }

        String flowType = normalizeEnum("flowType", request.getFlowType());
        String direction = normalizeEnum("direction", request.getDirection());
        String protocol = normalizeEnum("protocol", request.getProtocol());
        String method = normalizeEnum("method", request.getMethod());
        String status = normalizeEnum("status", request.getStatus());
        String alertSeverity = normalizeEnum("alertSeverity", request.getAlertSeverity());

        return new NormalizedRequest(
                page,
                size,
                sortDirection,
                from,
                to,
                trimToNull(request.getServiceId()),
                trimToNull(request.getServiceName()),
                trimToNull(request.getEndpointId()),
                trimToNull(request.getEndpointName()),
                flowType,
                direction,
                protocol,
                method,
                status,
                trimToNull(request.getResultCode()),
                trimToNull(request.getErrorCode()),
                trimToNull(request.getClientId()),
                trimToNull(request.getClientKey()),
                trimToNull(request.getTraceId()),
                trimToNull(request.getCorrelationId()),
                alertSeverity,
                trimToNull(request.getTarget())
        );
    }

    private String normalizeInstant(String value, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized == null) return null;
        try {
            return Instant.parse(normalized).toString();
        } catch (DateTimeParseException exception) {
            throw invalidInput(fieldName + " must be a valid ISO-8601 instant");
        }
    }

    private String normalizeEnum(String fieldName, String value) {
        String normalized = normalizeUpper(value);
        if (normalized == null) return null;
        Set<String> allowedValues = ALLOWED_VALUES.get(fieldName);
        if (allowedValues != null && !allowedValues.contains(normalized)) {
            throw invalidInput(fieldName + " is invalid");
        }
        return normalized;
    }

    private String normalizeUpper(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private AppException invalidInput(String message) {
        return new AppException(ErrorCode.INVALID_INPUT, message);
    }

    private SecurityLogResponse toResponse(SecurityEventLog log) {
        return SecurityLogResponse.builder()
                .id(log.getId())
                .timestamp(log.getTimestamp())
                .traceId(log.getTraceId())
                .correlationId(log.getCorrelationId())
                .flowType(log.getFlowType())
                .direction(log.getDirection())
                .serviceId(log.getServiceId())
                .serviceName(log.getServiceName())
                .endpointId(log.getEndpointId())
                .endpointName(log.getEndpointName())
                .protocol(log.getProtocol())
                .method(log.getMethod())
                .path(log.getPath())
                .targetUrl(log.getTargetUrl())
                .topic(log.getTopic())
                .consumerGroup(log.getConsumerGroup())
                .producerClientId(log.getProducerClientId())
                .clientId(log.getClientId())
                .clientKey(log.getClientKey())
                .sourceIp(log.getSourceIp())
                .authType(log.getAuthType())
                .denyReason(log.getDenyReason())
                .alertSeverity(log.getAlertSeverity())
                .status(log.getStatus())
                .resultCode(log.getResultCode())
                .errorCode(log.getErrorCode())
                .requestSizeBytes(log.getRequestSizeBytes())
                .messageSizeBytes(log.getMessageSizeBytes())
                .responseSizeBytes(log.getResponseSizeBytes())
                .durationMs(log.getDurationMs())
                .thresholdMs(log.getThresholdMs())
                .timeoutMs(log.getTimeoutMs())
                .rateLimit(log.getRateLimit())
                .rateLimitWindowSeconds(log.getRateLimitWindowSeconds())
                .remainingQuota(log.getRemainingQuota())
                .retentionDays(log.getRetentionDays())
                .retentionBucket(log.getRetentionBucket())
                .retryCount(log.getRetryCount())
                .retryAttempt(log.getRetryAttempt())
                .retryBackoffMs(log.getRetryBackoffMs())
                .rollbackStrategy(log.getRollbackStrategy())
                .build();
    }

    private record NormalizedRequest(
            int page,
            int size,
            String sortDirection,
            String from,
            String to,
            String serviceId,
            String serviceName,
            String endpointId,
            String endpointName,
            String flowType,
            String direction,
            String protocol,
            String method,
            String status,
            String resultCode,
            String errorCode,
            String clientId,
            String clientKey,
            String traceId,
            String correlationId,
            String alertSeverity,
            String target
    ) {
    }
}
