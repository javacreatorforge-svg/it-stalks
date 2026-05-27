package com.redstonedev.itstalks.client;

import com.redstonedev.itstalks.ItStalks;
import com.redstonedev.itstalks.client.overlay.StatusOverlay;
import com.redstonedev.itstalks.client.renderer.TheHollowRenderer;
import com.redstonedev.itstalks.init.ModEntities;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class ClientSetup {
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            EntityRenderers.register(ModEntities.THE_HOLLOW.get(), TheHollowRenderer::new);
        });
    }

    @Mod.EventBusSubscriber(modid = ItStalks.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class OverlayRegistration {
        @SubscribeEvent
        public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "it_stalks_status", StatusOverlay.INSTANCE);
        }
    }
}
