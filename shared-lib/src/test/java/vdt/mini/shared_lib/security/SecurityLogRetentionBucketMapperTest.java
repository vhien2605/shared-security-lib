package vdt.mini.shared_lib.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityLogRetentionBucketMapperTest {
    @ParameterizedTest
    @CsvSource(nullValues = "NULL", textBlock = """
            NULL,30,r30
            -1,-1,r14
            0,0,r14
            14,14,r14
            15,15,r30
            30,30,r30
            31,31,r90
            90,90,r90
            """)
    void bucket_shouldNormalizeRetentionDays(Integer input, int expectedDays, String expectedBucket) {
        assertThat(SecurityLogRetentionBucketMapper.normalizedDays(input)).isEqualTo(expectedDays);
        assertThat(SecurityLogRetentionBucketMapper.bucket(input)).isEqualTo(expectedBucket);
    }
}
