package com.fantasy.bff.service.mapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Matches NHL players to a fantasy platform's players. Nobody publishes a crosswalk between
 * NHL ids and Yahoo or ESPN ids, so the two sides have to be matched on identity.
 *
 * <p>The rules below come from measuring the alternatives against staging data (950 active
 * skaters, 1407 Yahoo skaters):
 *
 * <ul>
 *   <li><b>Normalised full name</b> — matched 97.4%.
 *   <li><b>Last name plus first initial</b>, only where that form is unique on both sides —
 *       recovered a further 1.3%. These are familiar forms: Yahoo's <i>Freddy</i> Gaudreau for
 *       Frederick, <i>Samuel</i> Blais for Sammy.
 *   <li><b>Sweater number</b> breaks a genuine collision. There is exactly one among active
 *       skaters: two Elias Petterssons, both on Vancouver. Their team doesn't separate them.
 *   <li><b>Team is not a matching key.</b> Requiring it dropped coverage to 77.9%, because the
 *       platform's rows are a sync snapshot while the NHL's team is live — every trade and
 *       signing disagrees until the next sync. It is used only to break ties.
 * </ul>
 *
 * <p>Whatever is left over stays unmatched on purpose. A platform only carries players with
 * fantasy relevance, so fringe and long-term absent players have no counterpart to find.
 */
@Component
public class PlayerIdResolver {

    private static final Logger log = LoggerFactory.getLogger(PlayerIdResolver.class);

    /**
     * The platforms abbreviate a handful of teams differently from the NHL. Only used for tie
     * breaks, but a tie break that compared {@code TBL} against {@code TB} would never fire.
     */
    private static final Map<String, String> TEAM_ALIASES = Map.of(
            "TBL", "TB",
            "LAK", "LA",
            "SJS", "SJ",
            "NJD", "NJ");

    /** One player on either side, reduced to what matching needs. */
    public record Candidate(long id, String name, String team, Integer sweaterNumber) {}

    /**
     * @param nhlPlayers players from the projection service, keyed by NHL id
     * @param platformPlayers players from the platform, keyed by its own id
     * @param overrides manual NHL id → platform id pairs, applied before any matching
     */
    public PlayerIdMapping resolve(
            List<Candidate> nhlPlayers,
            List<Candidate> platformPlayers,
            Map<Long, Integer> overrides) {

        Map<String, List<Candidate>> byFullName = index(platformPlayers, key -> key.fullName());
        Map<String, List<Candidate>> byFallback = index(
                platformPlayers, key -> key.hasFallback() ? key.lastNameInitial() : null);

        Map<Long, Integer> resolved = new LinkedHashMap<>();
        List<PlayerIdMapping.Unmatched> unmatched = new ArrayList<>();
        int onName = 0;
        int onFallback = 0;
        int onOverride = 0;

        for (Candidate nhl : nhlPlayers) {
            Integer override = overrides.get(nhl.id());
            if (override != null) {
                resolved.put(nhl.id(), override);
                onOverride++;
                continue;
            }

            PlayerNameKey key = PlayerNameKey.of(nhl.name());
            Candidate match = pick(byFullName.get(key.fullName()), nhl);
            if (match != null) {
                resolved.put(nhl.id(), (int) match.id());
                onName++;
                continue;
            }

            List<Candidate> byName = byFullName.get(key.fullName());
            if (byName != null && !byName.isEmpty()) {
                // The name exists but nothing separated the candidates — guessing here would
                // silently attach a projection to the wrong player.
                unmatched.add(new PlayerIdMapping.Unmatched(
                        nhl.id(), nhl.name(), nhl.team(), PlayerIdMapping.Unmatched.Reason.AMBIGUOUS));
                continue;
            }

            if (key.hasFallback()) {
                List<Candidate> fallbackCandidates = byFallback.get(key.lastNameInitial());
                // Only when the fallback form is unique on the platform side: 19 last name and
                // initial pairs are shared there, and a familiar-form guess isn't worth a
                // wrong match.
                if (fallbackCandidates != null && fallbackCandidates.size() == 1) {
                    resolved.put(nhl.id(), (int) fallbackCandidates.get(0).id());
                    onFallback++;
                    continue;
                }
            }

            unmatched.add(new PlayerIdMapping.Unmatched(
                    nhl.id(), nhl.name(), nhl.team(), PlayerIdMapping.Unmatched.Reason.NOT_ON_PLATFORM));
        }

        PlayerIdMapping mapping =
                new PlayerIdMapping(resolved, unmatched, onName, onFallback, onOverride);
        log.info(
                "Resolved {}/{} NHL players to platform ids ({}%): {} by name, {} by fallback, "
                        + "{} by override; {} unmatched",
                mapping.matched(),
                mapping.total(),
                Math.round(mapping.coverage() * 100),
                onName,
                onFallback,
                onOverride,
                unmatched.size());
        return mapping;
    }

    /**
     * Picks the one candidate a player matches, using sweater number and then team to separate
     * a genuine collision. Returns null when nothing decides it.
     */
    private Candidate pick(List<Candidate> candidates, Candidate nhl) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        List<Candidate> bySweater = candidates.stream()
                .filter(c -> nhl.sweaterNumber() != null && nhl.sweaterNumber().equals(c.sweaterNumber()))
                .toList();
        if (bySweater.size() == 1) {
            return bySweater.get(0);
        }
        List<Candidate> byTeam = candidates.stream()
                .filter(c -> sameTeam(nhl.team(), c.team()))
                .toList();
        return byTeam.size() == 1 ? byTeam.get(0) : null;
    }

    private boolean sameTeam(String nhlTeam, String platformTeam) {
        if (nhlTeam == null || platformTeam == null) {
            return false;
        }
        String normalised = TEAM_ALIASES.getOrDefault(nhlTeam, nhlTeam);
        return normalised.equalsIgnoreCase(platformTeam);
    }

    private Map<String, List<Candidate>> index(
            List<Candidate> players, java.util.function.Function<PlayerNameKey, String> keyOf) {
        Map<String, List<Candidate>> index = new HashMap<>();
        for (Candidate player : players) {
            String key = keyOf.apply(PlayerNameKey.of(player.name()));
            if (key == null || key.isEmpty()) {
                continue;
            }
            index.computeIfAbsent(key, k -> new ArrayList<>()).add(player);
        }
        return index;
    }

    /** Names that resolve to more than one platform player, for diagnosing a bad match. */
    public Set<String> ambiguousPlatformNames(List<Candidate> platformPlayers) {
        return index(platformPlayers, PlayerNameKey::fullName).entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(Map.Entry::getKey)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
    }
}
