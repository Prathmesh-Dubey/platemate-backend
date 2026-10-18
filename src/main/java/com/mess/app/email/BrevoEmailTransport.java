package com.mess.app.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.http.HttpRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Brevo (https://www.brevo.com) transactional API: {@code POST /v3/smtp/email} with an {@code api-key}
 * header. {@code EMAIL_FROM} must be a sender (or domain) you validated in the Brevo dashboard.
 *
 * <p>The request format follows Brevo's public API documentation but has not been exercised against
 * the live service from this project; test it once with your own key.</p>
 */
@Component
@ConditionalOnProperty(name = "email.provider", havingValue = "brevo")
class BrevoEmailTransport extends AbstractHttpEmailTransport {

    BrevoEmailTransport(
            @Value("${email.api-key:}") String apiKey,
            @Value("${email.from:}") String from,
            @Value("${email.from-name:}") String fromName,
            @Value("${email.brevo.base-url:https://api.brevo.com}") String baseUrl) {
        super("brevo", apiKey, from, fromName, baseUrl);
    }

    @Override
    protected HttpRequest.Builder buildRequest(String to, String subject, String textBody)
            throws JsonProcessingException {
        Map<String, Object> sender = new LinkedHashMap<>();
        sender.put("email", fromEmail);
        if (!fromName.isEmpty()) {
            sender.put("name", fromName);
        }
        String json = MAPPER.writeValueAsString(Map.of(
                "sender", sender,
                "to", List.of(Map.of("email", to)),
                "subject", subject,
                "textContent", textBody));
        return HttpRequest.newBuilder(endpoint("/v3/smtp/email"))
                .header("api-key", apiKey)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
    }

    @Override
    public String name() {
        return "Brevo";
    }
}
