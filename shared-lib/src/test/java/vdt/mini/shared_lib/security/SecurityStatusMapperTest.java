package vdt.mini.shared_lib.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import vdt.mini.shared_lib.enums.SecurityErrorCode;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityStatusMapperTest {
    private final SecurityStatusMapper mapper = new SecurityStatusMapper();

    @ParameterizedTest
    @CsvSource({
            "AUTH_MISSING,401",
            "API_KEY_INVALID,401",
            "HMAC_INVALID,401",
            "BLACKLISTED,403",
            "ENDPOINT_NOT_REGISTERED,404",
            "REQUEST_SIZE_EXCEEDED,413",
            "RESPONSE_SIZE_EXCEEDED,413",
            "RATE_LIMIT_EXCEEDED,429",
            "ENDPOINT_DISABLED,503",
            "ENDPOINT_INACTIVE,503",
            "TIMEOUT_EXCEEDED,504",
            "INTERNAL_ERROR,500"
    })
    void toHttpStatus_shouldMapInboundSpecCodes(SecurityErrorCode errorCode, int expectedStatus) {
        assertThat(mapper.toHttpStatus(errorCode).value()).isEqualTo(expectedStatus);
        assertThat(mapper.resultCode(errorCode)).isEqualTo(String.valueOf(expectedStatus));
    }
}
