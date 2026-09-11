package com.theawakening.client;

import com.theawakening.TheAwakening;
import com.theawakening.network.ModNetwork;
import com.theawakening.network.packet.ReviveIntentPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Sends a low-frequency heartbeat only while USE is held on another player. */
@Mod.EventBusSubscriber(modid = TheAwakening.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientReviveInput {
    private static int lastTarget = -1;
    private static int heartbeat;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            clearIntent();
            return;
        }

        int targetId = -1;
        if (minecraft.options.keyUse.isDown()
            && minecraft.screen == null
            && minecraft.hitResult instanceof EntityHitResult hit
            && hit.getEntity() instanceof Player target
            && target != minecraft.player) {
            targetId = target.getId();
        }

        if (targetId < 0) {
            clearIntent();
            return;
        }

        heartbeat++;
        if (targetId != lastTarget || heartbeat >= 4) {
            lastTarget = targetId;
            heartbeat = 0;
            ModNetwork.CHANNEL.sendToServer(new ReviveIntentPacket(targetId, true));
        }
    }

    private static void clearIntent() {
        heartbeat = 0;
        if (lastTarget >= 0) {
            lastTarget = -1;
            ModNetwork.CHANNEL.sendToServer(new ReviveIntentPacket(-1, false));
        }
    }

    private ClientReviveInput() {}
}
