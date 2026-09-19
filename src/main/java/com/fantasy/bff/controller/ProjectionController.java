package com.fantasy.bff.controller;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.dto.request.CopyProjectionRequest;
import com.fantasy.bff.dto.request.CreateProjectionRequest;
import com.fantasy.bff.dto.request.ImportProjectionRequest;
import com.fantasy.bff.dto.response.ProjectionResponse;
import com.fantasy.bff.dto.request.RenameProjectionRequest;
import com.fantasy.bff.dto.request.StartDraftRequest;
import com.fantasy.bff.dto.request.UpdateProjectionRequest;
import com.fantasy.bff.dto.response.ProjectionSummaryResponse;
import com.fantasy.bff.service.ProjectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
        return databaseServiceClient.listProjections(UUID.fromString(userId)).stream()
                .map(ProjectionSummaryResponse::from)
                .toList();
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
        @ApiResponse(responseCode = "403", description = "source=model needs premium and this account has none"),
        @ApiResponse(responseCode = "409",
                description = "The name is already taken by a projection or an imported board, "
                        + "or a draft against that preset already exists")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectionResponse create(@AuthenticationPrincipal String userId,
                                     @Valid @RequestBody CreateProjectionRequest request) {
        return projectionService.create(UUID.fromString(userId), request);
    }

    @Operation(summary = "Follow a shared board by its link",
            description = "Anyone holding a share link may follow the board behind it and draft "
                    + "against it. A follow is a live mirror: it is rewritten, name and all, "
                    + "every time the author publishes again, and the follower's own draft is "
                    + "the only thing on it they may change. Following a link twice does not "
                    + "make a second copy — the follow already held comes back, with 200 rather "
                    + "than 201. It disappears when the share does. To take a board of your own "
                    + "that the author's changes never reach, copy it instead.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Now following the board",
                content = @Content(schema = @Schema(implementation = ProjectionResponse.class))),
        @ApiResponse(responseCode = "200",
                description = "This link was already followed; that follow is returned untouched",
                content = @Content(schema = @Schema(implementation = ProjectionResponse.class))),
        @ApiResponse(responseCode = "400",
                description = "Validation failed, or the link is the caller's own board"),
        @ApiResponse(responseCode = "404", description = "No share with that token"),
        @ApiResponse(responseCode = "409",
                description = "A request racing this one created the follow; list the projections again"),
        @ApiResponse(responseCode = "412", description = "`seenUpdatedAt` was sent and the author "
                + "has changed the board since. Nothing was written.")
    })
    @PostMapping("/imports")
    public ResponseEntity<ProjectionResponse> importFromShare(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody ImportProjectionRequest request) {
        ProjectionService.Followed followed =
                projectionService.follow(UUID.fromString(userId), request);
        return ResponseEntity.status(followed.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(followed.projection());
    }

    @Operation(summary = "Copy a shared board into the current user's own projections",
            description = "The copy is the board as it is published now, named after the share "
                    + "(\"Copy of <name>\", numbered if that one is taken), with no draft and no "
                    + "link back: it is the user's to edit, and nothing the author publishes "
                    + "afterwards reaches it. Taking a copy also leaves the user following the "
                    + "link, unless the board is their own.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Copy created"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "404", description = "No share with that token"),
        @ApiResponse(responseCode = "409",
                description = "A request racing this one took the name or created the follow"),
        @ApiResponse(responseCode = "412", description = "`seenUpdatedAt` was sent and the author "
                + "has changed the board since. Nothing was written.")
    })
    @PostMapping("/copies")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectionResponse copyFromShare(@AuthenticationPrincipal String userId,
                                            @Valid @RequestBody CopyProjectionRequest request) {
        return projectionService.copyFromShare(UUID.fromString(userId), request);
    }

    @Operation(summary = "Start a draft against one of the current user's boards",
            description = "The draft is a row of its own, holding a copy of the board's numbers "
                    + "and its own picks — so the board can be edited or deleted while the draft "
                    + "is under way, and a board can be drafted against as many times as its "
                    + "owner likes. The rows are copied server-side and are not sent. Without a "
                    + "`name` the draft takes the board's, numbered (\"Board (2)\") where "
                    + "another draft already holds it.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Draft created"),
        @ApiResponse(responseCode = "400", description = "Validation failed, or the source is itself a draft"),
        @ApiResponse(responseCode = "404", description = "No such projection for this user")
    })
    @PostMapping("/{id}/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectionResponse startDraft(@AuthenticationPrincipal String userId,
                                         @PathVariable UUID id,
                                         @Valid @RequestBody StartDraftRequest request) {
        return projectionService.startDraft(UUID.fromString(userId), id, request);
    }

    @Operation(summary = "Rename one of the current user's boards or drafts",
            description = "Without sending the board with it. A name the user typed is refused "
                    + "where it is taken; one the app derived (`derived: true`, a draft taking "
                    + "the name of the league it was just synced with) is numbered instead, and "
                    + "is skipped altogether where the user has named the row themselves. The "
                    + "saved name is in the response either way.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Renamed, or left as it was"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "404", description = "No such projection for this user"),
        @ApiResponse(responseCode = "409", description = "Another row of the user's holds that name")
    })
    @PutMapping("/{id}/name")
    public ProjectionSummaryResponse rename(@AuthenticationPrincipal String userId,
                                            @PathVariable UUID id,
                                            @Valid @RequestBody RenameProjectionRequest request) {
        return projectionService.rename(UUID.fromString(userId), id, request);
    }

    @Operation(summary = "Update one of the current user's saved projections",
            description = "On a followed board only `data.draft` is kept: the rest is the "
                    + "author's and is rewritten whenever they publish. The request is not "
                    + "refused, it is simply taken in part, and the board as stored comes back.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Projection updated"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "404", description = "No such projection for this user"),
        @ApiResponse(responseCode = "409",
                description = "The name is already taken by another projection or imported board")
    })
    @PutMapping("/{id}")
    public ProjectionResponse update(@AuthenticationPrincipal String userId, @PathVariable UUID id,
                                     @Valid @RequestBody UpdateProjectionRequest request) {
        return projectionService.update(UUID.fromString(userId), id, request.toDownstream());
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
