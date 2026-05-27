package com.redstonedev.itstalks.client.sound;

import com.redstonedev.itstalks.entity.TheHollowEntity;
import com.redstonedev.itstalks.init.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TheHollowChaseSoundInstance extends AbstractTickableSoundInstance {
    private final TheHollowEntity hollow;

    public TheHollowChaseSoundInstance(TheHollowEntity entity) {
        super(ModSounds.CHASE_THEME.get(), SoundSource.HOSTILE, RandomSource.create());
        this.hollow = entity;
        this.looping = true;
        this.delay = 0;
        this.volume = 1.0F;
        this.pitch = 1.0F;
        this.attenuation = Attenuation.LINEAR;
        this.x = entity.getX();
        this.y = entity.getY();
        this.z = entity.getZ();
    }

    @Override
    public void tick() {
        if (hollow.isRemoved() || !hollow.isAlive() || !hollow.isAggressiveMode()) {
            this.stop();
            return;
        }
        this.x = hollow.getX();
        this.y = hollow.getY();
        this.z = hollow.getZ();
    }
}
