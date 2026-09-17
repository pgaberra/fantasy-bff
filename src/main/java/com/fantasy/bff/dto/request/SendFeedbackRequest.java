package com.fantasy.bff.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SendFeedbackRequest(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "BUG for something on the site that is broken, FEATURE for something "
                        + "the user wants added.")
        @NotNull
        FeedbackType type,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "One line, at most 120 characters.")
        @NotBlank
        @Size(max = 120)
        String title,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "What happened, or what is wanted. At most 5000 characters.")
        @NotBlank
        @Size(max = 5000)
        String description,

        @Schema(description = "The path of the page the user was on, such as /draft. Only the path: a "
                + "query string or fragment can carry a single-use token, so either is refused.")
        @Size(max = 200)
        @Pattern(regexp = "^/[A-Za-z0-9/_.~%-]*$", message = "must be a path without a query string or fragment")
        String page
) {}
