package com.theawakening.revive;

import com.theawakening.difficulty.AwakeningDifficulty;

/** Central Stage 4 tuning for boss-encounter downed/revive behavior. */
public final class ReviveRules {
    public static final int REVIVE_HOLD_TICKS = 80; // ~4 seconds
    public static final int POST_REVIVE_INVULNERABILITY_TICKS = 60; // 3 seconds
    public static final int INTENT_HEARTBEAT_TICKS = 8;
    public static final double MAX_REVIVE_DISTANCE = 3.25D;
    public static final double REVIVE_HEALTH_FRACTION = 0.40D;

    public static int downedCharges(AwakeningDifficulty difficulty) {
        return switch (safe(difficulty)) {
            case NORMAL -> 3;
            case HARD -> 2;
            case NIGHTMARE, AWAKENED -> 1;
        };
    }

    /** Keeps the specification's 20-30 second window while tightening with encounter difficulty. */
    public static int downedDurationTicks(AwakeningDifficulty difficulty) {
        return switch (safe(difficulty)) {
            case NORMAL -> 30 * 20;
            case HARD -> 25 * 20;
            case NIGHTMARE -> 22 * 20;
            case AWAKENED -> 20 * 20;
        };
    }

    public static int downedDurationSeconds(AwakeningDifficulty difficulty) {
        return downedDurationTicks(difficulty) / 20;
    }

    private static AwakeningDifficulty safe(AwakeningDifficulty difficulty) {
        return difficulty == null ? AwakeningDifficulty.NORMAL : difficulty;
    }

    private ReviveRules() {}
}
