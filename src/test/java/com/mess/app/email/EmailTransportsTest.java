package com.mess.app.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mess.app.exception.EmailDeliveryException;
import com.mess.app.testsupport.StubHttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EmailTransportsTest {

    private static final String API_KEY = "re_test_key_123456";
    private static final ObjectMapper JSON = new ObjectMapper();

    // ------------------------------------------------------------------ Resend

    @Test
    void resendPostsJsonWithBearerKeyToTheEmailsEndpoint() throws Exception {
        try (StubHttpServer stub = new StubHttpServer()) {
            stub.reply("POST /emails", 200, "{\"id\":\"49a3999c\"}");
            ResendEmailTransport transport = new ResendEmailTransport(API_KEY, "noreply@example.com", "Mess App", stub.baseUrl());

            transport.send("student@example.com", "🔐 Verify Your Email", "Your OTP is 123456");

            StubHttpServer.Received request = stub.lastRequest();
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/emails");
            assertThat(request.header("authorization")).isEqualTo("Bearer " + API_KEY);
            assertThat(request.header("content-type")).contains("application/json");
            JsonNode body = JSON.readTree(request.body());
            assertThat(body.get("from").asText()).isEqualTo("Mess App <noreply@example.com>");
            assertThat(body.get("to").get(0).asText()).isEqualTo("student@example.com");
            assertThat(body.get("subject").asText()).isEqualTo("🔐 Verify Your Email");
            assertThat(body.get("text").asText()).isEqualTo("Your OTP is 123456");
        }
    }

    @Test
    void resendUsesThePlainAddressWhenNoDisplayNameIsConfigured() throws Exception {
        try (StubHttpServer stub = new StubHttpServer()) {
            stub.reply("POST /emails", 200, "{\"id\":\"1\"}");

            new ResendEmailTransport(API_KEY, "noreply@example.com", "", stub.baseUrl()).send("a@b.com", "s", "t");

            assertThat(JSON.readTree(stub.lastRequest().body()).get("from").asText()).isEqualTo("noreply@example.com");
        }
    }

    @Test
    void resendRejectionBecomesAnEmailDeliveryExceptionWithTheProviderReasonButNoSecrets() throws Exception {
        try (StubHttpServer stub = new StubHttpServer()) {
            stub.reply("POST /emails", 403, "{\"message\":\"You can only send testing emails to your own email address\"}");
            ResendEmailTransport transport = new ResendEmailTransport(API_KEY, "onboarding@resend.dev", "", stub.baseUrl());

            assertThatThrownBy(() -> transport.send("student@example.com", "s", "Your OTP is 654321"))
                    .isInstanceOf(EmailDeliveryException.class)
                    .hasMessageContaining("HTTP 403")
                    .hasMessageContaining("only send testing emails")
                    .satisfies(e -> {
                        assertThat(e.getMessage()).doesNotContain(API_KEY);
                        assertThat(e.getMessage()).doesNotContain("654321");
                        assertThat(e.getMessage()).doesNotContain("student@example.com");
                    });
        }
    }

    @Test
    void anUnreachableProviderIsReportedAsADeliveryFailure() {
        ResendEmailTransport transport = new ResendEmailTransport(API_KEY, "noreply@example.com", "", "http://127.0.0.1:1");

        assertThatThrownBy(() -> transport.send("a@b.com", "s", "t"))
                .isInstanceOf(EmailDeliveryException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(API_KEY));
    }

    @Test
    void resendNeedsAnApiKeyAndASender() {
        assertThatThrownBy(() -> new ResendEmailTransport("", "noreply@example.com", "", "https://api.resend.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EMAIL_API_KEY").hasMessageContaining("EMAIL_FROM");
        assertThatThrownBy(() -> new ResendEmailTransport(API_KEY, " ", "", "https://api.resend.com"))
                .isInstanceOf(IllegalStateException.class);
    }

    // ------------------------------------------------------------------ Brevo

    @Test
    void brevoPostsToTheTransactionalEndpointWithAnApiKeyHeader() throws Exception {
        try (StubHttpServer stub = new StubHttpServer()) {
            stub.reply("POST /v3/smtp/email", 201, "{\"messageId\":\"<1@smtp-relay>\"}");
            BrevoEmailTransport transport = new BrevoEmailTransport("xkeysib-test", "noreply@example.com", "Mess App", stub.baseUrl());

            transport.send("student@example.com", "Subject", "Body text");

            StubHttpServer.Received request = stub.lastRequest();
            assertThat(request.path()).isEqualTo("/v3/smtp/email");
            assertThat(request.header("api-key")).isEqualTo("xkeysib-test");
            JsonNode body = JSON.readTree(request.body());
            assertThat(body.get("sender").get("email").asText()).isEqualTo("noreply@example.com");
            assertThat(body.get("sender").get("name").asText()).isEqualTo("Mess App");
            assertThat(body.get("to").get(0).get("email").asText()).isEqualTo("student@example.com");
            assertThat(body.get("textContent").asText()).isEqualTo("Body text");
        }
    }

    @Test
    void brevoRejectionIsReported() throws Exception {
        try (StubHttpServer stub = new StubHttpServer()) {
            stub.reply("POST /v3/smtp/email", 401, "{\"code\":\"unauthorized\",\"message\":\"Key not found\"}");
            BrevoEmailTransport transport = new BrevoEmailTransport("xkeysib-test", "noreply@example.com", "", stub.baseUrl());

            assertThatThrownBy(() -> transport.send("a@b.com", "s", "t"))
                    .isInstanceOf(EmailDeliveryException.class)
                    .hasMessageContaining("HTTP 401")
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain("xkeysib-test"));
        }
    }

    // ------------------------------------------------------------------ SMTP (local development)

    @Test
    void smtpTransportStillSendsThroughJavaMail() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpEmailTransport transport = new SmtpEmailTransport(mailSender, "me@gmail.com", "");

        transport.send("student@example.com", "Hello", "Body");

        org.mockito.ArgumentCaptor<SimpleMailMessage> captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("student@example.com");
        assertThat(captor.getValue().getSubject()).isEqualTo("Hello");
        assertThat(captor.getValue().getText()).isEqualTo("Body");
    }

    @Test
    void smtpFailuresBecomeEmailDeliveryExceptions() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new org.springframework.mail.MailSendException("Couldn't connect to host, port: smtp.gmail.com, 587"))
                .when(mailSender).send(any(SimpleMailMessage.class));
        SmtpEmailTransport transport = new SmtpEmailTransport(mailSender, "me@gmail.com", "");

        assertThatThrownBy(() -> transport.send("a@b.com", "s", "t")).isInstanceOf(EmailDeliveryException.class);
    }

    // ------------------------------------------------------------------ helpers

    @Test
    void emailAddressesAreMaskedForLogs() {
        assertThat(EmailAddresses.mask("john.doe@gmail.com")).isEqualTo("j***@gmail.com");
        assertThat(EmailAddresses.mask("nonsense")).isEqualTo("***");
        assertThat(EmailAddresses.mask(null)).isEqualTo("(empty)");
    }
}
