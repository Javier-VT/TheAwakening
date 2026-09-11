package com.theawakening.progression;

import com.theawakening.health.HealthManager;
import com.theawakening.health.PermanentHealthRules;
import com.theawakening.network.ModNetwork;
import com.theawakening.network.packet.PlayerProgressSyncPacket;
import com.theawakening.player.PlayerData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.Objects;
import java.util.Set;

/**
 * Server-authoritative coordinator for world/player progression.
 * All gameplay code should mutate progression through this class instead of writing NBT directly.
 */
public final class ProgressionManager {
    private ProgressionManager() {}

    public static void reconcileOnLogin(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        AwakeningWorldProgress world = AwakeningWorldProgress.get(player.server);
        PlayerData local = PlayerData.get(player);
        AwakeningWorldProgress.PlayerProgress merged = world.playerSnapshot(player.getUUID());

        AwakeningWorldProgress.PlayerProgress localSnapshot = new AwakeningWorldProgress.PlayerProgress();
        localSnapshot.setPhase(local.personalPhase());
        localSnapshot.setPermanentHeartsUsed(local.permanentHeartUses());
        localSnapshot.setCompletedFateWheels(local.completedFateWheels());
        for (String challenge : local.completedChallenges()) {
            localSnapshot.completeChallenge(challenge);
        }
        merged.mergeMonotonic(localSnapshot);
        world.replacePlayer(player.getUUID(), merged);
        applyToPlayerMirror(player, merged);
        HealthManager.apply(player, merged);
        sync(player);
    }

    public static AwakeningWorldProgress.PlayerProgress snapshot(ServerPlayer player) {
        return AwakeningWorldProgress.get(player.server).playerSnapshot(player.getUUID());
    }

    public static int personalPhase(ServerPlayer player) {
        return snapshot(player).phase();
    }

    public static boolean canAccessGlobalPhase(ServerPlayer player, int phase) {
        int requested = Math.max(1, Math.min(8, phase));
        AwakeningWorldProgress world = AwakeningWorldProgress.get(player.server);
        return requested <= world.globalPhase() && requested <= snapshot(player).phase();
    }

    public static boolean advancePersonalPhase(ServerPlayer player, int phase) {
        AwakeningWorldProgress world = AwakeningWorldProgress.get(player.server);
        int current = world.playerSnapshot(player.getUUID()).phase();
        int requested = Math.max(1, Math.min(8, phase));
        int target = Math.min(requested, world.globalPhase());
        if (target <= current) {
            return false;
        }
        world.updatePlayer(player.getUUID(), progress -> progress.setPhase(target));
        refreshMirrorAndSync(player);
        return true;
    }

    public static void setPersonalPhaseAdmin(ServerPlayer player, int phase) {
        AwakeningWorldProgress world = AwakeningWorldProgress.get(player.server);
        world.updatePlayer(player.getUUID(), progress -> progress.setPhase(phase));
        refreshMirrorAndSync(player);
    }

    public static void setPermanentHeartUses(ServerPlayer player, int uses) {
        AwakeningWorldProgress world = AwakeningWorldProgress.get(player.server);
        AwakeningWorldProgress.PlayerProgress before = world.playerSnapshot(player.getUUID());
        AwakeningWorldProgress.PlayerProgress updated = world.updatePlayer(player.getUUID(), progress ->
            progress.setPermanentHeartsUsed(PermanentHealthRules.sanitizeStoredUses(uses)));
        applyToPlayerMirror(player, updated);
        HealthManager.applyAfterPermanentIncrease(player, before, updated);
        sync(player);
    }

    public static boolean grantPermanentHeartUse(ServerPlayer player) {
        AwakeningWorldProgress world = AwakeningWorldProgress.get(player.server);
        AwakeningWorldProgress.PlayerProgress before = world.playerSnapshot(player.getUUID());
        int allowed = PermanentHealthRules.maxUsesForPhase(before.phase());
        if (before.permanentHeartsUsed() >= allowed) {
            return false;
        }
        AwakeningWorldProgress.PlayerProgress updated = world.updatePlayer(player.getUUID(), progress ->
            progress.setPermanentHeartsUsed(progress.permanentHeartsUsed() + 1));
        applyToPlayerMirror(player, updated);
        HealthManager.applyAfterPermanentIncrease(player, before, updated);
        sync(player);
        return true;
    }

    public static int addPermanentHeartUse(ServerPlayer player) {
        grantPermanentHeartUse(player);
        return snapshot(player).permanentHeartsUsed();
    }

    public static void reapplyRuntimeState(ServerPlayer player) {
        AwakeningWorldProgress.PlayerProgress progress = snapshot(player);
        applyToPlayerMirror(player, progress);
        HealthManager.apply(player, progress);
        sync(player);
    }

    public static boolean completeChallenge(ServerPlayer player, String id) {
        AwakeningWorldProgress world = AwakeningWorldProgress.get(player.server);
        AwakeningWorldProgress.PlayerProgress before = world.playerSnapshot(player.getUUID());
        if (before.challenges().contains(id)) {
            return false;
        }
        world.updatePlayer(player.getUUID(), progress -> progress.completeChallenge(id));
        refreshMirrorAndSync(player);
        return true;
    }

    public static int incrementPlayerFateWheels(ServerPlayer player) {
        AwakeningWorldProgress world = AwakeningWorldProgress.get(player.server);
        AwakeningWorldProgress.PlayerProgress updated = world.updatePlayer(player.getUUID(),
            AwakeningWorldProgress.PlayerProgress::incrementCompletedFateWheels);
        refreshMirrorAndSync(player);
        return updated.completedFateWheels();
    }

    public static void sync(ServerPlayer player) {
        AwakeningWorldProgress world = AwakeningWorldProgress.get(player.server);
        AwakeningWorldProgress.PlayerProgress progress = world.playerSnapshot(player.getUUID());
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
            PlayerProgressSyncPacket.from(progress, world.globalPhase(), world.difficulty()));
    }

    public static void syncAll(Iterable<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            sync(player);
        }
    }

    private static void refreshMirrorAndSync(ServerPlayer player) {
        AwakeningWorldProgress.PlayerProgress progress = snapshot(player);
        applyToPlayerMirror(player, progress);
        HealthManager.apply(player, progress);
        sync(player);
    }

    private static void applyToPlayerMirror(ServerPlayer player, AwakeningWorldProgress.PlayerProgress progress) {
        PlayerData local = PlayerData.get(player);
        local.setPersonalPhase(progress.phase());
        local.setPermanentHeartUses(progress.permanentHeartsUsed());
        local.setCompletedFateWheels(progress.completedFateWheels());
        Set<String> current = local.completedChallenges();
        for (String challenge : progress.challenges()) {
            if (!current.contains(challenge)) {
                local.completeChallenge(challenge);
            }
        }
    }
}
