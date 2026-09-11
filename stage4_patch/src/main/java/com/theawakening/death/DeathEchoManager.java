package com.theawakening.death;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Lightweight definitive-death visual. No ArmorStand/entity spam: the server stores only a tiny
 * transient descriptor and asks nearby clients to render vanilla particles for ~2 seconds.
 */
public final class DeathEchoManager {
    private static final int DURATION_TICKS = 40;
    private static final List<DeathEcho> ACTIVE = new ArrayList<>();

    public static void spawn(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        ACTIVE.add(new DeathEcho(level.dimension(), player.position(), now, now + DURATION_TICKS));
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
            SoundSource.PLAYERS, 0.85F, 0.65F);
        level.sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 0.8D, player.getZ(),
            12, 0.35D, 0.65D, 0.35D, 0.02D);
    }

    public static void tick(MinecraftServer server) {
        Iterator<DeathEcho> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            DeathEcho echo = iterator.next();
            ServerLevel level = server.getLevel(echo.dimension());
            if (level == null) {
                iterator.remove();
                continue;
            }
            long now = level.getGameTime();
            if (now >= echo.endTick()) {
                level.sendParticles(ParticleTypes.POOF, echo.position().x, echo.position().y + 1.0D,
                    echo.position().z, 8, 0.28D, 0.55D, 0.28D, 0.01D);
                iterator.remove();
                continue;
            }
            if ((now - echo.startTick()) % 4L != 0L) {
                continue;
            }

            double progress = (double) (now - echo.startTick()) / DURATION_TICKS;
            double y = echo.position().y + 0.35D + progress * 1.55D;
            double spread = Math.max(0.08D, 0.30D * (1.0D - progress));
            level.sendParticles(ParticleTypes.SOUL, echo.position().x, y, echo.position().z,
                3, spread, 0.32D, spread, 0.008D);
            level.sendParticles(ParticleTypes.END_ROD, echo.position().x, y + 0.25D, echo.position().z,
                1, spread * 0.6D, 0.20D, spread * 0.6D, 0.005D);
        }
    }

    public static void clear() {
        ACTIVE.clear();
    }

    private record DeathEcho(ResourceKey<Level> dimension, Vec3 position, long startTick, long endTick) {}

    private DeathEchoManager() {}
}
