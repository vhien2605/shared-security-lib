package vdt.mini.management_service.service.anomaly.alert;

import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import vdt.mini.management_service.util.enums.AlertChannel;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class EmailAlertChannelStrategy implements AlertChannelStrategy {
    private static final Logger log = LoggerFactory.getLogger(EmailAlertChannelStrategy.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private final JavaMailSender mailSender;
    private final String senderUsername;
    private final String mailTo;
    private final EmailAlertThrottleService throttleService;

    public EmailAlertChannelStrategy(JavaMailSender mailSender, @Value("${mail.sender.username}") String senderUsername,
                                      @Value("${mail.to:}") String mailTo,
                                      EmailAlertThrottleService throttleService) {
        this.mailSender = mailSender;
        this.senderUsername = senderUsername;
        this.mailTo = mailTo;
        this.throttleService = throttleService;
    }

    @Override
    public AlertChannel channel() {
        return AlertChannel.EMAIL;
    }

    @Override
    public void send(AnomalyAlertContext context) {
        if (context.alertConfig() == null || !context.emailEnabled()) return;
        List<String> recipients = recipients();
        if (recipients.isEmpty()) {
            log.warn("Skip anomaly email alert because mail.to is empty or invalid anomalyId={}", context.event().anomalyId());
            return;
        }
        if (!throttleService.acquire(context.event().incidentId(), context.alertConfig().getThrottleMinutes())) return;
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(senderUsername);
            message.setTo(recipients.toArray(String[]::new));
            message.setSubject(context.title());
            message.setText(context.content());
            mailSender.send(message);
        } catch (RuntimeException exception) {
            log.warn("Failed to send anomaly email alert anomalyId={}", context.event().anomalyId(), exception);
        }
    }

    private List<String> recipients() {
        if (mailTo == null || mailTo.isBlank()) return List.of();
        return Arrays.stream(mailTo.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .filter(value -> EMAIL_PATTERN.matcher(value).matches())
                .distinct()
                .toList();
    }
}
