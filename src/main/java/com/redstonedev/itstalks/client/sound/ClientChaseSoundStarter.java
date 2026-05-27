package com.redstonedev.itstalks.client.sound;

import com.redstonedev.itstalks.entity.TheHollowEntity;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientChaseSoundStarter {
    private ClientChaseSoundStarter() {}
    public static void start(TheHollowEntity entity) {
        Minecraft.getInstance().getSoundManager().play(new TheHollowChaseSoundInstance(entity));
    }
}
