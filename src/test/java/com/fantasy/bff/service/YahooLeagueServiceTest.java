package com.fantasy.bff.service;

import com.fantasy.bff.client.YahooServiceClient;
import com.fantasy.bff.dto.response.LeagueProjectionSettingsResponse;
import com.fantasy.bff.generated.yahoo.model.LeagueSettingsResponse;
import com.fantasy.bff.generated.yahoo.model.LeagueSummary;
import com.fantasy.bff.generated.yahoo.model.LeaguesResponse;
import com.fantasy.bff.generated.yahoo.model.RosterSlot;
import com.fantasy.bff.generated.yahoo.model.StatCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class YahooLeagueServiceTest {

    private static final String USER_ID = "user-1";
    private static final String LEAGUE_KEY = "465.l.18249";

    private final YahooServiceClient client = mock(YahooServiceClient.class);
    private final YahooLeagueService service = new YahooLeagueService(client);

    private LeagueSettingsResponse pointsSettings() {
        return new LeagueSettingsResponse()
                .leagueKey(LEAGUE_KEY)
                .name("VNHLFL")
                .scoringType("headpoint")
                .statCategories(List.of(new StatCategory().statId(1).name("Goals").displayName("Goals").pointValue(4.5)))
                .rosterPositions(List.of(new RosterSlot().position("C").count(2)));
    }

    @Test
    void mapsSettingsAndResolvesLeagueSizeFromTheLeagueList() {
        when(client.settings(USER_ID, LEAGUE_KEY)).thenReturn(pointsSettings());
        when(client.leagues(USER_ID)).thenReturn(new LeaguesResponse().leagues(List.of(
                new LeagueSummary().leagueKey(LEAGUE_KEY).name("VNHLFL").numTeams(14),
                new LeagueSummary().leagueKey("other").name("Other").numTeams(8))));

        LeagueProjectionSettingsResponse result = service.projectionSettings(USER_ID, LEAGUE_KEY);

        assertThat(result.scoringType()).isEqualTo("points");
        assertThat(result.leagueSize()).isEqualTo(14);
        assertThat(result.statWeights()).containsEntry("goals", 4.5);
    }

    @Test
    void leavesLeagueSizeNullWhenTheLeagueIsNotInTheList() {
        when(client.settings(USER_ID, LEAGUE_KEY)).thenReturn(pointsSettings());
        when(client.leagues(USER_ID)).thenReturn(new LeaguesResponse().leagues(List.of(
                new LeagueSummary().leagueKey("other").name("Other").numTeams(8))));

        LeagueProjectionSettingsResponse result = service.projectionSettings(USER_ID, LEAGUE_KEY);

        assertThat(result.leagueSize()).isNull();
    }
}
