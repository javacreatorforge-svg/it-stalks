package com.redstonedev.itstalks;

import com.mojang.logging.LogUtils;
import com.redstonedev.itstalks.client.ClientSetup;
import com.redstonedev.itstalks.entity.TheHollowEntity;
import com.redstonedev.itstalks.event.ForgeEvents;
import com.redstonedev.itstalks.init.ModEntities;
import com.redstonedev.itstalks.init.ModItems;
import com.redstonedev.itstalks.init.ModSounds;
import com.redstonedev.itstalks.network.PacketHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import software.bernie.geckolib3.GeckoLib;

@Mod(ItStalks.MODID)
public class ItStalks {
    public static final String MODID = "it_stalks";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ItStalks() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        GeckoLib.initialize();

        ModEntities.ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModSounds.SOUND_EVENTS.register(modBus);

        modBus.addListener(this::commonSetup);
        modBus.addListener(this::clientSetup);
        modBus.addListener(this::entityAttributes);

        MinecraftForge.EVENT_BUS.register(new ForgeEvents());
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(PacketHandler::register);
        LOGGER.info("It Stalks - something watches");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        ClientSetup.onClientSetup(event);
    }

    private void entityAttributes(final EntityAttributeCreationEvent event) {
        event.put(ModEntities.THE_HOLLOW.get(), TheHollowEntity.createAttributes().build());
    }
}
