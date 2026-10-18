package com.mess.app.service.impl;

import com.mess.app.email.EmailAddresses;
import com.mess.app.email.EmailTransport;
import com.mess.app.exception.EmailDeliveryException;
import com.mess.app.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Builds the emails (OTP templates) and hands them to the configured {@link EmailTransport}
 * (SMTP, Resend, Brevo or console - see EMAIL_PROVIDER). A failed delivery is always reported to the
 * caller as an {@link EmailDeliveryException}; it is never silently ignored.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final EmailTransport transport;

    @Override
    public void sendEmail(String to, String subject, String body) {
        try {
            transport.send(to, subject, body);
        } catch (EmailDeliveryException e) {
            log.error("Failed to send email to {} via {}: {}", EmailAddresses.mask(to), transport.name(), e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            log.error("Failed to send email to {} via {}: {}", EmailAddresses.mask(to), transport.name(),
                    e.getClass().getSimpleName());
            throw new EmailDeliveryException("Failed to send email: " + e.getClass().getSimpleName(), e);
        }
    }

    @Override
    public void sendOtpEmail(String to, String otp, String purpose) {
        String subject = getOtpSubject(purpose);
        String body = getOtpBody(purpose, otp);
        sendEmail(to, subject, body);
    }

    private String getOtpSubject(String purpose) {
        switch (purpose.toLowerCase()) {
            case "register":
                return "🔐 Verify Your Email - Mess Management System";
            case "login":
                return "🔐 Login OTP - Mess Management System";
            case "forgot_password":
                return "🔐 Reset Your Password - Mess Management System";
            default:
                return "🔐 OTP Verification - Mess Management System";
        }
    }

    private String getOtpBody(String purpose, String otp) {
        String purposeText;
        String additionalInfo = "";

        switch (purpose.toLowerCase()) {
            case "register":
                purposeText = "verify your email address and complete your registration";
                additionalInfo = "\n\nOnce verified, you'll be able to access your account and start using the Mess Management System.";
                break;
            case "login":
                purposeText = "log in to your account securely";
                additionalInfo = "\n\nThis OTP is required to complete your login process. Do not share this OTP with anyone.";
                break;
            case "forgot_password":
                purposeText = "reset your password";
                additionalInfo = "\n\nIf you did not request a password reset, please ignore this email or contact support immediately.";
                break;
            default:
                purposeText = "complete your verification";
        }

        return String.format(
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "        MESS MANAGEMENT SYSTEM\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
                "Dear User,\n\n" +
                "We received a request to %s.\n\n" +
                "Your One-Time Password (OTP) is:\n\n" +
                "   🔑  %s  🔑\n\n" +
                "This OTP is valid for the next 5 minutes.\n" +
                "Please do not share this OTP with anyone.%s\n\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "If you didn't request this, please ignore this email.\n" +
                "For security, never share your OTP with anyone.\n\n" +
                "Regards,\n" +
                "Mess Management System Team\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
                purposeText, otp, additionalInfo
        );
    }
}