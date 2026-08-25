package com.fantasy.bff.controller;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.dto.request.CreateProjectionRequest;
import com.fantasy.bff.dto.request.ImportProjectionRequest;
import com.fantasy.bff.dto.response.ProjectionResponse;
import com.fantasy.bff.generated.db.model.ProjectionSummaryResponse;
import com.fantasy.bff.generated.db.model.UpdateProjectionRequest;
import com.fantasy.bff.service.ProjectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Projections", description = "The authenticated user's saved player projections")
@RestController
@RequestMapping("/api/v1/projections")
public class ProjectionController {

    private final DatabaseServiceClient databaseServiceClient;
    private final ProjectionService projectionService;

    public ProjectionController(DatabaseServiceClient databaseServiceClient,
                                ProjectionService projectionService) {
        this.databaseServiceClient = databaseServiceClient;
        this.projectionService = projectionService;
    }

    @Operation(summary = "List the current user's saved projections (metadata only)")
    @ApiResponse(responseCode = "200", description = "The user's projections, newest first")
    @GetMapping
    public List<ProjectionSummaryResponse> list(@AuthenticationPrincipal String userId) {
        return databaseServiceClient.listProjections(UUID.fromString(userId));
    }

    @Operation(summary = "Fetch one of the current user's saved projections, including its data")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Projection found"),
        @ApiResponse(responseCode = "404", description = "No such projection for this user")
    })
    @GetMapping("/{id}")
    public ProjectionResponse get(@AuthenticationPrincipal String userId, @PathVariable UUID id) {
        return projectionService.get(UUID.fromString(userId), id);
    }

    @Operation(summary = "Save a new projection for the current user",
            description = "Send `source` with an empty `data.players` to have the server fill the "
                    + "player rows in from its own read model, instead of uploading ~1600 players "
                    + "the client just downloaded. Send `data.players` (and no `source`) when the "
                    + "rows are user-specific, e.g. copied or carried over from the demo.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Projection created"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "409", description = "A projection of that kind already exists")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectionResponse create(@AuthenticationPrincipal String userId,
                                     @Valid @RequestBody CreateProjectionRequest request) {
        return projectionService.create(UUID.fromString(userId), request);
    }

    @Operation(summary = "Copy a shared projection into the current user's own, by its share token",
            description = "Anyone holding a share link may copy the board behind it and draft "
                    + "against it. The copy is of the snapshot as it was published, carries none "
                    + "of the author's draft, and records who shared it.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Projection imported"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "404", description = "No share with that token"),
        @ApiResponse(responseCode = "409",
                description = "An imported projection with that name already exists")
    })
    @PostMapping("/imports")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectionResponse importFromShare(@AuthenticationPrincipal String userId,
                                              @Valid @RequestBody ImportProjectionRequest request) {
        return projectionService.importFromShare(UUID.fromString(userId), request);
    }

    @Operation(summary = "Update one of the current user's saved projections")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Projection updated"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "404", description = "No such projection for this user"),
        @ApiResponse(responseCode = "409", description = "Another projection with that name already exists")
    })
    @PutMapping("/{id}")
    public ProjectionResponse update(@AuthenticationPrincipal String userId, @PathVariable UUID id,
                                     @Valid @RequestBody UpdateProjectionRequest request) {
        return projectionService.update(UUID.fromString(userId), id, request);
    }

    @Operation(summary = "Delete one of the current user's saved projections")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Projection deleted"),
        @ApiResponse(responseCode = "404", description = "No such projection for this user")
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal String userId, @PathVariable UUID id) {
        databaseServiceClient.deleteProjection(UUID.fromString(userId), id);
    }
}
