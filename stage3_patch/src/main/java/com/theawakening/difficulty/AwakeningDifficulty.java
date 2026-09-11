package com.theawakening.difficulty;

/**
 * Global encounter difficulty. This does not modify vanilla Minecraft mobs; it is the authoritative
 * tuning source for THE AWAKENING encounters, bosses, telegraphs and pattern composition.
 */
public enum AwakeningDifficulty {
    NORMAL(new DifficultyProfile(1.00D, 1.00D, 1.00D, 1.00D, 1.00D, 1)),
    HARD(new DifficultyProfile(1.15D, 1.08D, 1.08D, 0.90D, 0.90D, 2)),
    NIGHTMARE(new DifficultyProfile(1.30D, 1.16D, 1.15D, 0.78D, 0.80D, 3)),
    AWAKENED(new DifficultyProfile(1.45D, 1.24D, 1.22D, 0.68D, 0.72D, 4));

    private final DifficultyProfile profile;

    AwakeningDifficulty(DifficultyProfile profile) {
        this.profile = profile;
    }

    public DifficultyProfile profile() {
        return profile;
    }
}
