package com.fantasy.bff.service;

import com.fantasy.bff.dto.response.GoalieResponse;
import com.fantasy.bff.dto.response.SkaterResponse;
import com.fantasy.bff.generated.db.model.PlayerProjection;
import com.fantasy.bff.generated.db.model.PlayerStats;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The player pool as projection rows. A projection covers every player the app serves, so both
 * the rows a new projection starts from and the rows an existing one gains when the pool grows
 * are built from the same read model — and must be built identically, or a player added later
 * would carry different stat keys than the ones created alongside it.
 */
@Service
public class PlayerPoolRows {

    private static final TypeReference<Map<String, Double>> STAT_MAP = new TypeReference<>() {};

    private static final Double ZERO = 0.0;

    private final PlayerService playerService;
    private final ObjectMapper objectMapper;

    public PlayerPoolRows(PlayerService playerService, ObjectMapper objectMapper) {
        this.playerService = playerService;
        this.objectMapper = objectMapper;
    }

    /**
     * Which numbering the ids on these rows are. It travels with them: a projection filled from
     * this pool is stamped with it, and a later id remap trusts that stamp to decide what to
     * translate.
     */
    public PlayerIdSpace playerIdSpace() {
        return playerService.playerIdSpace();
    }

    /** Reads the whole pool once. Both stat maps are kept so a row can be built either way. */
    public Pool read() {
        Map<Integer, Row> rows = new LinkedHashMap<>();
        for (SkaterResponse skater : playerService.getSkaters()) {
            rows.put(skater.id(), new Row(
                    PlayerProjection.TypeEnum.SKATER,
                    toStatMap(skater.stats().utility()),
                    toStatMap(skater.stats().scoring())));
        }
        for (GoalieResponse goalie : playerService.getGoalies()) {
            rows.put(goalie.id(), new Row(
                    PlayerProjection.TypeEnum.GOALIE,
                    toStatMap(goalie.stats().utility()),
                    toStatMap(goalie.stats().scoring())));
        }
        return new Pool(rows);
    }

    /**
     * Converts the typed stat records to the stored map form through Jackson rather than by
     * listing the fields, so the keys stay identical to what the same records serialise to on
     * {@code /api/v1/players/*} — the client's projections are keyed by exactly those names.
     */
    private Map<String, Double> toStatMap(Object stats) {
        return objectMapper.convertValue(stats, STAT_MAP);
    }

    private record Row(PlayerProjection.TypeEnum type,
                       Map<String, Double> utility,
                       Map<String, Double> scoring) {}

    /** One reading of the pool, in the order the app serves it: skaters, then goalies. */
    public static final class Pool {

        private final Map<Integer, Row> rows;

        private Pool(Map<Integer, Row> rows) {
            this.rows = rows;
        }

        public boolean isEmpty() {
            return rows.isEmpty();
        }

        public int size() {
            return rows.size();
        }

        public Collection<Integer> playerIds() {
            return rows.keySet();
        }

        public boolean contains(int playerId) {
            return rows.containsKey(playerId);
        }

        /** Every player in the pool as a row. {@code blank} zeroes the stats. */
        public List<PlayerProjection> all(boolean blank) {
            List<PlayerProjection> players = new ArrayList<>(rows.size());
            rows.keySet().forEach(playerId -> players.add(row(playerId, blank)));
            return players;
        }

        public PlayerProjection row(int playerId, boolean blank) {
            Row row = rows.get(playerId);
            PlayerStats stats = new PlayerStats()
                    .utility(copy(row.utility(), blank))
                    .scoring(copy(row.scoring(), blank));
            return new PlayerProjection()
                    .playerId(playerId)
                    .type(row.type())
                    .stats(stats);
        }

        /** A row owns its stat maps: the pool is read once and may hand out the same player twice. */
        private static Map<String, Double> copy(Map<String, Double> values, boolean blank) {
            Map<String, Double> copied = new LinkedHashMap<>(values.size());
            values.forEach((key, value) -> copied.put(key, blank ? ZERO : value));
            return copied;
        }
    }
}
