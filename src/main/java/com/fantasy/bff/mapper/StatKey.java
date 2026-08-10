package com.fantasy.bff.mapper;

/**
 * The projection stat vocabulary the mapper emits. Internal to the BFF mapping; the wire
 * value ({@link #key()}) is what ends up in the string-keyed response (and what db-service
 * persists), so this stays the single in-mapper source for the stat ids and the weight set.
 */
enum StatKey {
    GP("gp", Kind.UTILITY),
    TOI_PER_GAME("toiPerGame", Kind.UTILITY),
    GOALS("goals", Kind.SCORING),
    ASSISTS("assists", Kind.SCORING),
    POINTS("points", Kind.SCORING),
    PLUS_MINUS("plusMinus", Kind.SCORING),
    PIM("pim", Kind.SCORING),
    PPG("ppg", Kind.SCORING),
    PPA("ppa", Kind.SCORING),
    PPP("ppp", Kind.SCORING),
    SHG("shg", Kind.SCORING),
    SHA("sha", Kind.SCORING),
    SHP("shp", Kind.SCORING),
    STPG("stpg", Kind.SCORING),
    STPA("stpa", Kind.SCORING),
    STP("stp", Kind.SCORING),
    GWG("gwg", Kind.SCORING),
    HAT_TRICKS("hatTricks", Kind.SCORING),
    SOG("sog", Kind.SCORING),
    SH_PCT("shPct", Kind.SCORING),
    FW("fw", Kind.SCORING),
    FL("fl", Kind.SCORING),
    HITS("hits", Kind.SCORING),
    BLOCKS("blocks", Kind.SCORING),
    DEF_POINTS("defPoints", Kind.SCORING),
    SHIFTS("shifts", Kind.SCORING),
    TOI("toi", Kind.SCORING),
    GS("gs", Kind.SCORING),
    W("w", Kind.SCORING),
    L("l", Kind.SCORING),
    OTL("otl", Kind.SCORING),
    SHO("sho", Kind.SCORING),
    SA("sa", Kind.SCORING),
    SV("sv", Kind.SCORING),
    GA("ga", Kind.SCORING),
    GAA("gaa", Kind.SCORING),
    SV_PCT("svPct", Kind.SCORING),
    WIN_PCT("winPct", Kind.SCORING);

    private enum Kind {
        SCORING,
        UTILITY
    }

    private final String key;
    private final Kind kind;

    StatKey(String key, Kind kind) {
        this.key = key;
        this.kind = kind;
    }

    String key() {
        return key;
    }

    boolean isUtility() {
        return kind == Kind.UTILITY;
    }
}
