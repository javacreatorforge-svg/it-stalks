package com.redstonedev.itstalks.client.model;

import com.redstonedev.itstalks.ItStalks;
import com.redstonedev.itstalks.entity.TheHollowEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;

public class TheHollowModel extends AnimatedGeoModel<TheHollowEntity> {
    private static final ResourceLocation MODEL =
            new ResourceLocation(ItStalks.MODID, "geo/the_hollow.geo.json");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ItStalks.MODID, "textures/entity/the_hollow.png");
    private static final ResourceLocation ANIMATIONS =
            new ResourceLocation(ItStalks.MODID, "animations/the_hollow.animation.json");

    @Override public ResourceLocation getModelResource(TheHollowEntity e)     { return MODEL; }
    @Override public ResourceLocation getTextureResource(TheHollowEntity e)   { return TEXTURE; }
    @Override public ResourceLocation getAnimationResource(TheHollowEntity e) { return ANIMATIONS; }
}
