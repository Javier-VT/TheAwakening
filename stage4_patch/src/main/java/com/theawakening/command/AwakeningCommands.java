package com.theawakening.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.theawakening.difficulty.AwakeningDifficulty;
import com.theawakening.difficulty.DifficultyProfile;
import com.theawakening.health.PermanentHealthRules;
import com.theawakening.progression.AwakeningPhase;
import com.theawakening.progression.AwakeningWorldProgress;
import com.theawakening.progression.ProgressionManager;
import com.theawakening.revive.ReviveManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public final class AwakeningCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("awakening")
            .then(Commands.literal("phase")
                .then(Commands.literal("get").executes(context -> getPhase(context.getSource())))
                .then(Commands.literal("set")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("phase", IntegerArgumentType.integer(1, 8))
                        .executes(context -> setPhase(context.getSource(), IntegerArgumentType.getInteger(context, "phase"))))))
            .then(Commands.literal("difficulty")
                .then(Commands.literal("get").executes(context -> getDifficulty(context.getSource())))
                .then(Commands.literal("profile").executes(context -> difficultyProfile(context.getSource())))
                .then(Commands.literal("set")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("difficulty", StringArgumentType.word())
                        .executes(context -> setDifficulty(context.getSource(), StringArgumentType.getString(context, "difficulty"))))))
            .then(Commands.literal("health")
                .then(Commands.literal("get")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> getPlayerHealth(
                            context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("grant")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> grantPlayerHealth(
                            context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("setuses")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("uses", IntegerArgumentType.integer(0, PermanentHealthRules.ABSOLUTE_MAX_USES))
                            .executes(context -> setPlayerHearts(
                                context.getSource(), EntityArgument.getPlayer(context, "player"),
                                IntegerArgumentType.getInteger(context, "uses"))))))
                .then(Commands.literal("reapply")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> reapplyPlayerHealth(
                            context.getSource(), EntityArgument.getPlayer(context, "player"))))))
            .then(Commands.literal("revive")
                .then(Commands.literal("status")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> reviveStatus(
                            context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("begin")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> beginReviveSession(
                            context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("end")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> endReviveSession(
                            context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("down")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> forceDowned(
                            context.getSource(), EntityArgument.getPlayer(context, "player"))))))
            .then(Commands.literal("progression")
                .executes(context -> progression(context.getSource()))
                .then(Commands.literal("get")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> playerProgression(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("setphase")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("phase", IntegerArgumentType.integer(1, 8))
                            .executes(context -> setPlayerPhase(
                                context.getSource(),
                                EntityArgument.getPlayer(context, "player"),
                                IntegerArgumentType.getInteger(context, "phase"))))))
                .then(Commands.literal("hearts")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("uses", IntegerArgumentType.integer(0, PermanentHealthRules.ABSOLUTE_MAX_USES))
                            .executes(context -> setPlayerHearts(
                                context.getSource(),
                                EntityArgument.getPlayer(context, "player"),
                                IntegerArgumentType.getInteger(context, "uses"))))))
                .then(Commands.literal("challenge")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("id", StringArgumentType.word())
                            .executes(context -> completeChallenge(
                                context.getSource(),
                                EntityArgument.getPlayer(context, "player"),
                                StringArgumentType.getString(context, "id"))))))
                .then(Commands.literal("sync")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> syncPlayer(context.getSource(), EntityArgument.getPlayer(context, "player")))))));
    }

    private static int getPhase(CommandSourceStack source) {
        int phase = AwakeningWorldProgress.get(source.getServer()).globalPhase();
        AwakeningPhase named = AwakeningPhase.fromIndex(phase);
        source.sendSuccess(() -> Component.literal("THE AWAKENING global phase: " + phase + " (" + named.name() + ")"), false);
        return phase;
    }

    private static int setPhase(CommandSourceStack source, int phase) {
        AwakeningWorldProgress.get(source.getServer()).setGlobalPhase(phase);
        ProgressionManager.syncAll(source.getServer().getPlayerList().getPlayers());
        source.sendSuccess(() -> Component.literal("THE AWAKENING global phase set to " + phase), true);
        return phase;
    }

    private static int getDifficulty(CommandSourceStack source) {
        AwakeningDifficulty difficulty = AwakeningWorldProgress.get(source.getServer()).difficulty();
        source.sendSuccess(() -> Component.literal("THE AWAKENING difficulty: " + difficulty.name()), false);
        return difficulty.ordinal();
    }

    private static int difficultyProfile(CommandSourceStack source) {
        AwakeningDifficulty difficulty = AwakeningWorldProgress.get(source.getServer()).difficulty();
        DifficultyProfile profile = difficulty.profile();
        source.sendSuccess(() -> Component.literal(
            difficulty.name()
                + " | bossDamage x" + profile.bossDamageMultiplier()
                + " | attackSpeed x" + profile.attackSpeedMultiplier()
                + " | projectileSpeed x" + profile.projectileSpeedMultiplier()
                + " | telegraph x" + profile.telegraphDurationMultiplier()
                + " | recovery x" + profile.recoveryWindowMultiplier()
                + " | comboBudget " + profile.combinationBudget()), false);
        return profile.combinationBudget();
    }

    private static int setDifficulty(CommandSourceStack source, String raw) {
        try {
            AwakeningDifficulty difficulty = AwakeningDifficulty.valueOf(raw.toUpperCase(Locale.ROOT));
            AwakeningWorldProgress.get(source.getServer()).setDifficulty(difficulty);
            ProgressionManager.syncAll(source.getServer().getPlayerList().getPlayers());
            source.sendSuccess(() -> Component.literal("THE AWAKENING difficulty set to " + difficulty.name()), true);
            return difficulty.ordinal();
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal("Unknown difficulty. Use NORMAL, HARD, NIGHTMARE, or AWAKENED."));
            return 0;
        }
    }

    private static int progression(CommandSourceStack source) {
        AwakeningWorldProgress data = AwakeningWorldProgress.get(source.getServer());
        source.sendSuccess(() -> Component.literal(
            "Phase " + data.globalPhase() + " | Difficulty " + data.difficulty().name()
                + " | Bosses " + data.defeatedBosses().size()
                + " | Fate Wheels " + data.completedFateWheels()), false);
        return data.globalPhase();
    }

    private static int playerProgression(CommandSourceStack source, ServerPlayer player) {
        AwakeningWorldProgress.PlayerProgress progress = ProgressionManager.snapshot(player);
        source.sendSuccess(() -> Component.literal(
            player.getGameProfile().getName()
                + " | Phase " + progress.phase()
                + " | Heart uses " + progress.permanentHeartsUsed()
                + " | Fate Wheels " + progress.completedFateWheels()
                + " | Challenges " + progress.challenges().size()), false);
        return progress.phase();
    }

    private static int setPlayerPhase(CommandSourceStack source, ServerPlayer player, int phase) {
        ProgressionManager.setPersonalPhaseAdmin(player, phase);
        source.sendSuccess(() -> Component.literal(
            "Set " + player.getGameProfile().getName() + " personal phase to " + phase), true);
        return phase;
    }

    private static int setPlayerHearts(CommandSourceStack source, ServerPlayer player, int uses) {
        ProgressionManager.setPermanentHeartUses(player, uses);
        source.sendSuccess(() -> Component.literal(
            "Set " + player.getGameProfile().getName() + " permanent heart uses to " + uses), true);
        return uses;
    }

    private static int getPlayerHealth(CommandSourceStack source, ServerPlayer player) {
        AwakeningWorldProgress.PlayerProgress progress = ProgressionManager.snapshot(player);
        int effectiveUses = PermanentHealthRules.effectiveUses(progress.permanentHeartsUsed(), progress.phase());
        int allowedUses = PermanentHealthRules.maxUsesForPhase(progress.phase());
        source.sendSuccess(() -> Component.literal(
            player.getGameProfile().getName()
                + " | Phase " + progress.phase()
                + " | storedUses " + progress.permanentHeartsUsed()
                + " | effectiveUses " + effectiveUses + "/" + allowedUses
                + " | progressionMaxHP " + PermanentHealthRules.progressionMaxHealth(progress.permanentHeartsUsed(), progress.phase())
                + " | liveMaxHP " + player.getMaxHealth()), false);
        return effectiveUses;
    }

    private static int grantPlayerHealth(CommandSourceStack source, ServerPlayer player) {
        boolean granted = ProgressionManager.grantPermanentHeartUse(player);
        AwakeningWorldProgress.PlayerProgress progress = ProgressionManager.snapshot(player);
        if (!granted) {
            source.sendFailure(Component.literal(
                player.getGameProfile().getName() + " has reached the permanent-health cap for Phase " + progress.phase()
                    + " (" + PermanentHealthRules.maxUsesForPhase(progress.phase()) + " uses)."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
            "Granted +" + (int) PermanentHealthRules.HEALTH_PER_USE + " max HP to "
                + player.getGameProfile().getName() + ". Uses: " + progress.permanentHeartsUsed()
                + " | Max HP: " + player.getMaxHealth()), true);
        return progress.permanentHeartsUsed();
    }

    private static int reapplyPlayerHealth(CommandSourceStack source, ServerPlayer player) {
        ProgressionManager.reapplyRuntimeState(player);
        source.sendSuccess(() -> Component.literal(
            "Reapplied permanent health for " + player.getGameProfile().getName()
                + ". Live max HP: " + player.getMaxHealth()), false);
        return (int) player.getMaxHealth();
    }

    private static int beginReviveSession(CommandSourceStack source, ServerPlayer player) {
        ReviveManager.beginEncounter(player);
        ReviveManager.Status status = ReviveManager.status(player);
        source.sendSuccess(() -> Component.literal(
            "Started revive session for " + player.getGameProfile().getName()
                + " | difficulty " + status.difficulty().name()
                + " | DOWNED charges " + status.remainingDowns()), true);
        return status.remainingDowns();
    }

    private static int endReviveSession(CommandSourceStack source, ServerPlayer player) {
        ReviveManager.endEncounter(player);
        source.sendSuccess(() -> Component.literal(
            "Ended revive session for " + player.getGameProfile().getName()), true);
        return 1;
    }

    private static int reviveStatus(CommandSourceStack source, ServerPlayer player) {
        ReviveManager.Status status = ReviveManager.status(player);
        source.sendSuccess(() -> Component.literal(
            player.getGameProfile().getName()
                + " | active=" + status.active()
                + " | downed=" + status.downed()
                + " | eliminated=" + status.eliminated()
                + " | remainingDowns=" + status.remainingDowns()
                + " | timer=" + status.downedSecondsRemaining() + "s"
                + " | difficulty=" + status.difficulty().name()), false);
        return status.remainingDowns();
    }

    private static int forceDowned(CommandSourceStack source, ServerPlayer player) {
        boolean changed = ReviveManager.forceDowned(player);
        if (!changed) {
            source.sendFailure(Component.literal(
                "Could not enter DOWNED. Start a revive session first and ensure charges remain."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
            "Forced DOWNED for " + player.getGameProfile().getName()), true);
        return 1;
    }

    private static int completeChallenge(CommandSourceStack source, ServerPlayer player, String id) {
        boolean changed = ProgressionManager.completeChallenge(player, id);
        source.sendSuccess(() -> Component.literal(
            (changed ? "Completed " : "Already completed ") + id + " for " + player.getGameProfile().getName()), true);
        return changed ? 1 : 0;
    }

    private static int syncPlayer(CommandSourceStack source, ServerPlayer player) {
        ProgressionManager.sync(player);
        source.sendSuccess(() -> Component.literal("Synced progression for " + player.getGameProfile().getName()), false);
        return 1;
    }

    private AwakeningCommands() {}
}
