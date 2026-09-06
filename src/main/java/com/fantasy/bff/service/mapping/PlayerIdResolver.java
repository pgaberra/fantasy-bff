package com.fantasy.bff.service.mapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
 *   <li><b>Last name plus first initial</b>, only where that form is unique on both sides, the
 *       platform player it would claim has no NHL namesake of his own, and a sweater number or
 *       team still agrees — recovered a further 1.3%. These are familiar forms: Yahoo's
 *       <i>Freddy</i> Gaudreau for Frederick, <i>Samuel</i> Blais for Sammy.
 *   <li><b>Sweater number</b> breaks a genuine collision, and is <em>required</em> to, where two
 *       NHL players carry one name — a shared name is not enough to claim a row, however few
 *       rows the platform offers. There is exactly one such collision in the live data: two
 *       Elias Petterssons, both on Vancouver, where the team separates nobody.
 *   <li><b>Team is not a matching key.</b> Requiring it dropped coverage to 77.9%, because the
 *       platform's rows are a sync snapshot while the NHL's team is live — every trade and
 *       signing disagrees until the next sync. It is used only to break ties.
 * </ul>
 *
 * <p>Whatever is left over stays unmatched on purpose. A platform only carries players with
 * fantasy relevance, so fringe and long-term absent players have no counterpart to find.
 *
 * <p>The fallback's two guards are what keep that honest, and they were bought the hard way.
 * The NHL side used to hold only players who had already played, so a prospect could not
 * collide with anyone; once the projection store started listing everyone on a roster, Cole
 * Brown took Connor Brown's id, Daniil Orlov took Dmitry Orlov's, and Blake Smith took Brendan
 * Smith's — each an unplayed prospect handed a veteran's row, which then showed the veteran as
 * a rookie. A near-miss on a name is not evidence of the same person when the exact name is
 * sitting right there on the other side, and on a surname like Smith it is barely evidence at
 * all without a sweater number or a team behind it.
 */
@Component
public class PlayerIdResolver {

    private static final Logger log = LoggerFactory.getLogger(PlayerIdResolver.class);

    /**
     * The platforms abbreviate a handful of teams differently from the NHL. Started as a tie
     * break aid — one that compared {@code TBL} against {@code TB} would never fire — and is now
     * also how an NHL team reaches the app, since the pool serves the platform's spelling and a
     * label in the other vocabulary would fall out of the team filter.
     */
    private static final Map<String, String> TEAM_ALIASES = Map.of(
            "TBL", "TB",
            "LAK", "LA",
            "SJS", "SJ",
            "NJD", "NJ");

    /**
     * An NHL team abbreviation in the platform's vocabulary. The two agree on twenty-eight of
     * the thirty-two clubs; this is the other four. Null in, null out.
     */
    public static String platformTeam(String nhlTeam) {
        return nhlTeam == null ? null : TEAM_ALIASES.getOrDefault(nhlTeam, nhlTeam);
    }

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

        // Every full name the NHL side carries, and how many of them share each fallback form.
        // The fallback is a guess at a nickname, and both counts are there to stop it firing
        // where it would be guessing between people rather than between spellings.
        Set<String> nhlFullNames = new HashSet<>();
        Map<String, Integer> nhlFallbackCounts = new HashMap<>();
        Map<String, List<Candidate>> nhlByFullName = new HashMap<>();
        for (Candidate nhl : nhlPlayers) {
            PlayerNameKey key = PlayerNameKey.of(nhl.name());
            nhlFullNames.add(key.fullName());
            nhlByFullName.computeIfAbsent(key.fullName(), k -> new ArrayList<>()).add(nhl);
            if (key.hasFallback()) {
                nhlFallbackCounts.merge(key.lastNameInitial(), 1, Integer::sum);
            }
        }

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
            List<Candidate> namesakes = nhlByFullName.get(key.fullName());
            Candidate match = pick(byFullName.get(key.fullName()), nhl, namesakes);
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

