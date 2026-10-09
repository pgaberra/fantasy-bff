package com.fantasy.bff.service.scoring;

import com.fantasy.bff.dto.response.RosterSlots;
import java.util.Set;

/**
 * A lineup slot a league starts players in, and who may fill it. The bench is not one: a player
 * there scores nothing that night.
 *
 * <p>Declared in the order a manager reads a lineup: the forwards, the forward flex slots
 * narrowest first, the defence, Util, the goalies.
 */
enum LineupSlot {
    LW("LW"),
    C("C"),
    RW("RW"),
    W("W"),
    F("F"),
    D("D"),
    UTIL("UTIL"),
    G("G");

    private final String column;

    LineupSlot(String column) {
        this.column = column;
    }

    /** The slot's key in the summary's position columns. */
    String column() {
        return column;
    }

    int count(RosterSlots slots) {
        return switch (this) {
            case C -> slots.c();
            case LW -> slots.lw();
            case RW -> slots.rw();
            case W -> slots.w();
            case F -> slots.f();
            case D -> slots.d();
            case UTIL -> slots.util();
            case G -> slots.g();
        };
    }

    boolean eligible(boolean goalie, Set<String> positions) {
        return switch (this) {
            case C -> !goalie && positions.contains("C");
            case LW -> !goalie && positions.contains("LW");
            case RW -> !goalie && positions.contains("RW");
            case W -> !goalie && (positions.contains("LW") || positions.contains("RW"));
            case F -> !goalie
                    && (positions.contains("C") || positions.contains("LW") || positions.contains("RW"));
            case D -> !goalie && positions.contains("D");
            case UTIL -> !goalie;
            case G -> goalie;
        };
    }

    /** Whether a slot takes more than one position, and so is tried after the named ones. */
    boolean flex() {
        return this == W || this == F || this == UTIL;
    }
}
