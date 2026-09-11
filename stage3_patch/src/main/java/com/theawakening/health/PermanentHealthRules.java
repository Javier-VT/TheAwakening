package com.theawakening.health;

import net.minecraft.util.Mth;

/**
 * Phase-aware permanent-health rules for THE AWAKENING.
 *
 * <p>Each permanent-heart use grants +2 max HP (one vanilla heart). Phase 2 unlocks the
 * first five uses (20 -> 30 HP) and Phase 3 unlocks the remaining five (30 -> 40 HP).
 * Later phases keep the 40 HP cap unless a future progression stage explicitly changes it.</p>
 */
public final class PermanentHealthRules {
    public static final double BASE_PLAYER_MAX_HEALTH = 20.0D;
    public static final double HEALTH_PER_USE = 2.0D;
    public static final int PHASE_TWO_MAX_USES = 5;
    public static final int ABSOLUTE_MAX_USES = 10;

    private PermanentHealthRules() {}

    public static int maxUsesForPhase(int phase) {
        int clamped = Mth.clamp(phase, 1, 8);
        if (clamped <= 1) {
            return 0;
        }
        if (clamped == 2) {
            return PHASE_TWO_MAX_USES;
        }
        return ABSOLUTE_MAX_USES;
    }

    public static int sanitizeStoredUses(int uses) {
        return Mth.clamp(uses, 0, ABSOLUTE_MAX_USES);
    }

    public static int effectiveUses(int storedUses, int personalPhase) {
        return Math.min(sanitizeStoredUses(storedUses), maxUsesForPhase(personalPhase));
    }

    public static double bonusHealth(int storedUses, int personalPhase) {
        return effectiveUses(storedUses, personalPhase) * HEALTH_PER_USE;
    }

    public static double progressionMaxHealth(int storedUses, int personalPhase) {
        return BASE_PLAYER_MAX_HEALTH + bonusHealth(storedUses, personalPhase);
    }
}
