package com.redstonedev.itstalks.init;

import com.redstonedev.itstalks.ItStalks;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ItStalks.MODID);

    // Colors sampled from the_hollow.png: pale white + light grey spots.
    public static final RegistryObject<ForgeSpawnEggItem> THE_HOLLOW_SPAWN_EGG =
            ITEMS.register("the_hollow_spawn_egg",
                    () -> new ForgeSpawnEggItem(
                            ModEntities.THE_HOLLOW,
                            0xFFFFFF,  // pale white
                            0xDADEDF,  // light grey
                            new Item.Properties().tab(CreativeModeTab.TAB_MISC)));
}
