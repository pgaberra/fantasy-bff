package com.fantasy.bff.service.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlayerIdOverridesTest {

    @Test
    @DisplayName("no configuration means no overrides")
    void emptyByDefault() {
        assertThat(new PlayerIdOverrides("").asMap()).isEmpty();
        assertThat(new PlayerIdOverrides(null).asMap()).isEmpty();
    }

    @Test
    @DisplayName("reads nhlId:platformId pairs, tolerating whitespace")
    void parsesPairs() {
        Map<Long, Integer> pairs = new PlayerIdOverrides(" 8480012:5432, 8471234:9876 ").asMap();

        assertThat(pairs).containsExactly(Map.entry(8480012L, 5432), Map.entry(8471234L, 9876));
    }

    @Test
    @DisplayName("refuses a malformed entry rather than skipping it")
    void rejectsMalformed() {
        // Skipping would look identical to an override that was never needed, and would only
        // surface when the player goes missing again.
        assertThatThrownBy(() -> new PlayerIdOverrides("8480012-5432"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nhlId:platformId");

        assertThatThrownBy(() -> new PlayerIdOverrides("mcdavid:5432"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Non-numeric");
    }

    @Test
    @DisplayName("refuses two overrides for the same player")
    void rejectsDuplicates() {
        assertThatThrownBy(() -> new PlayerIdOverrides("8480012:5432,8480012:9876"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    @DisplayName("an override decides a match the name ladder refuses to guess at")
    void overrideBeatsAmbiguity() {
        // Two players sharing a name, a team and a jersey number: nothing in the ladder can
        // separate them, so both would go unmatched. This is what the override is for.
        var nhl = java.util.List.of(new PlayerIdResolver.Candidate(1L, "Sebastian Aho", "NYI", 28));
        var platform = java.util.List.of(
                new PlayerIdResolver.Candidate(700, "Sebastian Aho", "NYI", 28),
                new PlayerIdResolver.Candidate(701, "Sebastian Aho", "NYI", 28));

        PlayerIdMapping without = new PlayerIdResolver().resolve(nhl, platform, Map.of());
        assertThat(without.nhlIdToPlatformId()).isEmpty();

        PlayerIdMapping with = new PlayerIdResolver()
                .resolve(nhl, platform, new PlayerIdOverrides("1:701").asMap());
        assertThat(with.nhlIdToPlatformId()).containsEntry(1L, 701);
    }
}
