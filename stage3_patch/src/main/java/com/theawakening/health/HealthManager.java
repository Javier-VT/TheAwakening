package com.theawakening.health;

import com.theawakening.progression.AwakeningWorldProgress;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/** Applies the authoritative permanent-health progression to live server players. */
public final class HealthManager {
    /** Fixed UUID prevents duplicate stacking across login/respawn/dimension changes. */
    public static final UUID PERMANENT_HEALTH_MODIFIER_ID = UUID.fromString("5d6ec8f4-b761-4eb1-8cf0-ea9dd60e6d85");
    private static final String MODIFIER_NAME = "theawakening.permanent_health";

    private HealthManager() {}

    public static void apply(ServerPlayer player, AwakeningWorldProgress.PlayerProgress progress) {
        if (player == null || progress == null) {
            return;
        }
        apply(player, progress.permanentHeartsUsed(), progress.phase());
    }

    public static void apply(ServerPlayer player, int storedUses, int personalPhase) {
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }

        maxHealth.removeModifier(PERMANENT_HEALTH_MODIFIER_ID);

        double bonus = PermanentHealthRules.bonusHealth(storedUses, personalPhase);
        if (bonus > 0.0D) {
            maxHealth.addTransientModifier(new AttributeModifier(
                PERMANENT_HEALTH_MODIFIER_ID,
                MODIFIER_NAME,
                bonus,
                AttributeModifier.Operation.ADDITION));
        }

        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /** Applies progression and restores only newly gained health, never a full heal. */
    public static void applyAfterPermanentIncrease(ServerPlayer player,
                                                   AwakeningWorldProgress.PlayerProgress before,
                                                   AwakeningWorldProgress.PlayerProgress after) {
        double oldBonus = PermanentHealthRules.bonusHealth(before.permanentHeartsUsed(), before.phase());
        double newBonus = PermanentHealthRules.bonusHealth(after.permanentHeartsUsed(), after.phase());
        apply(player, after);
        double gained = Math.max(0.0D, newBonus - oldBonus);
        if (gained > 0.0D) {
            player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + (float) gained));
        }
    }

    public static double expectedProgressionMaxHealth(AwakeningWorldProgress.PlayerProgress progress) {
        return PermanentHealthRules.progressionMaxHealth(progress.permanentHeartsUsed(), progress.phase());
    }
}
