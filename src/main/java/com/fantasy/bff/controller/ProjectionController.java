package com.fantasy.bff.controller;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.generated.db.model.CreateProjectionRequest;
import com.fantasy.bff.generated.db.model.ProjectionResponse;
import com.fantasy.bff.generated.db.model.ProjectionSummaryResponse;
import com.fantasy.bff.generated.db.model.UpdateProjectionRequest;
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

    public ProjectionController(DatabaseServiceClient databaseServiceClient) {
        this.databaseServiceClient = databaseServiceClient;
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
        return databaseServiceClient.getProjection(UUID.fromString(userId), id);
    }

    @Operation(summary = "Save a new projection for the current user")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Projection created"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "409", description = "A projection with that name already exists")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectionResponse create(@AuthenticationPrincipal String userId,
                                     @Valid @RequestBody CreateProjectionRequest request) {
        return databaseServiceClient.createProjection(UUID.fromString(userId), request);
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
        return databaseServiceClient.updateProjection(UUID.fromString(userId), id, request);
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