            if (key.hasFallback() && nhlFallbackCounts.getOrDefault(key.lastNameInitial(), 0) == 1) {
                List<Candidate> fallbackCandidates = byFallback.get(key.lastNameInitial());
                // Only when the fallback form is unique on the platform side: 19 last name and
                // initial pairs are shared there, and a familiar-form guess isn't worth a
                // wrong match. Unique on the NHL side too (the guard above), or two players
                // would each be told they are the platform's only candidate.
                if (fallbackCandidates != null && fallbackCandidates.size() == 1) {
                    Candidate candidate = fallbackCandidates.get(0);
                    // ...and never a platform player who has an NHL namesake of his own. He is
                    // that man's row, whether or not that man has been reached yet.
                    boolean spokenFor =
                            nhlFullNames.contains(PlayerNameKey.of(candidate.name()).fullName());
                    if (!spokenFor && corroborates(nhl, candidate)) {
                        resolved.put(nhl.id(), (int) candidate.id());
                        onFallback++;
                        continue;
                    }
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
        warnOnSharedPlatformIds(resolved);
        return mapping;
    }

    /**
     * Picks a row for a player who shares his name with another NHL player.
     *
     * <p>The single-candidate shortcut cannot apply here, and that was the bug: the platform
     * carries one Elias Pettersson, the NHL has two on Vancouver, and each of them was handed
     * that one row without the sweater number ever being consulted. One of the two then had the
     * other's numbers, silently — the flag that reveals it is the same double-claim warning
     * below.
     *
     * <p>What a namesake needs is not corroboration but <em>discrimination</em>: a signal that
     * picks him out from the others carrying his name. A sweater number usually does. A shared
     * team usually does not — namesakes on one roster are precisely the case that makes the
     * collision visible, and "we are both Vancouver" separates nobody. So each signal is
     * required to be unique among the namesakes before it counts, and a player nothing
     * distinguishes is reported unmatched rather than given a coin flip.
     */
    private Candidate pickAmongNamesakes(
            List<Candidate> candidates, Candidate nhl, List<Candidate> namesakes) {
        List<Candidate> bySweater = candidates.stream()
                .filter(c -> distinguishesBySweater(nhl, c, namesakes))
                .toList();
        if (bySweater.size() == 1) {
            return bySweater.get(0);
        }
        List<Candidate> byTeam = candidates.stream()
                .filter(c -> distinguishesByTeam(nhl, c, namesakes))
                .toList();
        return byTeam.size() == 1 ? byTeam.get(0) : null;
    }

    private boolean distinguishesBySweater(
            Candidate nhl, Candidate candidate, List<Candidate> namesakes) {
        if (nhl.sweaterNumber() == null || !nhl.sweaterNumber().equals(candidate.sweaterNumber())) {
            return false;
        }
        return namesakes.stream()
                .noneMatch(other -> other.id() != nhl.id()
                        && nhl.sweaterNumber().equals(other.sweaterNumber()));
    }

    private boolean distinguishesByTeam(
            Candidate nhl, Candidate candidate, List<Candidate> namesakes) {
        if (!sameTeam(nhl.team(), candidate.team())) {
            return false;
        }
        return namesakes.stream()
                .noneMatch(other -> other.id() != nhl.id() && sameTeam(other.team(), candidate.team()));
    }

    /**
     * Whether something other than the near-miss on the name says these are the same person.
     *
     * <p>An exact name is evidence on its own; "same last name, same first initial" is not, and
     * on the commonest surnames it is barely evidence at all. Measured over staging's 1145
     * matches, the twelve the fallback made split perfectly on this test: the nine where a
     * sweater number or a team agreed were all real — Sam for Samuel Montembeault, Zack for
     * Zachary Bolduc, Danny for Danil Zhilkin — and the three where neither did were all
     * different people, every one of them a Smith. A nickname changes the first name and leaves
     * the rest of the man alone, so requiring one of them to still line up costs the real
     * rescues nothing.
     *
     * <p>Team is a weak signal on its own — the platform's rows are a sync snapshot, so a recent
     * trade disagrees — which is why it is not a matching key. As corroboration for a name that
     * already nearly matches, agreeing is what counts; disagreeing only withholds a guess.
     */
    private boolean corroborates(Candidate nhl, Candidate candidate) {
        boolean sameSweater = nhl.sweaterNumber() != null
                && nhl.sweaterNumber().equals(candidate.sweaterNumber());
        return sameSweater || sameTeam(nhl.team(), candidate.team());
    }

    /**
     * Two NHL players mapped to one platform row is always a bad match — the platform has one
     * row per person. It is silent in every downstream reading except the ones that combine
     * players, where the wrong man's answer quietly wins; the rookie flag is a union, so one
     * unplayed prospect sharing a veteran's id is enough to mark the veteran a rookie. Nothing
     * here can tell which of the two is right, so this warns rather than dropping either.
     */
    private void warnOnSharedPlatformIds(Map<Long, Integer> resolved) {
        Map<Integer, Long> claims = new HashMap<>();
        for (Map.Entry<Long, Integer> entry : resolved.entrySet()) {
            claims.merge(entry.getValue(), 1L, Long::sum);
        }
        List<Integer> shared = claims.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (!shared.isEmpty()) {
            log.warn(
                    "{} platform ids are claimed by more than one NHL player, so at least one "
                            + "match is wrong: {}",
                    shared.size(),
                    shared);
        }
    }

    /**
     * Picks the one candidate a player matches, using sweater number and then team to separate
     * a genuine collision. Returns null when nothing decides it.
     */
    private Candidate pick(List<Candidate> candidates, Candidate nhl, List<Candidate> namesakes) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        if (namesakes != null && namesakes.size() > 1) {
            return pickAmongNamesakes(candidates, nhl, namesakes);
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
        return platformTeam(nhlTeam).equalsIgnoreCase(platformTeam);
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
