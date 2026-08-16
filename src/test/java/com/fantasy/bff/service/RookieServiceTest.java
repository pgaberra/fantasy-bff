package com.fantasy.bff.service;

import com.fantasy.bff.dto.response.RookiesResponse;
import com.fantasy.bff.generated.projection.model.PlayerResponse;
import com.fantasy.bff.service.mapping.PlayerIdMapping;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Marking rookies is a decoration on a player list, not something the app runs on. The one
 * thing it must not do is turn "we cannot say" into "nobody is one" — which is the answer
 * production would give today, with the projection service switched off.
 */
@ExtendWith(MockitoExtension.class)
class RookieServiceTest {

    @Mock
    private PlayerSplitContextProvider contextProvider;

    private RookiesResponse rookies() {
        return new RookieService(contextProvider).rookies();
    }

    @Test
    void reportsTheRookiesByPlatformId() {
        givenContext(Optional.of(Set.of(42, 7)));

        RookiesResponse response = rookies();

        assertThat(response.known()).isTrue();
        assertThat(response.playerIds()).containsExactly(7, 42);
    }

    @Test
    @DisplayName("an empty answer is still an answer when the history could decide")
    void reportsNoRookiesAsKnown() {
        givenContext(Optional.of(Set.of()));

        assertThat(rookies().known()).isTrue();
        assertThat(rookies().playerIds()).isEmpty();
    }

    @Test
    void reportsUnknownWhenTheHistoryCouldNotDecide() {
        givenContext(Optional.empty());

        assertThat(rookies().known()).isFalse();
        assertThat(rookies().playerIds()).isEmpty();
    }

    /** Production runs with the projection service stopped, so this is the ordinary case there. */
    @Test
    void reportsUnknownRatherThanFailingWhenTheProjectionServiceIsUnreachable() {
        when(contextProvider.context()).thenThrow(new IllegalStateException("projection-service is down"));

        assertThat(rookies().known()).isFalse();
        assertThat(rookies().playerIds()).isEmpty();
    }

    private void givenContext(Optional<Set<Integer>> rookies) {
        when(contextProvider.context()).thenReturn(new PlayerSplitContextProvider.Context(
                new PlayerIdMapping(Map.of(), List.of(), 0, 0, 0),
                Map.<Long, PlayerResponse>of(),
                Set.of(),
                rookies));
    }
}
