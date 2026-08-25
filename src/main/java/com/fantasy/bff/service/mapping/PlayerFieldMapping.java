package com.fantasy.bff.service.mapping;

import com.fantasy.bff.dto.response.SkaterPosition;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The reshaping every platform's player rows go through on the way to the frontend DTOs.
 *
 * <p>Yahoo and ESPN describe a player differently but the app shows one shape, and the two
 * mappings have to agree exactly: a projection is keyed by the stat names these produce, so a
 * rounding or a position that came out differently per source would read as the player having
 * changed.
 */
public final class PlayerFieldMapping {

    private PlayerFieldMapping() {
    }

    /**
     * Fantasy eligible positions → the frontend enum, falling back to the player's primary
     * position when none of them map (a platform slot the app has no column for).
     */
    public static Set<SkaterPosition> positions(List<String> eligiblePositions, String primaryPosition) {
        Set<SkaterPosition> positions = new LinkedHashSet<>();
        if (eligiblePositions != null) {
            for (String position : eligiblePositions) {
                SkaterPosition mapped = fantasyPosition(position);
                if (mapped != null) {
                    positions.add(mapped);
                }
            }
        }
        if (positions.isEmpty()) {
            positions.add(nhlPosition(primaryPosition));
        }
        return positions;
    }

    public static SkaterPosition fantasyPosition(String position) {
        return switch (position == null ? "" : position.toUpperCase(Locale.ROOT)) {
            case "C" -> SkaterPosition.C;
            case "LW", "L" -> SkaterPosition.LW;
            case "RW", "R" -> SkaterPosition.RW;
            case "D" -> SkaterPosition.D;
            default -> null;
        };
    }

    /** NHL position codes are C/L/R/D; the frontend uses C/LW/RW/D. */
    public static SkaterPosition nhlPosition(String positionCode) {
        return switch (positionCode == null ? "" : positionCode) {
            case "L" -> SkaterPosition.LW;
            case "R" -> SkaterPosition.RW;
            case "D" -> SkaterPosition.D;
            default -> SkaterPosition.C;
        };
    }

    /** Average ice time arrives as "MM:SS" (e.g. "22:59"); the frontend wants seconds per game. */
    public static int toiToSeconds(String avgToi) {
        if (avgToi == null || avgToi.isBlank()) {
            return 0;
        }
        String[] parts = avgToi.split(":");
        if (parts.length != 2) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (NumberFormatException notATime) {
            return 0;
        }
    }

    public static int zero(Integer value) {
        return value == null ? 0 : value;
    }

    public static double zero(Double value) {
        return value == null ? 0.0 : value;
    }

    public static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    /** Share of decisions won. ESPN reports it directly; here it follows from W, L and OTL. */
    public static double winPct(int wins, int losses, int overtimeLosses) {
        int decisions = wins + losses + overtimeLosses;
        return decisions == 0 ? 0.0 : round3((double) wins / decisions);
    }
}
