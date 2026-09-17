package com.fantasy.bff.controller;

import com.fantasy.bff.dto.request.SendFeedbackRequest;
import com.fantasy.bff.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Feedback", description = "Bug reports and feature requests from signed-in users")
@RestController
@RequestMapping("/api/v1/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @Operation(operationId = "sendFeedback",
            summary = "Send a bug report or a feature request",
            description = "Filed privately, together with the account's email address so the "
                    + "answer can go out by mail. Served only where GET /api/v1/features reports "
                    + "feedback.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Sent"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "404", description = "This environment does not take feedback"),
        @ApiResponse(responseCode = "502", description = "It could not be filed, and nothing was kept")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void send(@AuthenticationPrincipal String userId, @Valid @RequestBody SendFeedbackRequest request) {
        feedbackService.send(userId, request);
    }
}
