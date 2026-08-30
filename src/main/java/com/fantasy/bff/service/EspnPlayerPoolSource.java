package com.fantasy.bff.service;

import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterPosition;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.service.mapping.PlayerFieldMapping;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The pool as espn-service serves it. Every stat the app shows is on the player's own row
 * here, including the four Yahoo never reported, so nothing has to be matched in by name.
 *
 * <p>Ids are ESPN's. That is the whole reason this source is behind a flag: a projection saved
 * while Yahoo was the source is keyed by Yahoo's ids, and those rows have to be migrated
 * before the switch is safe.
 */
@Component
@ConditionalOnProperty(name = "players.source", havingValue = "espn")
public class EspnPlayerPoolSource implements PlayerPoolSource {

    @Override
    public PlayerIdSpace playerIdSpace() {
        return PlayerIdSpace.ESPN;
    }

    /**
     * ESPN's own player id is the path segment, so nothing a payload says can steer this
     * request anywhere else. The size is the one the player table draws at.
     */
    private static final String HEADSHOT_PATH =
            "/combiner/i?img=/i/headshots/nhl/players/full/{playerId}.png&w=64&h=64";

    private final EspnServiceClient espnServiceClient;
    private final RestClient espnImageClient;
    private final int statsSeason;

    public EspnPlayerPoolSource(EspnServiceClient espnServiceClient,
                                @Qualifier("espnImageClient") RestClient espnImageClient,
                                @Value("${services.espn-fantasy.stats-season}") int statsSeason) {
        this.espnServiceClient = espnServiceClient;
        this.espnImageClient = espnImageClient;
        this.statsSeason = statsSeason;
    }

    @Override
    public String platform() {
        return "espn";
    }

    /**
     * The limit is ignored: espn-service has no such parameter, so the pool arrives whole and
     * {@link PlayerService} cuts it. Correct, just without the saving the Yahoo source gets.
     */
    @Override
    public List<SkaterResponse> getSkaters(Integer limit) {
        return espnServiceClient.skaters(statsSeason).stream()
                .map(EspnPlayerPoolSource::toSkater)
                .toList();
    }

    /** Ignores the limit, for the reason given on {@link #getSkaters(Integer)}. */
    @Override
    public List<GoalieResponse> getGoalies(Integer limit) {
        return espnServiceClient.goalies(statsSeason).stream()
                .map(EspnPlayerPoolSource::toGoalie)
                .toList();
    }

