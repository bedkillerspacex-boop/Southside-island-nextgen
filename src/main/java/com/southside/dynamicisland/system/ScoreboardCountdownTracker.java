package com.southside.dynamicisland.system;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ScoreboardCountdownTracker {

    // [\s\p{Z}] covers ASCII whitespace + all Unicode space separators (incl. U+3000 Chinese space)
    private static final Pattern PATTERN_MINSEC = Pattern.compile(
            "游戏将在[\\s\\p{Z}]*(\\d+):(\\d+)[\\s\\p{Z}]*后开始");
    private static final Pattern PATTERN_SEC    = Pattern.compile(
            "游戏将在[\\s\\p{Z}]*(\\d+)[\\s\\p{Z}]*秒后开始");
    private static final Pattern PATTERN_BARE   = Pattern.compile(
            "^[\\s\\p{Z}]*(\\d+):(\\d+)[\\s\\p{Z}]*$");

    // Debug: dump scoreboard to log every 5 s while no countdown detected
    private static long lastDebugDump = 0;

    private ScoreboardCountdownTracker() {}

    /** Returns total seconds remaining, or -1 if no active countdown found. */
    public static int getCountdownSeconds(MinecraftClient client) {
        if (client.world == null) return -1;
        Scoreboard sb = client.world.getScoreboard();

        boolean doDebug = System.currentTimeMillis() - lastDebugDump > 5000;

        // 1. Check sidebar objective
        ScoreboardObjective sidebar = sb.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (sidebar != null) {
            if (doDebug) System.out.println("[DI-CD] sidebar display='" + sidebar.getDisplayName().getString() + "'");

            int r = matchText(sidebar.getDisplayName().getString());
            if (r >= 0) return r;

            for (var holder : sb.getKnownScoreHolders()) {
                Object2IntMap<ScoreboardObjective> map = sb.getScoreHolderObjectives(holder);
                if (!map.containsKey(sidebar)) continue;

                String hn = holder.getNameForScoreboard();
                if (doDebug) System.out.println("[DI-CD]  holder='" + hn + "'");

                r = matchText(hn);
                if (r >= 0) return r;

                // Also try team prefix + name + suffix
                var abstractTeam = sb.getScoreHolderTeam(hn);
                if (abstractTeam instanceof Team team) {
                    String prefix = team.getPrefix().getString();
                    String suffix = team.getSuffix().getString();
                    if (doDebug) System.out.println("[DI-CD]    team prefix='" + prefix + "' suffix='" + suffix + "'");

                    r = matchText(prefix + hn + suffix);
                    if (r >= 0) return r;
                    r = matchText(prefix);
                    if (r >= 0) return r;
                    r = matchText(suffix);
                    if (r >= 0) return r;
                }
            }
        }

        // 2. Fallback: all known holders
        for (var holder : sb.getKnownScoreHolders()) {
            String hn = holder.getNameForScoreboard();
            int r = matchText(hn);
            if (r >= 0) return r;

            var abstractTeam = sb.getScoreHolderTeam(hn);
            if (abstractTeam instanceof Team team) {
                r = matchText(team.getPrefix().getString() + hn + team.getSuffix().getString());
                if (r >= 0) return r;
            }
        }

        if (doDebug) lastDebugDump = System.currentTimeMillis();
        return -1;
    }

    private static int matchText(String raw) {
        if (raw == null || raw.isEmpty()) return -1;
        // Strip § color codes (§ = U+00A7 or literal §), including any char after them
        String text = raw.replaceAll("[\\u00A7§].", "").trim();

        Matcher m = PATTERN_MINSEC.matcher(text);
        if (m.find()) return Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));

        m = PATTERN_SEC.matcher(text);
        if (m.find()) return Integer.parseInt(m.group(1));

        m = PATTERN_BARE.matcher(text);
        if (m.find()) return Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));

        return -1;
    }
}