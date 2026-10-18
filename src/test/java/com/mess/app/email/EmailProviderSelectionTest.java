package com.mess.app.email;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Checks which EmailTransport the EMAIL_PROVIDER setting selects, without starting the whole application. */
class EmailProviderSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
            .withUserConfiguration(SmtpEmailTransport.class, ResendEmailTransport.class,
                    BrevoEmailTransport.class, ConsoleEmailTransport.class);

    @Test
    void smtpIsTheDefaultSoLocalDevelopmentKeepsWorking() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(EmailTransport.class);
            assertThat(context).hasSingleBean(SmtpEmailTransport.class);
        });
    }

    @Test
    void resendIsSelectedWhenConfigured() {
        runner.withPropertyValues("email.provider=resend", "email.api-key=re_x", "email.from=noreply@example.com")
                .run(context -> {
                    assertThat(context).hasSingleBean(EmailTransport.class);
                    assertThat(context).hasSingleBean(ResendEmailTransport.class);
                });
    }

    @Test
    void brevoIsSelectedWhenConfigured() {
        runner.withPropertyValues("email.provider=brevo", "email.api-key=k", "email.from=noreply@example.com")
                .run(context -> assertThat(context).hasSingleBean(BrevoEmailTransport.class));
    }

    @Test
    void consoleProviderIsAvailableForDevelopment() {
        runner.withPropertyValues("email.provider=console")
                .run(context -> assertThat(context).hasSingleBean(ConsoleEmailTransport.class));
    }

    @Test
    void anApiProviderWithoutItsKeyFailsStartupWithAClearMessage() {
        runner.withPropertyValues("email.provider=resend", "email.from=noreply@example.com")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("EMAIL_API_KEY");
                });
    }

    @Test
    void anUnknownProviderDoesNotSilentlyFallBackToAnotherOne() {
        runner.withPropertyValues("email.provider=carrier-pigeon")
                .run(context -> assertThat(context).doesNotHaveBean(EmailTransport.class));
    }
}
