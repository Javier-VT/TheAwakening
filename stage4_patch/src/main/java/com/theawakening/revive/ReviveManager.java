package com.theawakening.revive;

import com.theawakening.config.ServerConfig;
import com.theawakening.death.DeathEchoManager;
import com.theawakening.difficulty.AwakeningDifficulty;
import com.theawakening.progression.AwakeningWorldProgress;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-authoritative Stage 4 revive state machine.
 *
 * <p>Sessions are encounter-scoped and intentionally transient. Future boss code calls
 * {@link #beginEncounter(ServerPlayer)} at encounter start and {@link #endEncounter(ServerPlayer)}
 * at cleanup. Outside an active session vanilla death remains untouched.</p>
 */
public final class ReviveManager {
    private static final UUID DOWNED_MOVEMENT_UUID = UUID.fromString("f1e16e9b-7f6b-4f65-a164-5a7ce57fd241");
    private static final AttributeModifier DOWNED_MOVEMENT = new AttributeModifier(
        DOWNED_MOVEMENT_UUID, "THE AWAKENING downed movement", -0.88D, AttributeModifier.Operation.MULTIPLY_TOTAL);

    private static final Map<UUID, EncounterSession> SESSIONS = new HashMap<>();
    private static final Map<UUID, DownedState> DOWNED = new HashMap<>();
    private static final Map<UUID, ReviveIntent> INTENTS = new HashMap<>();
    private static final Map<UUID, Long> POST_REVIVE_INVULNERABILITY = new HashMap<>();
    private static final Set<UUID> FORCING_DEATH = new HashSet<>();

    public static void beginEncounter(ServerPlayer player) {
        clearDowned(player);
        clearIntentsFor(player.getUUID());
        AwakeningDifficulty difficulty = AwakeningWorldProgress.get(player.server).difficulty();
        SESSIONS.put(player.getUUID(), new EncounterSession(
            ReviveRules.downedCharges(difficulty), ReviveRules.downedCharges(difficulty), false, difficulty));
        player.displayClientMessage(Component.literal(
            "Revive session active: " + ReviveRules.downedCharges(difficulty) + " DOWNED charge(s) [" + difficulty.name() + "]"), false);
    }

    public static void endEncounter(ServerPlayer player) {
        clearDowned(player);
        clearIntentsFor(player.getUUID());
        POST_REVIVE_INVULNERABILITY.remove(player.getUUID());
        SESSIONS.remove(player.getUUID());
        player.displayClientMessage(Component.literal("Revive session ended."), false);
    }

    public static boolean hasActiveEncounter(ServerPlayer player) {
        EncounterSession session = SESSIONS.get(player.getUUID());
        return session != null && !session.eliminated();
    }

    public static boolean isDowned(ServerPlayer player) {
        return DOWNED.containsKey(player.getUUID());
    }

    public static boolean isPostReviveInvulnerable(ServerPlayer player) {
        Long until = POST_REVIVE_INVULNERABILITY.get(player.getUUID());
        return until != null && player.serverLevel().getGameTime() < until;
    }

    public static boolean isForcingDeath(ServerPlayer player) {
        return FORCING_DEATH.contains(player.getUUID());
    }

    /** Called from cancellable LivingDeathEvent. Returns true when vanilla death must be cancelled. */
    public static boolean tryEnterDowned(ServerPlayer player) {
        if (!ServerConfig.REVIVE_ENABLED.get() || isForcingDeath(player) || isDowned(player)) {
            return false;
        }
        EncounterSession session = SESSIONS.get(player.getUUID());
        if (session == null || session.eliminated() || session.remainingDowns() <= 0) {
            return false;
        }

        int remaining = session.remainingDowns() - 1;
        SESSIONS.put(player.getUUID(), session.withRemainingDowns(remaining));
        long now = player.serverLevel().getGameTime();
        long deadline = now + ReviveRules.downedDurationTicks(session.difficulty());
        DOWNED.put(player.getUUID(), new DownedState(now, deadline, session.difficulty()));

        player.setHealth(1.0F);
        applyDownedRestriction(player);
        player.serverLevel().sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 0.8D, player.getZ(),
            10, 0.35D, 0.45D, 0.35D, 0.01D);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.SHIELD_BREAK,
            SoundSource.PLAYERS, 0.8F, 0.65F);
        player.displayClientMessage(Component.literal(
            "DOWNED — " + ReviveRules.downedDurationSeconds(session.difficulty())
                + "s to be revived. Remaining DOWNED charges after this: " + remaining), false);
        return true;
    }

    /** Development/testing entry point that uses the same state machine without inflicting damage. */
    public static boolean forceDowned(ServerPlayer player) {
        return tryEnterDowned(player);
    }

    public static void markDefinitiveDeath(ServerPlayer player) {
        EncounterSession session = SESSIONS.get(player.getUUID());
        if (session != null) {
            SESSIONS.put(player.getUUID(), session.eliminate());
            clearDowned(player);
            clearIntentsFor(player.getUUID());
            POST_REVIVE_INVULNERABILITY.remove(player.getUUID());
            DeathEchoManager.spawn(player);
        }
    }

    /** Client heartbeat: holding use while keeping a downed player under the crosshair. */
    public static void updateIntent(ServerPlayer reviver, int targetEntityId, boolean active) {
        if (!active || isDowned(reviver)) {
            INTENTS.remove(reviver.getUUID());
            return;
        }
        Entity entity = reviver.level().getEntity(targetEntityId);
        if (!(entity instanceof ServerPlayer target) || target == reviver || !isDowned(target)) {
            INTENTS.remove(reviver.getUUID());
            return;
        }
        long now = reviver.serverLevel().getGameTime();
        ReviveIntent current = INTENTS.get(reviver.getUUID());
        int progress = current != null && current.target().equals(target.getUUID()) ? current.progressTicks() : 0;
        INTENTS.put(reviver.getUUID(), new ReviveIntent(target.getUUID(), now, progress));
    }

    public static void tickPlayer(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        tickInvulnerability(player, now);
        tickDowned(player, now);
        tickReviver(player, now);
    }

    public static Status status(ServerPlayer player) {
        EncounterSession session = SESSIONS.get(player.getUUID());
        DownedState downed = DOWNED.get(player.getUUID());
        if (session == null) {
            return new Status(false, false, false, 0, 0, AwakeningWorldProgress.get(player.server).difficulty());
        }
        int seconds = 0;
        if (downed != null) {
            seconds = (int) Math.max(0L, (downed.deadlineTick() - player.serverLevel().getGameTime() + 19L) / 20L);
        }
        return new Status(true, downed != null, session.eliminated(), session.remainingDowns(), seconds, session.difficulty());
    }

    public static void onRespawn(ServerPlayer player) {
        clearDowned(player);
        clearIntentsFor(player.getUUID());
        POST_REVIVE_INVULNERABILITY.remove(player.getUUID());
    }

    public static void onLogout(ServerPlayer player) {
        clearDowned(player);
        clearIntentsFor(player.getUUID());
        POST_REVIVE_INVULNERABILITY.remove(player.getUUID());
        SESSIONS.remove(player.getUUID());
        FORCING_DEATH.remove(player.getUUID());
    }

    public static void clearAll() {
        SESSIONS.clear();
        DOWNED.clear();
        INTENTS.clear();
        POST_REVIVE_INVULNERABILITY.clear();
        FORCING_DEATH.clear();
    }

    private static void tickInvulnerability(ServerPlayer player, long now) {
        Long until = POST_REVIVE_INVULNERABILITY.get(player.getUUID());
        if (until != null && now >= until) {
            POST_REVIVE_INVULNERABILITY.remove(player.getUUID());
        }
    }

    private static void tickDowned(ServerPlayer player, long now) {
        DownedState state = DOWNED.get(player.getUUID());
        if (state == null) {
            return;
        }

        applyDownedRestriction(player);
        player.setSprinting(false);
        player.stopUsingItem();
        player.setPose(Pose.SWIMMING);
        player.setDeltaMovement(player.getDeltaMovement().multiply(0.35D, 1.0D, 0.35D));
        if (player.getHealth() < 1.0F) {
            player.setHealth(1.0F);
        }

        long remaining = state.deadlineTick() - now;
        if (remaining <= 0L || player.getY() < player.serverLevel().getMinBuildHeight() - 32) {
            forceDefinitiveDeath(player);
            return;
        }
        if (remaining % 20L == 0L) {
            player.displayClientMessage(Component.literal(
                "DOWNED — " + (remaining / 20L) + "s | Hold USE on you for 4s to revive"), true);
        }
    }

    private static void tickReviver(ServerPlayer reviver, long now) {
        ReviveIntent intent = INTENTS.get(reviver.getUUID());
        if (intent == null) {
            return;
        }
        if (now - intent.lastHeartbeatTick() > ReviveRules.INTENT_HEARTBEAT_TICKS) {
            INTENTS.remove(reviver.getUUID());
            return;
        }

        ServerPlayer target = reviver.server.getPlayerList().getPlayer(intent.target());
        if (!isValidRevivePair(reviver, target)) {
            INTENTS.remove(reviver.getUUID());
            reviver.displayClientMessage(Component.literal("Revive interrupted."), true);
            return;
        }

        int progress = intent.progressTicks() + 1;
        INTENTS.put(reviver.getUUID(), intent.withProgress(progress));
        if (progress % 10 == 0) {
            int percent = Math.min(100, progress * 100 / ReviveRules.REVIVE_HOLD_TICKS);
            reviver.displayClientMessage(Component.literal("Reviving " + target.getGameProfile().getName() + " — " + percent + "%"), true);
            target.displayClientMessage(Component.literal("Being revived — " + percent + "%"), true);
        }
        if (progress >= ReviveRules.REVIVE_HOLD_TICKS) {
            revive(reviver, target);
        }
    }

    private static boolean isValidRevivePair(ServerPlayer reviver, ServerPlayer target) {
        if (target == null || !isDowned(target) || isDowned(reviver) || !reviver.isAlive() || reviver.isSpectator()) {
            return false;
        }
        if (reviver.level() != target.level()) {
            return false;
        }
        if (reviver.distanceToSqr(target) > ReviveRules.MAX_REVIVE_DISTANCE * ReviveRules.MAX_REVIVE_DISTANCE) {
            return false;
        }
        return reviver.hasLineOfSight(target);
    }

    private static void revive(ServerPlayer reviver, ServerPlayer target) {
        clearDowned(target);
        clearIntentsFor(target.getUUID());
        long now = target.serverLevel().getGameTime();
        POST_REVIVE_INVULNERABILITY.put(target.getUUID(), now + ReviveRules.POST_REVIVE_INVULNERABILITY_TICKS);
        target.setPose(Pose.STANDING);
        target.setHealth(Math.max(1.0F, (float) (target.getMaxHealth() * ReviveRules.REVIVE_HEALTH_FRACTION)));
        target.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
            target.getX(), target.getY() + 0.9D, target.getZ(), 22, 0.45D, 0.65D, 0.45D, 0.06D);
        target.serverLevel().playSound(null, target.blockPosition(), SoundEvents.PLAYER_LEVELUP,
            SoundSource.PLAYERS, 0.9F, 1.15F);
        target.displayClientMessage(Component.literal("REVIVED — 40% HP | brief invulnerability"), false);
        reviver.displayClientMessage(Component.literal("Revive complete: " + target.getGameProfile().getName()), false);
    }

    private static void forceDefinitiveDeath(ServerPlayer player) {
        UUID id = player.getUUID();
        clearDowned(player);
        clearIntentsFor(id);
        EncounterSession session = SESSIONS.get(id);
        if (session != null) {
            SESSIONS.put(id, session.eliminate());
        }
        FORCING_DEATH.add(id);
        try {
            player.setHealth(1.0F);
            player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
        } finally {
            FORCING_DEATH.remove(id);
        }
    }

    private static void applyDownedRestriction(ServerPlayer player) {
        AttributeInstance movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null && movement.getModifier(DOWNED_MOVEMENT_UUID) == null) {
            movement.addTransientModifier(DOWNED_MOVEMENT);
        }
    }

    private static void clearDowned(ServerPlayer player) {
        DOWNED.remove(player.getUUID());
        AttributeInstance movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null) {
            movement.removeModifier(DOWNED_MOVEMENT_UUID);
        }
        if (player.isAlive()) {
            player.setPose(Pose.STANDING);
        }
    }

    private static void clearIntentsFor(UUID targetOrReviver) {
        INTENTS.remove(targetOrReviver);
        Iterator<Map.Entry<UUID, ReviveIntent>> iterator = INTENTS.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().target().equals(targetOrReviver)) {
                iterator.remove();
            }
        }
    }

    public record Status(boolean active, boolean downed, boolean eliminated, int remainingDowns,
                         int downedSecondsRemaining, AwakeningDifficulty difficulty) {}

    private record EncounterSession(int maxDowns, int remainingDowns, boolean eliminated,
                                    AwakeningDifficulty difficulty) {
        private EncounterSession withRemainingDowns(int value) {
            return new EncounterSession(maxDowns, Math.max(0, value), eliminated, difficulty);
        }

        private EncounterSession eliminate() {
            return new EncounterSession(maxDowns, remainingDowns, true, difficulty);
        }
    }

    private record DownedState(long startTick, long deadlineTick, AwakeningDifficulty difficulty) {}

    private record ReviveIntent(UUID target, long lastHeartbeatTick, int progressTicks) {
        private ReviveIntent withProgress(int value) {
            return new ReviveIntent(target, lastHeartbeatTick, value);
        }
    }

    private ReviveManager() {}
}
