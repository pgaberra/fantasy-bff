package com.fantasy.bff.service;

import com.fantasy.bff.client.PlayerServiceClient;
import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.generated.espn.model.PlayerStatLine;
import com.fantasy.bff.generated.yahoo.model.SyncRunResponse;
import com.fantasy.bff.service.mapping.EspnStatLineIndex;
import com.fantasy.bff.service.mapping.PlayerFieldMapping;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The pool as yahoo-service serves it, with the handful of stats Yahoo does not report at all
 * — hat tricks, shifts, goalie overtime losses and time on ice — merged in from espn-service.
 *
 * <p>The merge is a name match, because nobody publishes a Yahoo-to-ESPN crosswalk. It is the
 * reason the ESPN source exists at all: there, those stats arrive on the player's own row.
 */
@Component
@ConditionalOnProperty(name = "players.source", havingValue = "yahoo", matchIfMissing = true)
public class YahooPlayerPoolSource implements PlayerPoolSource {

    /** Far enough back to find the last good run through a run of failures, and no further. */
    private static final int SYNC_RUNS_TO_SCAN = 10;

    private final PlayerServiceClient playerServiceClient;
    private final EspnPlayerStatsProvider espnPlayerStats;

    public YahooPlayerPoolSource(PlayerServiceClient playerServiceClient,
                                 EspnPlayerStatsProvider espnPlayerStats) {
        this.playerServiceClient = playerServiceClient;
        this.espnPlayerStats = espnPlayerStats;
    }

    @Override
    public String platform() {
        return "yahoo";
    }

    @Override
    public List<SkaterResponse> getSkaters() {
        List<SkaterResponse> skaters = playerServiceClient.getSkaters();
        Map<Integer, PlayerStatLine> statLines = espnPlayerStats.index().matchAll(
                skaters.stream()
                        .map(skater -> new EspnStatLineIndex.Subject(
                                skater.id(), skater.name(), primaryPosition(skater),
                                skater.sweaterNumber()))
                        .toList());
        return skaters.stream().map(skater -> withEspnStats(skater, statLines.get(skater.id()))).toList();
    }

    @Override
    public List<GoalieResponse> getGoalies() {
        List<GoalieResponse> goalies = playerServiceClient.getGoalies();
        Map<Integer, PlayerStatLine> statLines = espnPlayerStats.index().matchAll(
                goalies.stream()
                        .map(goalie -> new EspnStatLineIndex.Subject(
                                goalie.id(), goalie.name(), "G", goalie.sweaterNumber()))
                        .toList());
        return goalies.stream().map(goalie -> withEspnStats(goalie, statLines.get(goalie.id()))).toList();
    }

    @Override
    public Optional<byte[]> getHeadshot(int playerId) {
        return playerServiceClient.getHeadshot(playerId);
    }

    @Override
    public Optional<OffsetDateTime> lastSyncedAt() {
        return playerServiceClient.getSyncRuns(SYNC_RUNS_TO_SCAN).stream()
                .filter(run -> "success".equals(run.getStatus()))
                .map(SyncRunResponse::getFinishedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder());
    }

    private static SkaterResponse withEspnStats(SkaterResponse skater, PlayerStatLine statLine) {
        if (skater.stats() == null || statLine == null) {
            return skater;
        }
        SkaterResponse.ScoringStats scoring = skater.stats().scoring();
        int hatTricks = PlayerFieldMapping.zero(statLine.getHatTricks());
        int shifts = PlayerFieldMapping.zero(statLine.getShifts());
        Integer espnToi = statLine.getTimeOnIce();
        int toi = espnToi == null ? scoring.toi() : espnToi;
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

    private static GoalieResponse withEspnStats(GoalieResponse goalie, PlayerStatLine statLine) {
        if (goalie.stats() == null || statLine == null) {
            return goalie;
        }
        GoalieResponse.ScoringStats scoring = goalie.stats().scoring();
        int otl = PlayerFieldMapping.zero(statLine.getOvertimeLosses());
        int toi = PlayerFieldMapping.zero(statLine.getTimeOnIce());
        return new GoalieResponse(
                goalie.id(), goalie.name(), goalie.teamAbbrev(), goalie.headshot(), goalie.sweaterNumber(),
                new GoalieResponse.Stats(
                        goalie.stats().utility(),
                        new GoalieResponse.ScoringStats(
                                scoring.gs(), scoring.w(), scoring.l(), otl, scoring.sho(), scoring.sa(),
                                scoring.sv(), scoring.ga(), scoring.gaa(), scoring.svPct(),
                                PlayerFieldMapping.winPct(scoring.w(), scoring.l(), otl), toi)));
    }

    /**
     * ESPN's stat line carries one position per player, so the match uses the first of the
     * Yahoo eligible positions — the same order yahoo-service lists them in, primary first.
     */
    private static String primaryPosition(SkaterResponse skater) {
        return skater.positions().stream().findFirst().map(SkaterPosition::name).orElse(null);
    }
}
