package com.fantasy.bff.service.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.fantasy.bff.service.mapping.PlayerIdResolver.Candidate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlayerIdResolverTest {

    private final PlayerIdResolver resolver = new PlayerIdResolver();

    private static Candidate nhl(long id, String name, String team, Integer sweater) {
        return new Candidate(id, name, team, sweater);
    }

    private static Candidate platform(int id, String name, String team, Integer sweater) {
        return new Candidate(id, name, team, sweater);
    }

    @Test
    @DisplayName("matches on the normalised full name")
    void matchesOnName() {
        PlayerIdMapping mapping = resolver.resolve(
                List.of(nhl(8478402, "Connor McDavid", "EDM", 97)),
                List.of(platform(5000, "Connor McDavid", "EDM", 97)),
                Map.of());

        assertThat(mapping.platformId(8478402)).contains(5000);
        assertThat(mapping.matchedOnName()).isEqualTo(1);
        assertThat(mapping.coverage()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("folds accents and punctuation the two sources disagree on")
    void foldsAccentsAndPunctuation() {
        PlayerIdMapping mapping = resolver.resolve(
                List.of(
                        nhl(1, "Ryan O'Reilly", "NSH", 90),
                        nhl(2, "Zachary Aston-Reese", "PHI", 20),
                        nhl(3, "Artturi Lehkonen", "COL", 62)),
                List.of(
                        platform(10, "Ryan OReilly", "NSH", 90),
                        platform(20, "Zachary Aston Reese", "PHI", 20),
                        platform(30, "Artturi Lehkonén", "COL", 62)),
                Map.of());

        assertThat(mapping.matched()).isEqualTo(3);
        assertThat(mapping.unmatched()).isEmpty();
    }

    @Test
    @DisplayName("a different team does not block a match")
    void teamIsNotRequired() {
        // Requiring the team dropped measured coverage from 97.4% to 77.9%: the platform's rows
        // are a sync snapshot, so every trade disagrees until it refreshes.
        PlayerIdMapping mapping = resolver.resolve(
                List.of(nhl(1, "Mikko Rantanen", "DAL", 96)),
                List.of(platform(10, "Mikko Rantanen", "COL", 96)),
                Map.of());

        assertThat(mapping.platformId(1)).contains(10);
    }

    @Test
    @DisplayName("falls back to last name and first initial for familiar forms")
    void rescuesNicknames() {
        PlayerIdMapping mapping = resolver.resolve(
                List.of(
                        nhl(1, "Frederick Gaudreau", "SEA", 89),
                        nhl(2, "Sammy Blais", "MTL", 27)),
                List.of(
                        platform(10, "Freddy Gaudreau", "SEA", 89),
                        platform(20, "Samuel Blais", "MTL", 27)),
                Map.of());

        assertThat(mapping.matched()).isEqualTo(2);
        assertThat(mapping.matchedOnFallback()).isEqualTo(2);
    }

    @Test
    @DisplayName("does not use the fallback when the platform has more than one such player")
    void fallbackSkipsAmbiguity() {
        // 19 last-name-plus-initial pairs are shared on the platform side; guessing there
        // would attach a projection to the wrong player.
        PlayerIdMapping mapping = resolver.resolve(
                List.of(nhl(1, "Mike Smith", "EDM", 41)),
                List.of(
                        platform(10, "Michael Smith", "EDM", 41),
                        platform(20, "Matthew Smith", "CGY", 8)),
                Map.of());

        assertThat(mapping.matched()).isZero();
        assertThat(mapping.unmatched())
                .singleElement()
                .extracting(PlayerIdMapping.Unmatched::reason)
                .isEqualTo(PlayerIdMapping.Unmatched.Reason.NOT_ON_PLATFORM);
    }

    @Test
    @DisplayName("separates two players sharing a name and a team by sweater number")
    void sweaterBreaksTheRealCollision() {
        // The one genuine collision among active skaters: two Elias Petterssons on Vancouver.
        // Their team is identical, so only the sweater number decides.
        PlayerIdMapping mapping = resolver.resolve(
                List.of(
                        nhl(8480012, "Elias Pettersson", "VAN", 40),
                        nhl(8483678, "Elias Pettersson", "VAN", 25)),
                List.of(
                        platform(10, "Elias Pettersson", "VAN", 40),
                        platform(20, "Elias Pettersson", "VAN", 25)),
                Map.of());

        assertThat(mapping.platformId(8480012)).contains(10);
        assertThat(mapping.platformId(8483678)).contains(20);
    }

    @Test
    @DisplayName("separates players sharing a name by team when the sweater number is unknown")
    void teamBreaksTiesWhenSweaterIsMissing() {
        // The two Sebastian Ahos play for different teams, so the team settles it even though
        // the NHL side has no sweater number here. Team is a tie break, and this is the tie.
        PlayerIdMapping mapping = resolver.resolve(
                List.of(nhl(1, "Sebastian Aho", "CAR", null)),
                List.of(
                        platform(10, "Sebastian Aho", "CAR", 20),
                        platform(20, "Sebastian Aho", "NYI", 28)),
                Map.of());

        assertThat(mapping.platformId(1)).contains(10);
    }

    @Test
    @DisplayName("reports a shared name it cannot separate rather than guessing")
    void ambiguityIsReported() {
        // Same name, same team, and no sweater number to go on. Nothing decides it, so the
        // player is reported unmatched instead of being attached to a coin flip.
        PlayerIdMapping mapping = resolver.resolve(
                List.of(nhl(1, "Elias Pettersson", "VAN", null)),
                List.of(
                        platform(10, "Elias Pettersson", "VAN", 40),
                        platform(20, "Elias Pettersson", "VAN", 25)),
                Map.of());

        assertThat(mapping.matched()).isZero();
        assertThat(mapping.unmatched())
                .singleElement()
                .extracting(PlayerIdMapping.Unmatched::reason)
                .isEqualTo(PlayerIdMapping.Unmatched.Reason.AMBIGUOUS);
    }

    @Test
    @DisplayName("keeps players the platform does not carry, with a reason")
    void unmatchedPlayersAreReported() {
        // A platform only lists players with fantasy relevance, so an AHL tweener has no
        // counterpart to find. Reporting that is the point; it is how a broken match shows up.
        PlayerIdMapping mapping = resolver.resolve(
                List.of(nhl(1, "Connor McDavid", "EDM", 97), nhl(2, "Jake Lucchini", "NSH", 15)),
                List.of(platform(10, "Connor McDavid", "EDM", 97)),
                Map.of());

        assertThat(mapping.matched()).isEqualTo(1);
        assertThat(mapping.unmatched())
                .singleElement()
                .satisfies(u -> {
                    assertThat(u.name()).isEqualTo("Jake Lucchini");
                    assertThat(u.reason())
                            .isEqualTo(PlayerIdMapping.Unmatched.Reason.NOT_ON_PLATFORM);
                });
        assertThat(mapping.coverage()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("an override wins over any matching")
    void overridesWin() {
        PlayerIdMapping mapping = resolver.resolve(
                List.of(nhl(1, "Connor McDavid", "EDM", 97)),
                List.of(platform(10, "Connor McDavid", "EDM", 97)),
                Map.of(1L, 999));

        assertThat(mapping.platformId(1)).contains(999);
        assertThat(mapping.matchedOnOverride()).isEqualTo(1);
        assertThat(mapping.matchedOnName()).isZero();
    }

    @Test
    @DisplayName("handles the team abbreviations the two sides spell differently")
    void teamAliasesAreApplied() {
        // Only matters for tie breaks, but a tie break comparing TBL against TB never fires.
        PlayerIdMapping mapping = resolver.resolve(
                List.of(nhl(1, "Nikita Kucherov", "TBL", null)),
                List.of(
                        platform(10, "Nikita Kucherov", "TB", 86),
                        platform(20, "Nikita Kucherov", "CHI", 86)),
                Map.of());

        assertThat(mapping.platformId(1)).contains(10);
    }
}
