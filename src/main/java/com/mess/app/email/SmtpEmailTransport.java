package com.mess.app.email;

import com.mess.app.exception.EmailDeliveryException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Classic SMTP through JavaMail (for example Gmail with an app password). Good for local development.
 * Render Free blocks outbound SMTP ports (25, 465, 587), so use {@code resend} or {@code brevo} there.
 */
@Component
@ConditionalOnProperty(name = "email.provider", havingValue = "smtp", matchIfMissing = true)
@Slf4j
class SmtpEmailTransport implements EmailTransport {

    private final JavaMailSender mailSender;
    private final String from;

    SmtpEmailTransport(JavaMailSender mailSender,
                       @Value("${spring.mail.username:}") String smtpUsername,
                       @Value("${email.from:}") String from) {
        this.mailSender = mailSender;
        this.from = from == null ? "" : from.trim();
        if (smtpUsername == null || smtpUsername.isBlank()) {
            log.warn("EMAIL_PROVIDER=smtp but MAIL_USERNAME is empty: sending email will fail. "
                    + "Set MAIL_USERNAME/MAIL_PASSWORD, or use EMAIL_PROVIDER=console for local development.");
        }
    }

    @Override
    public void send(String to, String subject, String textBody) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (!from.isEmpty()) {
                message.setFrom(from);
            }
            message.setTo(to);
            message.setSubject(subject);
            message.setText(textBody);
            mailSender.send(message);
            log.info("Email sent via SMTP to {}", EmailAddresses.mask(to));
        } catch (Exception e) {
            throw new EmailDeliveryException("SMTP send failed: " + e.getClass().getSimpleName(), e);
        }
    }

    @Override
    public String name() {
        return "SMTP";
    }
}