    @Override
    public Optional<byte[]> getHeadshot(int playerId) {
        return espnImageClient.get()
                .uri(HEADSHOT_PATH, playerId)
                .accept(MediaType.IMAGE_PNG)
                .exchange((request, response) -> {
                    if (response.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                        return Optional.<byte[]>empty();
                    }
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new IllegalStateException(
                                "ESPN returned " + response.getStatusCode() + " for a headshot");
                    }
                    return Optional.of(response.getBody().readAllBytes());
                });
    }

    @Override
    public Optional<OffsetDateTime> lastSyncedAt() {
        var status = espnServiceClient.lastPlayerSync();
        return status == null ? Optional.empty() : Optional.ofNullable(status.getSyncedAt());
    }

    private static SkaterResponse toSkater(com.fantasy.bff.generated.espn.model.SkaterResponse s) {
        int ppg = PlayerFieldMapping.zero(s.getPowerPlayGoals());
        int ppp = PlayerFieldMapping.zero(s.getPowerPlayPoints());
        int shg = PlayerFieldMapping.zero(s.getShorthandedGoals());
        int shp = PlayerFieldMapping.zero(s.getShorthandedPoints());
        int ppa = ppp - ppg;
        int sha = shp - shg;
        int points = PlayerFieldMapping.zero(s.getPoints());
        int gamesPlayed = PlayerFieldMapping.zero(s.getGamesPlayed());
        int toiPerGame = PlayerFieldMapping.toiToSeconds(s.getAvgToi());
        Set<SkaterPosition> positions =
                PlayerFieldMapping.positions(s.getEligiblePositions(), s.getPosition());
        Integer seasonToi = s.getTimeOnIce();
        return new SkaterResponse(
                (int) (long) s.getId(),
                s.getFirstName() + " " + s.getLastName(),
                s.getTeamAbbrev(),
                headshotUrl(s.getHeadshot()),
                s.getSweaterNumber(),
                positions,
                new SkaterResponse.Stats(
                        new SkaterResponse.UtilityStats(gamesPlayed, toiPerGame),
                        new SkaterResponse.ScoringStats(
                                PlayerFieldMapping.zero(s.getGoals()),
                                PlayerFieldMapping.zero(s.getAssists()),
                                points,
                                PlayerFieldMapping.zero(s.getPlusMinus()),
                                PlayerFieldMapping.zero(s.getPim()),
                                ppg,
                                // Neither platform reports power-play or shorthanded *assists*;
                                // they follow from the points.
                                ppa,
                                ppp,
                                shg,
                                sha,
                                shp,
                                // ESPN scores special teams as one category: power play plus
                                // shorthanded. Nothing reports it directly, so it is summed.
                                ppg + shg,
                                ppa + sha,
                                ppp + shp,
                                PlayerFieldMapping.zero(s.getGameWinningGoals()),
                                PlayerFieldMapping.zero(s.getHatTricks()),
                                PlayerFieldMapping.zero(s.getShots()),
                                // shootingPctg is a fraction (0.156); the UI wants percent.
                                PlayerFieldMapping.round1(
                                        PlayerFieldMapping.zero(s.getShootingPctg()) * 100.0),
                                PlayerFieldMapping.zero(s.getTotalFaceoffWins()),
                                PlayerFieldMapping.zero(s.getTotalFaceoffLosses()),
                                PlayerFieldMapping.zero(s.getHits()),
                                PlayerFieldMapping.zero(s.getBlockedShots()),
                                // ESPN's "defensemen points" category counts a player's points
                                // only while they are eligible at defence.
                                positions.contains(SkaterPosition.D) ? points : 0,
                                PlayerFieldMapping.zero(s.getShifts()),
                                // ESPN reports the season total directly; the per-game average
                                // is only a fallback for a row that somehow lacks it.
                                seasonToi == null ? gamesPlayed * toiPerGame : seasonToi)));
    }

    private static GoalieResponse toGoalie(com.fantasy.bff.generated.espn.model.GoalieResponse g) {
        int wins = PlayerFieldMapping.zero(g.getWins());
        int losses = PlayerFieldMapping.zero(g.getLosses());
        int overtimeLosses = PlayerFieldMapping.zero(g.getOvertimeLosses());
        return new GoalieResponse(
                (int) (long) g.getId(),
                g.getFirstName() + " " + g.getLastName(),
                g.getTeamAbbrev(),
                headshotUrl(g.getHeadshot()),
                g.getSweaterNumber(),
                new GoalieResponse.Stats(
                        new GoalieResponse.UtilityStats(PlayerFieldMapping.zero(g.getGamesPlayed())),
                        new GoalieResponse.ScoringStats(
                                PlayerFieldMapping.zero(g.getGamesStarted()),
                                wins,
                                losses,
                                overtimeLosses,
                                PlayerFieldMapping.zero(g.getShutouts()),
                                PlayerFieldMapping.zero(g.getShotsAgainst()),
                                PlayerFieldMapping.zero(g.getSaves()),
                                PlayerFieldMapping.zero(g.getGoalsAgainst()),
                                PlayerFieldMapping.round2(
                                        PlayerFieldMapping.zero(g.getGoalsAgainstAvg())),
                                // savePctg stays a fraction (0.912) — UI convention.
                                PlayerFieldMapping.round3(PlayerFieldMapping.zero(g.getSavePctg())),
                                PlayerFieldMapping.winPct(wins, losses, overtimeLosses),
                                PlayerFieldMapping.zero(g.getTimeOnIce()))));
    }

    /**
     * ESPN's own image URL, handed to the browser to fetch directly.
     *
     * <p>It used to be rewritten to a path on this API and proxied. That put roughly sixteen
     * hundred image requests per page load through one host, and the edge started refusing them
     * — a burst of eighty came back with seven 429s, which the browser draws as broken images.
     * A CDN is the thing that is good at serving the same small picture to everyone, so the
     * browser goes there.
     *
     * <p>espn-service has already dropped the URL for players it has no picture for, so an
     * address here is one that resolves. {@code /api/v1/players/{id}/headshot} still works and
     * still serves it, for the Yahoo pool and for anything holding an older link.
     */
    private static String headshotUrl(String espnSourceUrl) {
        if (espnSourceUrl == null || espnSourceUrl.isBlank()) {
            return null;
        }
        return espnSourceUrl;
    }
}
