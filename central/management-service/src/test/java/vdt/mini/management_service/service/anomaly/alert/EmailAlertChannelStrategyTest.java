package vdt.mini.management_service.service.anomaly.alert;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import vdt.mini.management_service.entity.AlertConfig;
import vdt.mini.management_service.service.anomaly.runtime.AnomalyTestFixtures;
import vdt.mini.management_service.util.enums.AlertSeverity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EmailAlertChannelStrategyTest {
    @Test
    void send_shouldDispatchEmailOnlyWhenConfiguredAndNotThrottled() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailAlertThrottleService throttle = mock(EmailAlertThrottleService.class);
        when(throttle.acquire("inc-1", 5)).thenReturn(true);
        AlertConfig config = new AlertConfig();
        config.setSeverity(AlertSeverity.WARNING);
        config.setThrottleMinutes(5);
        var event = AnomalyTestFixtures.anomalyEvent("inc-1", "HIGH");

        new EmailAlertChannelStrategy(mailSender, "sender@example.com", "ops@example.com,sec@example.com",
                throttle)
                .send(new AnomalyAlertContext(event, "title", "content", config, true));

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getFrom()).isEqualTo("sender@example.com");
        assertThat(messageCaptor.getValue().getTo()).containsExactly("ops@example.com", "sec@example.com");
    }

    @Test
    void send_shouldIgnoreAlertSeverityThresholdWhenEmailChannelIsEnabled() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailAlertThrottleService throttle = mock(EmailAlertThrottleService.class);
        when(throttle.acquire("inc-1", 5)).thenReturn(true);
        AlertConfig config = new AlertConfig();
        config.setSeverity(AlertSeverity.CRITICAL);
        config.setThrottleMinutes(5);

        new EmailAlertChannelStrategy(mailSender, "sender@example.com", "ops@example.com", throttle)
                .send(new AnomalyAlertContext(AnomalyTestFixtures.anomalyEvent("inc-1", "LOW"), "title", "content", config, true));

        verify(throttle).acquire("inc-1", 5);
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void send_shouldSkipWhenMailToIsInvalid() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailAlertThrottleService throttle = mock(EmailAlertThrottleService.class);
        AlertConfig config = new AlertConfig();
        config.setSeverity(AlertSeverity.WARNING);
        config.setThrottleMinutes(5);

        new EmailAlertChannelStrategy(mailSender, "sender@example.com", "bad-email", throttle)
                .send(new AnomalyAlertContext(AnomalyTestFixtures.anomalyEvent("inc-1", "HIGH"), "title", "content", config, true));

        verifyNoInteractions(mailSender, throttle);
    }

    @Test
    void placeholders_shouldNoopWithoutThrowing() {
        var context = new AnomalyAlertContext(AnomalyTestFixtures.anomalyEvent("inc-1", "HIGH"), "title", "content", null, false);
        new SlackAlertChannelStrategy().send(context);
        new SmsAlertChannelStrategy().send(context);
    }
}
