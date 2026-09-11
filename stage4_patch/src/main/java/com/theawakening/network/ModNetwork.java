package com.theawakening.network;

import com.theawakening.TheAwakening;
import com.theawakening.network.packet.PlayerProgressSyncPacket;
import com.theawakening.network.packet.ReviveIntentPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "3";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(TheAwakening.MOD_ID, "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    private static boolean initialized;
    private static int nextPacketId;

    public static synchronized void init() {
        if (initialized) {
            return;
        }

        CHANNEL.messageBuilder(PlayerProgressSyncPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(PlayerProgressSyncPacket::encode)
            .decoder(PlayerProgressSyncPacket::decode)
            .consumerMainThread(PlayerProgressSyncPacket::handle)
            .add();

        CHANNEL.messageBuilder(ReviveIntentPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(ReviveIntentPacket::encode)
            .decoder(ReviveIntentPacket::decode)
            .consumerMainThread(ReviveIntentPacket::handle)
            .add();

        initialized = true;
    }

    private ModNetwork() {}
}
