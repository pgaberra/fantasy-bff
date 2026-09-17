package com.fantasy.bff.email;

import com.fantasy.bff.dto.request.FeedbackType;

public interface FeedbackNotificationEmailSender {

    void send(FeedbackType type, String title, String issueUrl);
}
