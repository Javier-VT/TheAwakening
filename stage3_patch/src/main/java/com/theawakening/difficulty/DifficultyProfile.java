package com.theawakening.difficulty;

import net.minecraft.util.Mth;

/**
 * Runtime tuning profile consumed by boss/raid systems from Stage 9 onward.
 * Values are deliberately centralized so later attacks do not hard-code difficulty branches.
 */
public record DifficultyProfile(
    double bossDamageMultiplier,
    double attackSpeedMultiplier,
    double projectileSpeedMultiplier,
    double telegraphDurationMultiplier,
    double recoveryWindowMultiplier,
    int combinationBudget
) {
    public float scaleBossDamage(float baseDamage) {
        return (float) Math.max(0.0D, baseDamage * bossDamageMultiplier);
    }

    public int scaleTelegraphTicks(int baseTicks) {
        if (baseTicks <= 0) return 0;
        return Math.max(1, Mth.ceil(baseTicks * telegraphDurationMultiplier));
    }

    public int scaleRecoveryTicks(int baseTicks) {
        if (baseTicks <= 0) return 0;
        return Math.max(1, Mth.ceil(baseTicks * recoveryWindowMultiplier));
    }

    public double scaleProjectileSpeed(double baseSpeed) {
        return Math.max(0.0D, baseSpeed * projectileSpeedMultiplier);
    }
}
