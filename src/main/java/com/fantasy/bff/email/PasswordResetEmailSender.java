package com.fantasy.bff.email;

import java.time.Instant;

public interface PasswordResetEmailSender {

    void send(String toEmail, String resetLink, Instant expiresAt);
}
