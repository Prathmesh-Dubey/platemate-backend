package com.mess.app.email;

/** Small helpers so that logs never contain a full email address. */
public final class EmailAddresses {

    private EmailAddresses() {
    }

    /** {@code john.doe@gmail.com} becomes {@code j***@gmail.com}. */
    public static String mask(String email) {
        if (email == null || email.isBlank()) {
            return "(empty)";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
