package com.mess.app.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;

/**
 * Resend (https://resend.com): {@code POST /emails} with a Bearer API key.
 *
 * <p>Resend only delivers to people other than the account owner after you verify a sending domain,
 * and {@code EMAIL_FROM} must use that verified domain.</p>
 */
@Component
@ConditionalOnProperty(name = "email.provider", havingValue = "resend")
class ResendEmailTransport extends AbstractHttpEmailTransport {

    ResendEmailTransport(
            @Value("${email.api-key:}") String apiKey,
            @Value("${email.from:}") String from,
            @Value("${email.from-name:}") String fromName,
            @Value("${email.resend.base-url:https://api.resend.com}") String baseUrl) {
        super("resend", apiKey, from, fromName, baseUrl);
    }

    @Override
    protected HttpRequest.Builder buildRequest(String to, String subject, String textBody)
            throws JsonProcessingException {
        String sender = fromName.isEmpty() ? fromEmail : fromName + " <" + fromEmail + ">";
        String json = MAPPER.writeValueAsString(Map.of(
                "from", sender,
                "to", List.of(to),
                "subject", subject,
                "text", textBody));
        return HttpRequest.newBuilder(endpoint("/emails"))
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json));
    }

    @Override
    public String name() {
        return "Resend";
    }
}
