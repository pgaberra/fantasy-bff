package com.fantasy.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record FeaturesResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether this environment serves the AI projection: the model's lines "
                        + "and a projection or preset draft seeded from them. False means neither is "
                        + "served to anyone, so a client should not offer it. It says nothing about "
                        + "this account's plan: an AI projection that needs premium is still available.")
        boolean aiProjection,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether the draft room may follow a linked Yahoo league's live draft. False means "
                        + "the draft endpoint answers 404, so a client should not offer it.")
        boolean leagueDraftSync,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether this environment serves the streamer planner. False means its "
                        + "endpoints answer 404, so a client should not offer it.")
        boolean streamerPlanner,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether Who's hot may show only the players a linked league has "
                        + "available. False means the rostered-players endpoint answers 404, so a "
                        + "client should not offer it.")
        boolean whosHotAvailableFilter,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether a draft may be started from the rest of the season "
                        + "(source=rest_of_season). False means it is refused and its status "
                        + "endpoint answers 404, so a client should not offer it. True still offers "
                        + "it only while a season is under way, which the status endpoint says.")
        boolean restOfSeasonPreset,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether the streamer planner reads the user's own team in the chosen "
                        + "league, to show the nights it has open lineup slots. False means the "
                        + "my-team endpoint answers 404. Never true while streamerPlanner is false.")
        boolean streamerPlannerMyTeam,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether this environment serves the FA scout. False means its "
                        + "endpoint answers 404, so a client should not offer it.")
        boolean faScout,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether this environment serves Role Changes. False means its endpoint "
                        + "answers 404, so a client should not offer it.")
        boolean roleChanges
) {}
