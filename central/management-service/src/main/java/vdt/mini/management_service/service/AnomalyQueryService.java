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
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;
import vdt.mini.management_service.dto.request.AnomalySearchRequest;
import vdt.mini.management_service.dto.response.AnomalyDetailResponse;
import vdt.mini.management_service.dto.response.AnomalyListItemResponse;
import vdt.mini.management_service.dto.response.AnomalyPageResponse;
import vdt.mini.management_service.dto.response.AnomalyStatisticsResponse;
import vdt.mini.management_service.entity.AnomalyDocument;
import vdt.mini.management_service.exception.AppException;
import vdt.mini.management_service.util.enums.AnomalyDecision;
import vdt.mini.management_service.util.enums.AnomalyType;
import vdt.mini.management_service.util.enums.ErrorCode;
import vdt.mini.management_service.util.enums.IncidentStatus;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AnomalyQueryService {
    private static final String INDEX_PATTERN = "security-anomalies-*";
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int MAX_STATISTICS_DOCS = 10_000;
    private static final Set<String> SORT_DIRECTIONS = Set.of("ASC", "DESC");
    private static final Set<String> LEVELS = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> SOURCE_TYPES = Set.of("RUNTIME_SECURITY_LOG", "LOG_BASELINE", "BEHAVIOR_BASELINE", "MANUAL");
    private static final Set<String> FLOW_TYPES = Set.of("INBOUND_HTTP", "INBOUND_MQ_LISTENER", "OUTBOUND_HTTP", "OUTBOUND_MQ");
    private static final Set<String> DIRECTIONS = Set.of("INBOUND", "OUTBOUND");
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "timestamp", "timestamp",
            "riskScore", "riskScore",
            "anomalyLevel", "anomalyLevel",
            "matchedCount", "matchedCount",
            "serviceName", "serviceName.keyword",
            "endpointName", "endpointName.keyword"
    );

    private final ElasticsearchOperations elasticsearchOperations;

    public AnomalyPageResponse search(AnomalySearchRequest request) {
        NormalizedRequest normalized = normalizeAndValidate(request, true);
        SearchHits<AnomalyDocument> hits = elasticsearchOperations.search(buildSearchQuery(normalized), AnomalyDocument.class);
        List<AnomalyListItemResponse> content = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(this::toListItem)
                .toList();
        long totalElements = hits.getTotalHits();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / normalized.size());

        return AnomalyPageResponse.builder()
                .content(content)
                .page(normalized.page())
                .size(normalized.size())
                .totalElements(totalElements)
                .totalPages(totalPages)
                .first(normalized.page() == 0)
                .last(totalPages == 0 || normalized.page() + 1 >= totalPages)
                .build();
    }

    public AnomalyDetailResponse getDetail(String anomalyId) {
        String id = trimToNull(anomalyId);
        if (id == null) throw invalidInput("anomalyId is required");

        AnomalyDocument byAnomalyId = findFirst(List.of(term("anomalyId", id)), Sort.by(Sort.Direction.DESC, "lastSeenAt"));
        if (byAnomalyId != null) return toDetail(byAnomalyId);

        AnomalyDocument byDocumentId = elasticsearchOperations.get(id, AnomalyDocument.class, IndexCoordinates.of(INDEX_PATTERN));
        if (byDocumentId != null) return toDetail(byDocumentId);

        AnomalyDocument byIncidentId = findFirst(List.of(term("incidentId", id)), Sort.by(Sort.Direction.DESC, "lastSeenAt"));
        if (byIncidentId != null) return toDetail(byIncidentId);

        throw new AppException(ErrorCode.ANOMALY_NOT_FOUND, "Anomaly not found: " + id);
    }

    public AnomalyStatisticsResponse getStatistics(AnomalySearchRequest request) {
        NormalizedRequest normalized = normalizeAndValidate(request, false);
        NativeQuery query = NativeQuery.builder()
                .withQuery(buildBoolQuery(buildFilters(normalized)))
                .withPageable(PageRequest.of(0, MAX_STATISTICS_DOCS, Sort.by(Sort.Direction.DESC, "timestamp")))
                .build();
        SearchHits<AnomalyDocument> hits = elasticsearchOperations.search(query, AnomalyDocument.class);
        List<AnomalyDocument> documents = hits.getSearchHits().stream().map(SearchHit::getContent).toList();

        long critical = documents.stream().filter(document -> "CRITICAL".equals(document.getAnomalyLevel())).count();
        Set<String> incidentIds = new HashSet<>();
        Set<String> serviceIds = new HashSet<>();
        int riskSum = 0;
        int riskCount = 0;
        for (AnomalyDocument document : documents) {
            addNonBlank(incidentIds, document.getIncidentId());
            addNonBlank(serviceIds, document.getServiceId());
            Integer riskScore = effectiveRiskScore(document);
            if (riskScore != null) {
                riskSum += riskScore;
                riskCount++;
            }
        }

        return AnomalyStatisticsResponse.builder()
                .totalAnomalies(hits.getTotalHits())
                .criticalAnomalies(critical)
                .totalIncidents(incidentIds.size())
                .affectedServices(serviceIds.size())
                .averageRiskScore(riskCount == 0 ? 0 : Math.round((riskSum * 10.0 / riskCount)) / 10.0)
                .byLevel(topBuckets(documents, AnomalyDocument::getAnomalyLevel, 10))
                .byType(topBuckets(documents, AnomalyDocument::getAnomalyType, 10))
                .byDecision(topBuckets(documents, AnomalyDocument::getDecision, 10))
                .timeline(timelineBuckets(documents, normalized.from(), normalized.to()))
                .topServices(topBuckets(documents, document -> firstNonBlank(document.getServiceName(), document.getServiceId()), 10))
                .topEndpoints(topBuckets(documents, document -> firstNonBlank(document.getEndpointName(), document.getEndpointId()), 10))
                .topMatchedRules(topMatchedRules(documents))
                .build();
    }

    private NativeQuery buildSearchQuery(NormalizedRequest request) {
        return NativeQuery.builder()
                .withQuery(buildBoolQuery(buildFilters(request)))
                .withPageable(PageRequest.of(
                        request.page(),
                        request.size(),
                        Sort.by(Sort.Direction.fromString(request.direction()), request.sortField())
                ))
                .build();
    }

    private Query buildBoolQuery(List<Query> filters) {
        return filters.isEmpty()
                ? Query.of(q -> q.matchAll(m -> m))
                : Query.of(q -> q.bool(b -> b.filter(filters)));
    }

    private List<Query> buildFilters(NormalizedRequest request) {
        List<Query> filters = new ArrayList<>();
        addRangeFilter(filters, request.from(), request.to());
        addTermFilter(filters, "serviceId", request.serviceId());
        addTermFilter(filters, "endpointId", request.endpointId());
        addTermFilter(filters, "anomalyType", request.anomalyType());
        addTermFilter(filters, "anomalyLevel", request.anomalyLevel());
        addTermFilter(filters, "decision", request.decision());
        addTermFilter(filters, "sourceType", request.sourceType());
        addTermFilter(filters, "flowType", request.flowType());
        addTermFilter(filters, "direction", request.eventDirection());
        addTermFilter(filters, "incidentId", request.incidentId());
        addTermFilter(filters, "traceId", request.traceId());
        addRiskScoreFilter(filters, request.minRiskScore(), request.maxRiskScore());
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

    private void addRiskScoreFilter(List<Query> filters, Integer min, Integer max) {
        if (min == null && max == null) return;
        filters.add(Query.of(q -> q.range(r -> r.number(n -> {
            n.field("riskScore");
            if (min != null) n.gte((double) min);
            if (max != null) n.lte((double) max);
            return n;
        }))));
    }

    private void addTermFilter(List<Query> filters, String field, String value) {
        if (value == null) return;
        filters.add(term(field, value));
    }

    private Query term(String field, String value) {
        return Query.of(q -> q.term(t -> t.field(field).value(FieldValue.of(value))));
    }

    private AnomalyDocument findFirst(List<Query> filters, Sort sort) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(buildBoolQuery(filters))
                .withPageable(PageRequest.of(0, 1, sort))
                .build();
        return elasticsearchOperations.search(query, AnomalyDocument.class)
                .getSearchHits()
                .stream()
                .findFirst()
                .map(SearchHit::getContent)
                .orElse(null);
    }

    private NormalizedRequest normalizeAndValidate(AnomalySearchRequest request, boolean includePaging) {
        AnomalySearchRequest safeRequest = request == null ? new AnomalySearchRequest() : request;
        int page = safeRequest.getPage() == null ? DEFAULT_PAGE : safeRequest.getPage();
        int size = safeRequest.getSize() == null ? DEFAULT_SIZE : safeRequest.getSize();
        if (includePaging) {
            if (page < 0) throw invalidInput("page must be greater than or equal to 0");
            if (size <= 0) throw invalidInput("size must be greater than 0");
            if (size > MAX_SIZE) throw invalidInput("size must not be greater than " + MAX_SIZE);
        }

        String direction = normalizeUpper(firstNonBlank(safeRequest.getDirection(), safeRequest.getSortDirection()));
        direction = direction == null ? "DESC" : direction;
        if (!SORT_DIRECTIONS.contains(direction)) throw invalidInput("direction must be ASC or DESC");

        String requestedSort = trimToNull(safeRequest.getSort());
        requestedSort = requestedSort == null ? "timestamp" : requestedSort;
        String sortField = SORT_FIELDS.get(requestedSort);
        if (sortField == null) throw invalidInput("sort is invalid");

        String from = normalizeInstant(safeRequest.getFrom(), "from");
        String to = normalizeInstant(safeRequest.getTo(), "to");
        if (from != null && to != null && Instant.parse(from).isAfter(Instant.parse(to))) {
            throw invalidInput("from must not be after to");
        }

        Integer minRiskScore = validateRiskScore(safeRequest.getMinRiskScore(), "minRiskScore");
        Integer maxRiskScore = validateRiskScore(safeRequest.getMaxRiskScore(), "maxRiskScore");
        if (minRiskScore != null && maxRiskScore != null && minRiskScore > maxRiskScore) {
            throw invalidInput("minRiskScore must not be greater than maxRiskScore");
        }

        return new NormalizedRequest(
                page,
                size,
                sortField,
                direction,
                from,
                to,
                trimToNull(safeRequest.getServiceId()),
                trimToNull(safeRequest.getEndpointId()),
                normalizeEnum("anomalyType", safeRequest.getAnomalyType(), enumNames(AnomalyType.class)),
                normalizeEnum("anomalyLevel", safeRequest.getAnomalyLevel(), LEVELS),
                normalizeEnum("decision", safeRequest.getDecision(), enumNames(AnomalyDecision.class)),
                normalizeEnum("sourceType", safeRequest.getSourceType(), SOURCE_TYPES),
                normalizeEnum("flowType", safeRequest.getFlowType(), FLOW_TYPES),
                normalizeEnum("eventDirection", safeRequest.getEventDirection(), DIRECTIONS),
                trimToNull(safeRequest.getIncidentId()),
                trimToNull(safeRequest.getTraceId()),
                minRiskScore,
                maxRiskScore
        );
    }

    private Set<String> enumNames(Class<? extends Enum<?>> enumClass) {
        Set<String> values = new HashSet<>();
        for (Enum<?> value : enumClass.getEnumConstants()) values.add(value.name());
        return values;
    }

    private Integer validateRiskScore(Integer value, String fieldName) {
        if (value == null) return null;
        if (value < 0 || value > 100) throw invalidInput(fieldName + " must be between 0 and 100");
        return value;
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

    private String normalizeEnum(String fieldName, String value, Set<String> allowedValues) {
        String normalized = normalizeUpper(value);
        if (normalized == null) return null;
        if (!allowedValues.contains(normalized)) throw invalidInput(fieldName + " is invalid");
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

    private AnomalyListItemResponse toListItem(AnomalyDocument document) {
        return AnomalyListItemResponse.builder()
                .anomalyId(firstNonBlank(document.getAnomalyId(), document.getId()))
                .incidentId(document.getIncidentId())
                .timestamp(document.getTimestamp())
                .sourceType(document.getSourceType())
                .anomalyType(document.getAnomalyType())
                .anomalyLevel(document.getAnomalyLevel())
                .serviceId(document.getServiceId())
                .serviceName(document.getServiceName())
                .endpointId(document.getEndpointId())
                .endpointName(document.getEndpointName())
                .flowType(document.getFlowType())
                .direction(document.getDirection())
                .decision(document.getDecision())
                .riskScore(effectiveRiskScore(document))
                .maxRiskScore(document.getMaxRiskScore())
                .status(document.getStatus())
                .matchedCount(effectiveMatchedCount(document))
                .traceId(document.getTraceId())
                .correlationId(document.getCorrelationId())
                .lastSeenAt(document.getLastSeenAt())
                .build();
    }

    private AnomalyDetailResponse toDetail(AnomalyDocument document) {
        Map<String, Object> featureSnapshot = document.getFeatureSnapshot() == null ? Map.of() : document.getFeatureSnapshot();
        Map<String, Object> latestFeatureSnapshot = document.getLatestFeatureSnapshot() == null ? Map.of() : document.getLatestFeatureSnapshot();
        Map<String, Object> effectiveSnapshot = !latestFeatureSnapshot.isEmpty() ? latestFeatureSnapshot : featureSnapshot;
        return AnomalyDetailResponse.builder()
                .anomalyId(firstNonBlank(document.getAnomalyId(), document.getId()))
                .incidentId(document.getIncidentId())
                .timestamp(document.getTimestamp())
                .sourceType(document.getSourceType())
                .anomalyType(document.getAnomalyType())
                .anomalyLevel(document.getAnomalyLevel())
                .status(document.getStatus())
                .traceId(document.getTraceId())
                .correlationId(document.getCorrelationId())
                .serviceId(document.getServiceId())
                .serviceName(document.getServiceName())
                .endpointId(document.getEndpointId())
                .endpointName(document.getEndpointName())
                .flowType(document.getFlowType())
                .direction(document.getDirection())
                .decision(document.getDecision())
                .riskScore(effectiveRiskScore(document))
                .maxRiskScore(document.getMaxRiskScore())
                .maxSeverity(document.getMaxSeverity())
                .confidence(document.getConfidence())
                .matchedRules(nullSafeList(document.getMatchedRules()))
                .detectedFeatures(nullSafeList(document.getDetectedFeatures()))
                .featureSnapshot(featureSnapshot)
                .latestFeatureSnapshot(latestFeatureSnapshot)
                .effectiveFeatureSnapshot(effectiveSnapshot)
                .ruleSetVersion(document.getRuleSetVersion())
                .logBaselineVersion(document.getLogBaselineVersion())
                .behaviorBaselineVersion(document.getBehaviorBaselineVersion())
                .windowStart(document.getWindowStart())
                .windowEnd(document.getWindowEnd())
                .windowSampleCount(document.getWindowSampleCount())
                .firstSeenAt(document.getFirstSeenAt())
                .lastSeenAt(document.getLastSeenAt())
                .matchedCount(effectiveMatchedCount(document))
                .createdAt(document.getCreatedAt())
                .build();
    }

    private List<String> nullSafeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private Integer effectiveRiskScore(AnomalyDocument document) {
        return document.getRiskScore() != null ? document.getRiskScore() : document.getMaxRiskScore();
    }

    private Integer effectiveMatchedCount(AnomalyDocument document) {
        return document.getMatchedCount() == null ? 1 : document.getMatchedCount();
    }

    private void addNonBlank(Set<String> values, String value) {
        String normalized = trimToNull(value);
        if (normalized != null) values.add(normalized);
    }

    private List<AnomalyStatisticsResponse.Bucket> topBuckets(List<AnomalyDocument> documents,
                                                              java.util.function.Function<AnomalyDocument, String> extractor,
                                                              int limit) {
        Map<String, Long> counts = new HashMap<>();
        for (AnomalyDocument document : documents) {
            String key = trimToNull(extractor.apply(document));
            if (key != null) counts.merge(key, 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(entry -> AnomalyStatisticsResponse.Bucket.builder().key(entry.getKey()).count(entry.getValue()).build())
                .toList();
    }

    private List<AnomalyStatisticsResponse.Bucket> topMatchedRules(List<AnomalyDocument> documents) {
        Map<String, Long> counts = new HashMap<>();
        for (AnomalyDocument document : documents) {
            if (document.getMatchedRules() == null) continue;
            document.getMatchedRules().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .forEach(value -> counts.merge(value, 1L, Long::sum));
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()))
                .limit(10)
                .map(entry -> AnomalyStatisticsResponse.Bucket.builder().key(entry.getKey()).count(entry.getValue()).build())
                .toList();
    }

    private List<AnomalyStatisticsResponse.TimelineBucket> timelineBuckets(List<AnomalyDocument> documents, String from, String to) {
        ChronoUnit unit = chooseTimelineUnit(from, to);
        Map<String, TimelineAccumulator> buckets = new LinkedHashMap<>();
        documents.stream()
                .sorted(Comparator.comparing(document -> parseInstant(document.getTimestamp()), Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(document -> {
                    Instant instant = parseInstant(document.getTimestamp());
                    if (instant == null) return;
                    String key = truncate(instant, unit).toString();
                    TimelineAccumulator accumulator = buckets.computeIfAbsent(key, ignored -> new TimelineAccumulator());
                    accumulator.total++;
                    if ("CRITICAL".equals(document.getAnomalyLevel())) accumulator.critical++;
                    if ("HIGH".equals(document.getAnomalyLevel())) accumulator.high++;
                });
        return buckets.entrySet().stream()
                .map(entry -> AnomalyStatisticsResponse.TimelineBucket.builder()
                        .bucket(entry.getKey())
                        .critical(entry.getValue().critical)
                        .high(entry.getValue().high)
                        .total(entry.getValue().total)
                        .build())
                .toList();
    }

    private ChronoUnit chooseTimelineUnit(String from, String to) {
        Instant fromInstant = parseInstant(from);
        Instant toInstant = parseInstant(to);
        if (fromInstant == null || toInstant == null) return ChronoUnit.HOURS;
        long hours = ChronoUnit.HOURS.between(fromInstant, toInstant);
        if (hours <= 24) return ChronoUnit.HOURS;
        if (hours <= 24 * 7) return ChronoUnit.DAYS;
        return ChronoUnit.WEEKS;
    }

    private Instant parseInstant(String value) {
        if (value == null) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private Instant truncate(Instant instant, ChronoUnit unit) {
        if (unit == ChronoUnit.WEEKS) {
            return instant.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).minusDays(instant.atZone(ZoneOffset.UTC).getDayOfWeek().getValue() - 1L).toInstant();
        }
        return instant.truncatedTo(unit);
    }

    private String firstNonBlank(String first, String second) {
        String normalizedFirst = trimToNull(first);
        return normalizedFirst == null ? trimToNull(second) : normalizedFirst;
    }

    private record NormalizedRequest(int page,
                                     int size,
                                     String sortField,
                                     String direction,
                                     String from,
                                     String to,
                                     String serviceId,
                                     String endpointId,
                                     String anomalyType,
                                     String anomalyLevel,
                                     String decision,
                                     String sourceType,
                                     String flowType,
                                     String eventDirection,
                                     String incidentId,
                                     String traceId,
                                     Integer minRiskScore,
                                     Integer maxRiskScore) {
    }

    private static class TimelineAccumulator {
        private long critical;
        private long high;
        private long total;
    }
}
