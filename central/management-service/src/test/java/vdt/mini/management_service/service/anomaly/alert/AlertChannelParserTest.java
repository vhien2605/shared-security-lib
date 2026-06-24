package vdt.mini.management_service.service.anomaly.alert;

import org.junit.jupiter.api.Test;
import vdt.mini.management_service.entity.AlertConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlertChannelParserTest {
    private final AlertChannelParser parser = new AlertChannelParser();

    @Test
    void emailEnabled_shouldDetectEmailChannelName() {
        AlertConfig config = new AlertConfig();
        config.setChannels(List.of("LOG", "EMAIL"));

        assertThat(parser.emailEnabled(config)).isTrue();
    }

    @Test
    void emailEnabled_shouldIgnoreNonEmailChannels() {
        AlertConfig config = new AlertConfig();
        config.setChannels(List.of("LOG", "SMS"));

        assertThat(parser.emailEnabled(config)).isFalse();
    }
}
