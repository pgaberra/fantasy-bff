package com.fantasy.bff.dto.request;

/**
 * A stretch of a team's schedule, in team game numbers.
 *
 * <p>Expressed the same way for every player, so "games 50-82" covers the same nights whether or
 * not a given player dressed for all of them. Either an explicit {@code fromGame}/{@code toGame}
 * pair or a {@code lastGames} shorthand — never both, since a range that says two different
 * things would silently resolve to one of them.
 *
 * <p>Any bound may be left out: an open range means the whole season.
 */
public record GameRange(Integer fromGame, Integer toGame, Integer lastGames) {

    public GameRange {
        if (lastGames != null && (fromGame != null || toGame != null)) {
            throw new IllegalArgumentException("Use either lastGames or fromGame/toGame, not both");
        }
        if (fromGame != null && toGame != null && fromGame > toGame) {
            throw new IllegalArgumentException("fromGame must not exceed toGame");
        }
    }

    public static GameRange ofLastGames(int lastGames) {
        return new GameRange(null, null, lastGames);
    }

    public static GameRange season() {
        return new GameRange(null, null, null);
    }
}
