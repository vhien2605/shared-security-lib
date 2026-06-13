package vdt.mini.shared_lib.web;

import feign.RequestInterceptor;
import feign.RequestTemplate;

public class OutboundFeignMetadataInterceptor implements RequestInterceptor {
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private final OutboundContextHolder contextHolder;

    public OutboundFeignMetadataInterceptor(OutboundContextHolder contextHolder) {
        this.contextHolder = contextHolder;
    }

    @Override
    public void apply(RequestTemplate template) {
        if (template == null) {
            return;
        }
        OutboundContext context = contextHolder.get();
        if (context == null) {
            return;
        }
        if (hasText(context.traceId())) {
            template.header(TRACE_ID_HEADER, context.traceId());
        }
        if (hasText(context.correlationId())) {
            template.header(CORRELATION_ID_HEADER, context.correlationId());
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
