package com.fantasy.bff.service;

import com.fantasy.bff.config.PlayerAvatarsProperties;
import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Serves the player read model the projections are built from, from whichever
 * {@link PlayerPoolSource} is wired in.
 */
@Service
public class PlayerService {

    private static final Logger log = LoggerFactory.getLogger(PlayerService.class);

    private final PlayerPoolSource playerPool;
    private final HeadshotCache headshots;
    private final PlayerSplitContextProvider playerContext;
    private final boolean avatarsEnabled;

    public PlayerService(
            PlayerPoolSource playerPool,
            HeadshotCache headshots,
            PlayerSplitContextProvider playerContext,
            PlayerAvatarsProperties avatars) {
        this.playerPool = playerPool;
        this.headshots = headshots;
        this.playerContext = playerContext;
        this.avatarsEnabled = avatars.enabled();
    }

    /**
     * Skaters, highest scoring first, capped at {@code limit} when one is given.
     *
     * <p>The order is what makes a limit meaningful: a caller asking for five wants the five the
     * board opens with, not five arbitrary players. Points rather than fantasy points because the
     * weights that turn stats into fantasy points belong to the caller's league, not here — every
     * consumer re-scores what it gets, and points is a wide enough net that the top of any
     * sensible scoring sits inside it.
     */
    public List<SkaterResponse> getSkaters(Integer limit) {
        try {
            Map<Integer, String> teams = playerContext.currentTeams();
            // The limit goes to the source as well as being applied here: a source that can ask
            // its upstream for a slice keeps the rest off the wire, and one that cannot is cut
            // here as before. The sort is what makes the answer the same either way.
            return capped(playerPool.getSkaters(limit).stream()
                    .map(skater -> onCurrentTeam(skater, teams.get(skater.id()),
                            SkaterResponse::teamAbbrev, SkaterResponse::withTeamAbbrev))
                    .map(skater -> avatarsEnabled ? skater : skater.withoutHeadshot())
                    .sorted(Comparator
                            .comparingInt((SkaterResponse skater) -> skater.stats().scoring().points())
                            .reversed()
                            // Ties broken by id so the same request answers the same way twice.
                            .thenComparingInt(SkaterResponse::id))
                    .toList(), limit);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to retrieve skaters from player service", e);
        }
    }

    /** Goalies, most wins first (then most saves), capped at {@code limit} when one is given. */
    public List<GoalieResponse> getGoalies(Integer limit) {
        try {
            Map<Integer, String> teams = playerContext.currentTeams();
            return capped(playerPool.getGoalies(limit).stream()
                    .map(goalie -> onCurrentTeam(goalie, teams.get(goalie.id()),
                            GoalieResponse::teamAbbrev, GoalieResponse::withTeamAbbrev))
                    .map(goalie -> avatarsEnabled ? goalie : goalie.withoutHeadshot())
                    .sorted(Comparator
                            .comparingInt((GoalieResponse goalie) -> goalie.stats().scoring().w())
                            .thenComparingInt(goalie -> goalie.stats().scoring().sv())
                            .reversed()
                            .thenComparingInt(GoalieResponse::id))
                    .toList(), limit);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to retrieve goalies from player service", e);
        }
    }

    /** The whole pool, for the callers that project against every player. */
    public List<SkaterResponse> getSkaters() {
        return getSkaters(null);
    }

    /** The whole pool, for the callers that project against every player. */
    public List<GoalieResponse> getGoalies() {
        return getGoalies(null);
    }

    private static <T> List<T> capped(List<T> players, Integer limit) {
        return limit == null || limit >= players.size() ? players : players.subList(0, limit);
    }

