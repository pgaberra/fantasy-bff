package com.fantasy.bff.service;

import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Serves the player read model the projections are built from, from whichever
 * {@link PlayerPoolSource} is wired in.
 */
@Service
public class PlayerService {

    private static final Logger log = LoggerFactory.getLogger(PlayerService.class);

    private final PlayerPoolSource playerPool;
    private final HeadshotCache headshots;

    public PlayerService(PlayerPoolSource playerPool, HeadshotCache headshots) {
        this.playerPool = playerPool;
        this.headshots = headshots;
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
            // The limit goes to the source as well as being applied here: a source that can ask
            // its upstream for a slice keeps the rest off the wire, and one that cannot is cut
            // here as before. The sort is what makes the answer the same either way.
            return capped(playerPool.getSkaters(limit).stream()
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
            return capped(playerPool.getGoalies(limit).stream()
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
     */
    public Optional<byte[]> getHeadshot(int playerId) {
        Optional<byte[]> held = headshots.get(playerId);
        if (held.isPresent()) {
            return held;
        }
        try {
            Optional<byte[]> drawn = playerPool.getHeadshot(playerId).map(image -> framed(playerId, image));
            drawn.ifPresent(image -> headshots.put(playerId, image));
            return drawn;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to retrieve a headshot from player service", e);
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
