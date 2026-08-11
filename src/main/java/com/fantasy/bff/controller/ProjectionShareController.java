package com.fantasy.bff.controller;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.dto.request.ShareProjectionRequest;
import com.fantasy.bff.dto.response.ShareLinkResponse;
import com.fantasy.bff.generated.db.model.CreateShareRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Projection shares", description = "The current user's public links to their projections")
@RestController
@RequestMapping("/api/v1/projections/{id}/share")
public class ProjectionShareController {

    private final DatabaseServiceClient databaseServiceClient;
    private final String webBaseUrl;

    public ProjectionShareController(DatabaseServiceClient databaseServiceClient,
                                     @Value("${app.web-base-url}") String webBaseUrl) {
        this.databaseServiceClient = databaseServiceClient;
        this.webBaseUrl = webBaseUrl;
    }

    @Operation(operationId = "getProjectionShare",
            summary = "Fetch the share link for one of the current user's projections")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The projection is shared"),
        @ApiResponse(responseCode = "404", description = "No such projection for this user, or it is not shared")
    })
    @GetMapping
    public ShareLinkResponse get(@AuthenticationPrincipal String userId, @PathVariable UUID id) {
        return ShareLinkResponse.from(
                databaseServiceClient.getProjectionShare(UUID.fromString(userId), id), webBaseUrl);
    }

    @Operation(operationId = "shareProjection",
            summary = "Publish or refresh the projection's public snapshot",
            description = "Idempotent: re-sharing keeps the same link and updates what it shows.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Share created or refreshed"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "404", description = "No such projection for this user"),
        @ApiResponse(responseCode = "409", description = "The account has no username yet")
    })
    @PutMapping
    public ShareLinkResponse share(@AuthenticationPrincipal String userId, @PathVariable UUID id,
                                   @Valid @RequestBody ShareProjectionRequest request) {
        CreateShareRequest downstream = new CreateShareRequest().players(request.players());
        return ShareLinkResponse.from(
                databaseServiceClient.shareProjection(UUID.fromString(userId), id, downstream), webBaseUrl);
    }

    @Operation(operationId = "unshareProjection",
            summary = "Take the projection's public link down",
            description = "The link stops working immediately, and sharing again produces a new one.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Share deleted"),
        @ApiResponse(responseCode = "404", description = "No such projection for this user, or it is not shared")
    })
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unshare(@AuthenticationPrincipal String userId, @PathVariable UUID id) {
        databaseServiceClient.unshareProjection(UUID.fromString(userId), id);
    }
}
