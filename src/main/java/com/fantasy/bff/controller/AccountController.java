package com.fantasy.bff.controller;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.dto.request.SetUsernameRequest;
import com.fantasy.bff.dto.response.AccountResponse;
import com.fantasy.bff.service.AvatarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * The signed-in account itself: its public name and its profile picture. The parts of an account
 * that are nobody else's business (email, password) are handled by the auth endpoints.
 */
@Tag(name = "Account", description = "The signed-in account's own profile")
@RestController
@RequestMapping("/api/v1/account")
public class AccountController {

    private final DatabaseServiceClient databaseServiceClient;
    private final AvatarService avatarService;

    public AccountController(DatabaseServiceClient databaseServiceClient, AvatarService avatarService) {
        this.databaseServiceClient = databaseServiceClient;
        this.avatarService = avatarService;
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

    @Operation(operationId = "getAvatar",
            summary = "Fetch the account's profile picture",
            description = "The picture as it was uploaded, served under the type it was checked to "
                    + "be. An account without one gets an empty 204 rather than an error: having no "
                    + "picture is the normal state of a new account, not a failure.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Picture returned"),
        @ApiResponse(responseCode = "204", description = "The account has no picture")
    })
    @GetMapping(value = "/avatar", produces = "image/*")
    public ResponseEntity<byte[]> getAvatar(@AuthenticationPrincipal String userId) {
        return avatarService.find(UUID.fromString(userId))
                .map(avatar -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(avatar.contentType()))
                        .cacheControl(CacheControl.noStore())
                        .body(avatar.data()))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(operationId = "setAvatar",
            summary = "Set the account's profile picture",
            description = "A PNG, JPEG or WebP of at most 512 KB, sent as the `file` part of a "
                    + "multipart form. The app scales the picture down before uploading; what "
                    + "arrives here is stored and served as it is. Replaces any picture the account had.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Picture set"),
        @ApiResponse(responseCode = "400", description = "Not a PNG, JPEG or WebP, empty, or larger than 512 KB"),
        @ApiResponse(responseCode = "413", description = "The upload exceeds the request size limit")
    })
    @PutMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setAvatar(@AuthenticationPrincipal String userId,
                          @RequestPart("file") MultipartFile file) throws IOException {
        avatarService.set(UUID.fromString(userId), file.getBytes());
    }

    @Operation(operationId = "removeAvatar", summary = "Remove the account's profile picture")
    @ApiResponse(responseCode = "204", description = "Picture removed, or there was none")
    @DeleteMapping("/avatar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAvatar(@AuthenticationPrincipal String userId) {
        avatarService.remove(UUID.fromString(userId));
    }
}
