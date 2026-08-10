package com.fantasy.bff.service;

import com.fantasy.bff.client.PlayerServiceClient;
import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.generated.espn.model.PlayerStatLine;
import com.fantasy.bff.service.mapping.EspnStatLineIndex;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Serves the player read model the projections are built from.
 *
 * <p>The stat lines come from yahoo-service, except for the handful of stats Yahoo does not
 * report at all — hat tricks, shifts, goalie overtime losses and time on ice. ESPN scores those
 * as categories, so a league synced from ESPN would otherwise show empty columns; they are
 * merged in here from espn-service's cached ESPN stat lines.
 */
@Service
public class PlayerService {

    private final PlayerServiceClient playerServiceClient;
    private final EspnPlayerStatsProvider espnPlayerStats;

    public PlayerService(PlayerServiceClient playerServiceClient, EspnPlayerStatsProvider espnPlayerStats) {
        this.playerServiceClient = playerServiceClient;
        this.espnPlayerStats = espnPlayerStats;
    }

    public List<SkaterResponse> getSkaters() {
        List<SkaterResponse> skaters;
        try {
            skaters = playerServiceClient.getSkaters();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to retrieve skaters from player service", e);
        }
        EspnStatLineIndex index = espnPlayerStats.index();
        return skaters.stream().map(skater -> withEspnStats(skater, index)).toList();
    }

    public List<GoalieResponse> getGoalies() {
        List<GoalieResponse> goalies;
        try {
            goalies = playerServiceClient.getGoalies();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to retrieve goalies from player service", e);
        }
        EspnStatLineIndex index = espnPlayerStats.index();
        return goalies.stream().map(goalie -> withEspnStats(goalie, index)).toList();
    }

    private static SkaterResponse withEspnStats(SkaterResponse skater, EspnStatLineIndex index) {
        if (skater.stats() == null) {
            return skater;
        }
        Optional<PlayerStatLine> line = index.find(skater.name(), primaryPosition(skater));
        if (line.isEmpty()) {
            return skater;
        }
        SkaterResponse.ScoringStats scoring = skater.stats().scoring();
        int hatTricks = zero(line.get().getHatTricks());
        int shifts = zero(line.get().getShifts());
        int toi = line.get().getTimeOnIce() == null ? scoring.toi() : line.get().getTimeOnIce();
        return new SkaterResponse(
                skater.id(), skater.name(), skater.teamAbbrev(), skater.headshot(), skater.sweaterNumber(),
                skater.positions(),
                new SkaterResponse.Stats(
                        skater.stats().utility(),
                        new SkaterResponse.ScoringStats(
                                scoring.goals(), scoring.assists(), scoring.points(), scoring.plusMinus(),
                                scoring.pim(), scoring.ppg(), scoring.ppa(), scoring.ppp(), scoring.shg(),
                                scoring.sha(), scoring.shp(), scoring.stpg(), scoring.stpa(), scoring.stp(),
                                scoring.gwg(), hatTricks, scoring.sog(), scoring.shPct(), scoring.fw(),
                                scoring.fl(), scoring.hits(), scoring.blocks(), scoring.defPoints(),
                                shifts, toi)));
    }

    private static GoalieResponse withEspnStats(GoalieResponse goalie, EspnStatLineIndex index) {
        if (goalie.stats() == null) {
            return goalie;
        }
        Optional<PlayerStatLine> line = index.find(goalie.name(), "G");
        if (line.isEmpty()) {
            return goalie;
        }
        GoalieResponse.ScoringStats scoring = goalie.stats().scoring();
        int otl = zero(line.get().getOvertimeLosses());
        int toi = zero(line.get().getTimeOnIce());
        return new GoalieResponse(
                goalie.id(), goalie.name(), goalie.teamAbbrev(), goalie.headshot(), goalie.sweaterNumber(),
                new GoalieResponse.Stats(
                        goalie.stats().utility(),
                        new GoalieResponse.ScoringStats(
                                scoring.gs(), scoring.w(), scoring.l(), otl, scoring.sho(), scoring.sa(),
                                scoring.sv(), scoring.ga(), scoring.gaa(), scoring.svPct(),
                                winPct(scoring.w(), scoring.l(), otl), toi)));
    }

    /**
     * ESPN's stat line carries one position per player, so the match uses the first of the
     * Yahoo eligible positions — the same order yahoo-service lists them in, primary first.
     */
    private static String primaryPosition(SkaterResponse skater) {
        return skater.positions().stream().findFirst().map(SkaterPosition::name).orElse(null);
    }

    /** Share of decisions won. ESPN reports it directly; here it follows from W, L and OTL. */
    private static double winPct(int wins, int losses, int overtimeLosses) {
        int decisions = wins + losses + overtimeLosses;
        return decisions == 0 ? 0.0 : Math.round((double) wins / decisions * 1000.0) / 1000.0;
    }

    private static int zero(Integer value) {
        return value == null ? 0 : value;
    }
}
