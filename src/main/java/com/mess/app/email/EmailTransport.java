package com.mess.app.email;

import com.mess.app.exception.EmailDeliveryException;

/**
 * Sends one plain-text email. The active implementation is chosen with {@code EMAIL_PROVIDER}:
 * <ul>
 *   <li>{@code smtp}    - JavaMail/SMTP (local development; blocked on Render Free)</li>
 *   <li>{@code resend}  - Resend HTTPS API</li>
 *   <li>{@code brevo}   - Brevo HTTPS API</li>
 *   <li>{@code console} - prints the email to the log instead of sending it (development only)</li>
 * </ul>
 */
public interface EmailTransport {

    /** @throws EmailDeliveryException when the provider did not accept the message */
    void send(String to, String subject, String textBody);

    /** Short provider name used in logs. */
    String name();
}
