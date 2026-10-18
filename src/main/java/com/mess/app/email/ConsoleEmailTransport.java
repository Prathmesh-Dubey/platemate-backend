package com.mess.app.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * DEVELOPMENT ONLY. Prints the email (including the OTP) to the log instead of sending it, so the
 * sign-up flow can be tested without any mail account. Never use this in production.
 */
@Component
@ConditionalOnProperty(name = "email.provider", havingValue = "console")
@Slf4j
class ConsoleEmailTransport implements EmailTransport {

    ConsoleEmailTransport() {
        log.warn("EMAIL_PROVIDER=console: emails are only printed to this log, nothing is sent. DEVELOPMENT ONLY.");
    }

    @Override
    public void send(String to, String subject, String textBody) {
        log.warn("[DEV EMAIL] to={} subject={}\n{}", to, subject, textBody);
    }

    @Override
    public String name() {
        return "console";
    }
}
