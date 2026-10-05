package com.fantasy.bff.service;

import com.fantasy.bff.generated.projection.model.PlayerResponse;
import com.fantasy.bff.service.mapping.PlayerIdMapping;
import com.fantasy.bff.service.mapping.PlayerIdResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Joins a league's players, numbered by the platform, to the model's NHL ids on <b>identity</b>:
 * name, club and sweater number, never through the player pool's id space. The answer is then right
 * whichever platform the pool is served from; a Yahoo league's players carry Yahoo ids, which an
 * ESPN-numbered pool could not resolve at all. A league's wire and the user's own team are both
 * joined this way.
 */
@Component
public class NhlIdentityJoin {

    private final PlayerSplitContextProvider contextProvider;
    private final PlayerIdResolver resolver;

    public NhlIdentityJoin(PlayerSplitContextProvider contextProvider, PlayerIdResolver resolver) {
        this.contextProvider = contextProvider;
        this.resolver = resolver;
    }

    /** One player as the platform lists him: its id, the name, club and number it has for him. */
    public record PlatformPlayer(String playerId, String name, String teamAbbrev, Integer sweaterNumber) {}

    /** The NHL id each of these players resolved to, where one did. */
    public PlayerIdMapping join(List<PlatformPlayer> players) {
        Map<Long, PlayerResponse> identities = contextProvider.context().identities();
        List<PlayerIdResolver.Candidate> platformSide = new ArrayList<>();
        for (PlatformPlayer player : players) {
            // The resolver works in numeric platform ids; a Yahoo id is a numeric string, so the
            // join key goes back to the platform's own form on the way out.
            Long numeric = numericId(player.playerId());
            if (numeric != null) {
                platformSide.add(new PlayerIdResolver.Candidate(
                        numeric, player.name(), player.teamAbbrev(), player.sweaterNumber()));
            }
        }
        List<PlayerIdResolver.Candidate> nhlSide = new ArrayList<>();
        for (PlayerResponse identity : identities.values()) {
            nhlSide.add(new PlayerIdResolver.Candidate(
                    identity.getNhlId(),
                    identity.getFullName(),
                    PlayerIdResolver.platformTeam(identity.getCurrentTeam()),
                    identity.getSweaterNumber()));
        }
        return resolver.resolve(nhlSide, platformSide, Map.of());
    }

    private static Long numericId(String playerId) {
        if (playerId == null) {
            return null;
        }
        try {
            return Long.valueOf(playerId);
        } catch (NumberFormatException notNumeric) {
            return null;
        }
    }
}
