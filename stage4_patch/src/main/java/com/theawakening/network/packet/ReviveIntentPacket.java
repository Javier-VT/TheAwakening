package com.theawakening.network.packet;

import com.theawakening.revive.ReviveManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server heartbeat while a player holds USE on a downed teammate. */
public record ReviveIntentPacket(int targetEntityId, boolean active) {
    public static void encode(ReviveIntentPacket message, FriendlyByteBuf buffer) {
        buffer.writeInt(message.targetEntityId);
        buffer.writeBoolean(message.active);
    }

    public static ReviveIntentPacket decode(FriendlyByteBuf buffer) {
        return new ReviveIntentPacket(buffer.readInt(), buffer.readBoolean());
    }

    public static void handle(ReviveIntentPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            ReviveManager.updateIntent(sender, message.targetEntityId(), message.active());
        }
        context.setPacketHandled(true);
    }
}
