package com.fantasy.bff.client;

import com.fantasy.bff.generated.espn.model.AvailablePlayer;
import com.fantasy.bff.generated.espn.model.CredentialStatusResponse;
import com.fantasy.bff.generated.espn.model.LeagueSettingsResponse;
import com.fantasy.bff.generated.espn.model.LeagueTeamsResponse;
import com.fantasy.bff.generated.espn.model.PlayerStatsResponse;
import com.fantasy.bff.generated.espn.model.PlayerSyncStatusResponse;
import com.fantasy.bff.generated.espn.model.SyncAcceptedResponse;

import java.util.List;

public interface EspnServiceClient {

    CredentialStatusResponse credentialStatus(String appUserId);

    void saveCredentials(String appUserId, String espnS2, String swid);

    void deleteCredentials(String appUserId);

    LeagueSettingsResponse settings(String appUserId, String leagueId);

    LeagueTeamsResponse teams(String appUserId, String leagueId);

    /** Cached ESPN season stat lines for the stats Yahoo does not report. Not user-specific. */
    PlayerStatsResponse playerStats();

    /**
     * The whole skater pool with the given season's stats, as espn-service caches it.
     *
     * @param season season start year — 2025 is the 2025-26 season
     */
    List<com.fantasy.bff.generated.espn.model.SkaterResponse> skaters(int season);

    /** The whole goalie pool with the given season's stats. */
    List<com.fantasy.bff.generated.espn.model.GoalieResponse> goalies(int season);

    /** When espn-service last refreshed its cached pool. Cheap enough to ask on every read. */
    PlayerSyncStatusResponse lastPlayerSync();

    /**
     * Starts a refresh of the cached player pool and returns as soon as it is under way. The
     * work runs for minutes; {@link #lastPlayerSync()} is how it is watched to completion.
     */
    SyncAcceptedResponse triggerPlayerSync();

    /** The players a league has available: free agents and players on waivers together. */
    List<AvailablePlayer> leagueFreeAgents(String appUserId, String leagueId, int limit);
}
