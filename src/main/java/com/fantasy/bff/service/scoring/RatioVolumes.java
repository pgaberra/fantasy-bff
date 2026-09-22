package com.fantasy.bff.service.scoring;

/**
 * How much of a ratio a player carries: the denominator his team's own ratio is summed over. A
 * team's GAA is its goals against over its hours in net, its save percentage its saves over the
 * shots it faced, its win percentage its wins over its decisions, its shooting percentage its
 * goals over its shots. So a goalie moves his team's GAA in proportion to his minutes, and a
 * category ranking has to weigh his rate by them — a 30-game backup's .920 is worth half a
 * 60-game starter's.
 *
 * <p>Every volume is read off the line where it is there and derived from the stats that name it
 * where it is not, since an imported board or a hand-written one can leave out the minutes or the
 * shots. Zero means the line gives nothing to derive a volume from; null means the stat is not a
 * ratio for that kind of player.
 *
 * <p>A port of {@code fantasy-web/src/app/models/ratio-volume.ts}.
 */
public final class RatioVolumes {

    private static final double SECONDS_PER_HOUR = 60 * 60;

    private RatioVolumes() {
    }

    /** The volume behind {@code key} for this player, or null where the stat is not his ratio. */
    public static Double volume(ScoredPlayer player, String key) {
        if (!player.goalie()) {
            return "shPct".equals(key) ? shotsOnGoal(player) : null;
        }
        return switch (key) {
            case "gaa" -> hoursInNet(player);
            case "svPct" -> shotsAgainst(player);
            case "winPct" -> decisions(player);
            default -> null;
        };
    }

    private static double hoursInNet(ScoredPlayer goalie) {
        double toi = goalie.scoringValue("toi");
        if (toi > 0) {
            return toi / SECONDS_PER_HOUR;
        }
        double ga = goalie.scoringValue("ga");
        double gaa = goalie.scoringValue("gaa");
        if (ga > 0 && gaa > 0) {
            return ga / gaa;
        }
        return Math.max(0, goalie.gamesPlayed());
    }

    private static double shotsAgainst(ScoredPlayer goalie) {
        double sa = goalie.scoringValue("sa");
        if (sa > 0) {
            return sa;
        }
        double sv = goalie.scoringValue("sv");
        double svPct = goalie.scoringValue("svPct");
        if (sv > 0 && svPct > 0) {
            return sv / svPct;
        }
        double ga = goalie.scoringValue("ga");
        double goalsAgainst = ga > 0 ? ga : goalie.scoringValue("gaa") * hoursInNet(goalie);
        // A save percentage of zero next to goals against is a stat that was never filled in, not
        // a goalie who stopped nothing, so it names no shots.
        if (goalsAgainst > 0 && svPct > 0 && svPct < 1) {
            return goalsAgainst / (1 - svPct);
        }
        return 0;
    }

    private static double decisions(ScoredPlayer goalie) {
        double decided = goalie.scoringValue("w") + goalie.scoringValue("l") + goalie.scoringValue("otl");
        if (decided > 0) {
            return decided;
        }
        double starts = goalie.scoringValue("gs");
        if (starts > 0) {
            return starts;
        }
        return Math.max(0, goalie.gamesPlayed());
    }

    private static double shotsOnGoal(ScoredPlayer skater) {
        double sog = skater.scoringValue("sog");
        if (sog > 0) {
            return sog;
        }
        double goals = skater.scoringValue("goals");
        double shPct = skater.scoringValue("shPct");
        if (goals > 0 && shPct > 0) {
            return goals / (shPct / 100);
        }
        return 0;
    }
}
