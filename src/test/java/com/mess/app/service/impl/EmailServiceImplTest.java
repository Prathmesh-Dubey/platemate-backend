package com.mess.app.service.impl;

import com.mess.app.email.EmailTransport;
import com.mess.app.exception.EmailDeliveryException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceImplTest {

    private final EmailTransport transport = mock(EmailTransport.class);
    private final EmailServiceImpl service = new EmailServiceImpl(transport);

    @Test
    void otpEmailsKeepTheExistingSubjectsAndContainTheCodeAndExpiry() {
        service.sendOtpEmail("a@b.com", "123456", "REGISTER");

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(transport).send(org.mockito.ArgumentMatchers.eq("a@b.com"), subject.capture(), body.capture());
        assertThat(subject.getValue()).isEqualTo("🔐 Verify Your Email - Mess Management System");
        assertThat(body.getValue()).contains("123456").contains("valid for the next 5 minutes")
                .contains("verify your email address and complete your registration");
    }

    @Test
    void forgotPasswordUsesItsOwnSubjectAndText() {
        service.sendOtpEmail("a@b.com", "999000", "FORGOT_PASSWORD");

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(transport).send(org.mockito.ArgumentMatchers.eq("a@b.com"), subject.capture(), body.capture());
        assertThat(subject.getValue()).isEqualTo("🔐 Reset Your Password - Mess Management System");
        assertThat(body.getValue()).contains("reset your password");
    }

    @Test
    void aFailedDeliveryIsNeverSwallowed() {
        doThrow(new EmailDeliveryException("Resend rejected the email (HTTP 403)"))
                .when(transport).send("a@b.com", "s", "b");

        assertThatThrownBy(() -> service.sendEmail("a@b.com", "s", "b"))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("HTTP 403");
    }

    @Test
    void unexpectedTransportErrorsAreWrappedWithoutLeakingTheirMessage() {
        when(transport.name()).thenReturn("test");
        doThrow(new IllegalStateException("secret-api-key-in-message")).when(transport).send("a@b.com", "s", "b");

        assertThatThrownBy(() -> service.sendEmail("a@b.com", "s", "b"))
                .isInstanceOf(EmailDeliveryException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("secret-api-key-in-message"));
    }
}
