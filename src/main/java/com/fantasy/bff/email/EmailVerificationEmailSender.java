package com.fantasy.bff.email;

import java.time.Instant;

public interface EmailVerificationEmailSender {

    void send(String toEmail, String verifyLink, Instant expiresAt);
}
