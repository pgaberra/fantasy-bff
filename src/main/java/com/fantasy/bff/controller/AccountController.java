package com.fantasy.bff.controller;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.dto.request.SetUsernameRequest;
import com.fantasy.bff.dto.response.AccountResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The signed-in account itself. Only the public name lives here for now — the parts of an account
 * that are nobody else's business (email, password) are handled by the auth endpoints.
 */
@Tag(name = "Account", description = "The signed-in account's own profile")
@RestController
@RequestMapping("/api/v1/account")
public class AccountController {

    private final DatabaseServiceClient databaseServiceClient;

    public AccountController(DatabaseServiceClient databaseServiceClient) {
        this.databaseServiceClient = databaseServiceClient;
    }

    @Operation(operationId = "getAccount", summary = "Fetch the signed-in account's profile")
    @ApiResponse(responseCode = "200", description = "Profile returned")
    @GetMapping
    public AccountResponse get(@AuthenticationPrincipal String userId) {
        return AccountResponse.from(databaseServiceClient.findUserById(UUID.fromString(userId)));
    }

    @Operation(operationId = "setUsername",
            summary = "Set the account's public name",
            description = "The name shown wherever the account publishes something. Sharing a "
                    + "projection requires one, which is the only thing that forces the choice.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Username set"),
        @ApiResponse(responseCode = "400", description = "Validation failed (length or characters)"),
        @ApiResponse(responseCode = "409", description = "Another account already holds that name")
    })
    @PutMapping("/username")
    public AccountResponse setUsername(@AuthenticationPrincipal String userId,
                                       @Valid @RequestBody SetUsernameRequest request) {
        return AccountResponse.from(
                databaseServiceClient.setUsername(UUID.fromString(userId), request.username()));
    }
}
