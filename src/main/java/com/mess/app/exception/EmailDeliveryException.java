package com.mess.app.exception;

/**
 * An email could not be handed to the email provider.
 * The message is written for logs; the API layer shows its own friendly text to users.
 */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message) {
        super(message);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
