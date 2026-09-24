package com.fantasy.bff.email;

public interface SignupNotificationEmailSender {

    /** Tells the owner an account was created. Returns at once and never throws. */
    void send(String userEmail, SignupMethod method);
}
