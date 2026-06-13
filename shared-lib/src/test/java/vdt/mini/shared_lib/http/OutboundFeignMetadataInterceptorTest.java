package vdt.mini.shared_lib.http;

import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vdt.mini.shared_lib.web.OutboundContext;
import vdt.mini.shared_lib.web.OutboundContextHolder;
import vdt.mini.shared_lib.web.OutboundFeignMetadataInterceptor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OutboundFeignMetadataInterceptorTest {
    private OutboundContextHolder contextHolder;
    private OutboundFeignMetadataInterceptor interceptor;

    @BeforeEach
    void setUp() {
        contextHolder = new OutboundContextHolder();
        interceptor = new OutboundFeignMetadataInterceptor(contextHolder);
        contextHolder.clear();
    }

    @Test
    void apply_shouldNoOp_whenContextMissing() {
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKeys("X-Trace-Id", "X-Correlation-Id", "X-Client-Key", "X-Signature");
    }

    @Test
    void apply_shouldPropagateOnlyExistingTraceAndCorrelationIds() {
        contextHolder.set(new OutboundContext("service-1", "endpoint-1", "Profile API", "http://profile/users",
                "GET", "HTTP", "trace-1", "corr-1", Instant.now(), "nonce"));
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers()).containsKeys("X-Trace-Id", "X-Correlation-Id");
        assertThat(template.headers().get("X-Trace-Id")).containsExactly("trace-1");
        assertThat(template.headers().get("X-Correlation-Id")).containsExactly("corr-1");
        assertThat(template.headers()).doesNotContainKeys("X-Client-Key", "X-Signature", "Authorization");
    }

    @Test
    void apply_shouldNotGenerateMetadata_whenValuesBlank() {
        contextHolder.set(new OutboundContext("service-1", "endpoint-1", "Profile API", "http://profile/users",
                "GET", "HTTP", "", null, Instant.now(), "nonce"));
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKeys("X-Trace-Id", "X-Correlation-Id");
    }
}
