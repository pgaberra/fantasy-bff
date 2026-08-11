package com.fantasy.bff.dto.response;

import com.fantasy.bff.model.downstream.User;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The signed-in account as the app needs it. Deliberately thin: the password hash and the social
 * subject ids that db-service returns to its trusted caller have no business reaching a browser.
 */
public record AccountResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
        @Schema(description = "The account's public name; absent until the user picks one, which "
                + "sharing a projection requires") String username
) {
    public static AccountResponse from(User user) {
        return new AccountResponse(user.email(), user.username());
    }
}
