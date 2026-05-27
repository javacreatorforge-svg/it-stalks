package com.redstonedev.itstalks.network;

import com.redstonedev.itstalks.ItStalks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ItStalks.MODID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private static int nextId = 0;

    public static void register() {
        CHANNEL.registerMessage(nextId++,
                StatusOverlayPacket.class,
                StatusOverlayPacket::encode,
                StatusOverlayPacket::decode,
                StatusOverlayPacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /**
     * Tells the client which status overlay to show.
     *   phase 0 = STALKING ("Something is watching nearby" - light gray)
     *   phase 1 = CHASING  ("It is chasing" - yellow)
     *   phase 2 = AGGRESSIVE ("RUN" - red)
     *   phase 3 = clear
     */
    public static class StatusOverlayPacket {
        public final int phase;

        public StatusOverlayPacket(int phase) { this.phase = phase; }

        public static void encode(StatusOverlayPacket p, FriendlyByteBuf buf) { buf.writeInt(p.phase); }
        public static StatusOverlayPacket decode(FriendlyByteBuf buf) { return new StatusOverlayPacket(buf.readInt()); }

        public static void handle(StatusOverlayPacket p, java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                        net.minecraftforge.api.distmarker.Dist.CLIENT,
                        () -> () -> com.redstonedev.itstalks.client.overlay.StatusOverlayState.set(p.phase));
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
