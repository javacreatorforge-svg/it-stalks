package com.redstonedev.itstalks.init;

import com.redstonedev.itstalks.ItStalks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ItStalks.MODID);

    public static final RegistryObject<SoundEvent> STALKING    = register("hollow_stalking");
    public static final RegistryObject<SoundEvent> CHASE_THEME = register("hollow_chase_theme");
    public static final RegistryObject<SoundEvent> HURT        = register("hollow_hurt");
    public static final RegistryObject<SoundEvent> DEATH       = register("hollow_death");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name,
                () -> new SoundEvent(new ResourceLocation(ItStalks.MODID, name)));
    }
}
