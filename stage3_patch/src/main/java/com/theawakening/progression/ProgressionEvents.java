package com.theawakening.progression;

import com.theawakening.TheAwakening;
import com.theawakening.player.PlayerData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TheAwakening.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ProgressionEvents {
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ProgressionManager.reconcileOnLogin(player);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        PlayerData.copyForClone(event.getOriginal(), event.getEntity());
        if (event.getEntity() instanceof ServerPlayer player) {
            ProgressionManager.reconcileOnLogin(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ProgressionManager.reapplyRuntimeState(player);
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ProgressionManager.reapplyRuntimeState(player);
        }
    }

    private ProgressionEvents() {}
}
