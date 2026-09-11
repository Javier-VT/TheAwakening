package com.theawakening.difficulty;

import com.theawakening.progression.AwakeningWorldProgress;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Server-authoritative access point for difficulty-dependent encounter tuning. */
public final class DifficultyManager {
    private DifficultyManager() {}

    public static AwakeningDifficulty current(MinecraftServer server) {
        return AwakeningWorldProgress.get(server).difficulty();
    }

    public static AwakeningDifficulty current(ServerLevel level) {
        return AwakeningWorldProgress.get(level.getServer()).difficulty();
    }

    public static DifficultyProfile profile(MinecraftServer server) {
        return current(server).profile();
    }

    public static DifficultyProfile profile(ServerLevel level) {
        return current(level).profile();
    }
}