    /**
     * A pool player on the club the NHL lists him on today.
     *
     * <p>The correction happens here rather than in a {@link PlayerPoolSource} for the same
     * reason the headshot framing below does: both sources need it, neither can see the other,
     * and neither of them is where the answer comes from. The pool's own team is whatever its
     * platform held at its last sync, and the sync is not always running — measured on staging
     * on 2026-09-06, with Yahoo's pool three months old, 18% of skaters and 24% of goalies were
     * listed on a club they had left. The projection service follows the NHL daily and knows
     * better, so what it knows is what the app shows.
     *
     * <p>Only ever a replacement, never a removal: a player the model has no team for keeps the
     * pool's answer, because an older club is a better answer than none.
     */
    private static <T> T onCurrentTeam(
            T player, String currentTeam, Function<T, String> teamOf, BiFunction<T, String, T> moveTo) {
        if (currentTeam == null || currentTeam.equalsIgnoreCase(teamOf.apply(player))) {
            return player;
        }
        return moveTo.apply(player, currentTeam);
    }

    /**
     * A player's headshot, framed on the face and sized for the avatar the table draws.
     *
     * <p>The framing happens here rather than in a {@link PlayerPoolSource} because both sources
     * need it and neither can see the other: Yahoo and ESPN both serve a wide frame of the upper
     * body, both need the same square cut out of it, and a rule kept in one of them leaves the
     * other with its own answer. This is where the two meet, so this is where the rule lives.
     *
     * <p>A drawn avatar is held in {@link HeadshotCache}. This used to hold nothing between
     * requests, on the reasoning that the crop is cheap and the browser is told to keep its copy
     * for a week — both true, and both beside the point on a cold cache: the cost is not the crop
     * but the fetch of the source in front of it, and a player table asks for fifty of those at
     * once. Measured on staging, that came back at a median of 844 ms an avatar. The other half
     * of that reasoning — that a cache would hold pictures nobody asked for, past the sync that
     * replaced them — is answered by filling it on demand and expiring what it holds.
     *
     * <p>A source that does not hand the picture over is answered the same way as a source that
     * has none: no picture, and the avatar falls back to initials. It used to be a 502 and an
     * ERROR, which is how a handful of reads of ESPN's image CDN timing out at 10:13 one morning
     * became a fault alarm — for three avatars. A picture that will not arrive is the same class
     * of thing as one that will not decode, which {@link #framed} has always treated this way.
     * Nothing is hidden by that: a source genuinely down fails the skater and goalie reads too,
     * which every reader hits before an avatar and which do still fault loudly, and the reason
     * this one gave is logged. Nothing is remembered either, so the next reader tries again.
     *
     * <p>Where avatars are switched off ({@link PlayerAvatarsProperties}) there is never a picture,
     * and the platform is not asked for one: an address a player list handed out before the switch,
     * or one a share snapshot stored, must not keep drawing the platform's photographs.
     */
    public Optional<byte[]> getHeadshot(int playerId) {
        if (!avatarsEnabled) {
            return Optional.empty();
        }
        Optional<byte[]> held = headshots.get(playerId);
        if (held.isPresent()) {
            return held;
        }
        try {
            Optional<byte[]> drawn = playerPool.getHeadshot(playerId).map(image -> framed(playerId, image));
            drawn.ifPresent(image -> headshots.put(playerId, image));
            return drawn;
        } catch (Exception e) {
            log.warn("Could not fetch the headshot for player {}, serving none: {}",
                    playerId, sanitizeForLog(e.toString()));
            return Optional.empty();
        }
    }

    /**
     * One picture that will not decode is not worth a 502 — the player keeps whatever the source
     * handed over, which is a real picture, just framed the way that platform framed it.
     */
    private static byte[] framed(int playerId, byte[] source) {
        try {
            return HeadshotThumbnailer.toThumbnail(source);
        } catch (Exception e) {
            log.warn("Could not frame the headshot for player {}, serving it as it came: {}",
                    playerId, sanitizeForLog(e.toString()));
            return source;
        }
    }

    /** Keeps a message from an upstream image out of the shape of our own log lines. */
    private static String sanitizeForLog(String message) {
        return message.replace('\r', ' ').replace('\n', ' ');
    }
}
