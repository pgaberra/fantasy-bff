package com.fantasy.bff.service;

import com.fantasy.bff.dto.response.InjuriesResponse;
import com.fantasy.bff.generated.projection.model.PlayerResponse;
import com.fantasy.bff.service.mapping.PlayerIdMapping;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Marking who is hurt is a decoration on a player list, like the rookie marker, and it fails the
 * same way: it must never turn "we could not ask" into "nobody is hurt", which is the answer
 * production would give today with the projection service switched off.
 */
@ExtendWith(MockitoExtension.class)
class InjuryServiceTest {

    @Mock
    private PlayerSplitContextProvider contextProvider;

    private InjuriesResponse injuries() {
        return new InjuryService(contextProvider).injuries();
    }

    @Test
    void reportsTheInjuredByPlatformId() {
        givenContext(List.of(
                new InjuriesResponse.Injury(42, "Out", "Knee", LocalDate.of(2026, 11, 7)),
                new InjuriesResponse.Injury(7, "Day-To-Day", null, null)));

        InjuriesResponse response = injuries();

        assertThat(response.known()).isTrue();
        assertThat(response.players()).extracting(InjuriesResponse.Injury::playerId)
                .containsExactly(7, 42);
        assertThat(response.players().get(1).bodyPart()).isEqualTo("Knee");
        assertThat(response.players().get(1).expectedReturn()).isEqualTo(LocalDate.of(2026, 11, 7));
    }

    @Test
    @DisplayName("a quiet report is still an answer, not a missing one")
    void reportsAnEmptyReportAsKnown() {
        givenContext(List.of());

        assertThat(injuries().known()).isTrue();
        assertThat(injuries().players()).isEmpty();
    }

    /** Production runs with the projection service stopped, so this is the ordinary case there. */
    @Test
    void reportsUnknownRatherThanFailingWhenTheProjectionServiceIsUnreachable() {
        when(contextProvider.context())
                .thenThrow(new IllegalStateException("projection-service is down"));

        assertThat(injuries().known()).isFalse();
        assertThat(injuries().players()).isEmpty();
    }

    private void givenContext(List<InjuriesResponse.Injury> injuries) {
        when(contextProvider.context()).thenReturn(new PlayerSplitContextProvider.Context(
                new PlayerIdMapping(Map.of(), List.of(), 0, 0, 0),
                Map.<Long, PlayerResponse>of(),
                Set.of(),
                Optional.of(Set.of()),
                injuries,
                Map.of()));
    }
}
