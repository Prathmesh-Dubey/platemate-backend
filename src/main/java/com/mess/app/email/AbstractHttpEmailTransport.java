package com.mess.app.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mess.app.exception.EmailDeliveryException;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Shared plumbing for email providers that expose an HTTPS JSON API (works on hosts that block SMTP
 * ports, such as Render Free). Uses the JDK HTTP client, so no extra dependency is needed.
 */
@Slf4j
abstract class AbstractHttpEmailTransport implements EmailTransport {

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    protected final String apiKey;
    protected final String fromEmail;
    protected final String fromName;
    protected final String baseUrl;

    protected AbstractHttpEmailTransport(String providerLabel, String apiKey, String fromEmail, String fromName,
                                         String baseUrl) {
        if (apiKey == null || apiKey.isBlank() || fromEmail == null || fromEmail.isBlank()) {
            throw new IllegalStateException("EMAIL_PROVIDER=" + providerLabel
                    + " needs EMAIL_API_KEY and EMAIL_FROM to be set (see .env.example)");
        }
        this.apiKey = apiKey.trim();
        this.fromEmail = fromEmail.trim();
        this.fromName = fromName == null ? "" : fromName.trim();
        String base = baseUrl.trim();
        this.baseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /** Builds the provider specific request. */
    protected abstract HttpRequest.Builder buildRequest(String to, String subject, String textBody)
            throws JsonProcessingException;

    @Override
    public void send(String to, String subject, String textBody) {
        HttpResponse<String> response;
        try {
            HttpRequest request = buildRequest(to, subject, textBody)
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .build();
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmailDeliveryException(name() + " request was interrupted", e);
        } catch (IOException | RuntimeException e) {
            // the message of these exceptions never contains the API key
            throw new EmailDeliveryException(name() + " could not be reached: " + e.getClass().getSimpleName(), e);
        }

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new EmailDeliveryException(name() + " rejected the email (HTTP " + status + ")"
                    + providerMessage(response.body()));
        }
        log.info("Email accepted by {} for {}", name(), EmailAddresses.mask(to));
    }

    protected URI endpoint(String path) {
        return URI.create(baseUrl + path);
    }

    /** The provider's own error text, e.g. "domain not verified". Truncated, never includes our key. */
    private static String providerMessage(String body) {
        try {
            JsonNode json = MAPPER.readTree(body == null ? "" : body);
            String message = json.path("message").asText("");
            if (message.isEmpty()) {
                return "";
            }
            return ": " + (message.length() > 200 ? message.substring(0, 200) : message);
        } catch (Exception e) {
            return "";
        }
    }
}
