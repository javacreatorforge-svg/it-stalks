package com.redstonedev.itstalks.init;

import com.redstonedev.itstalks.ItStalks;
import com.redstonedev.itstalks.entity.TheHollowEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ItStalks.MODID);

    public static final RegistryObject<EntityType<TheHollowEntity>> THE_HOLLOW =
            ENTITIES.register("the_hollow", () -> EntityType.Builder
                    .<TheHollowEntity>of(TheHollowEntity::new, MobCategory.MONSTER)
                    .sized(0.9F, 2.4F) // tall and thin
                    .clientTrackingRange(16)
                    .build(new ResourceLocation(ItStalks.MODID, "the_hollow").toString()));
}
