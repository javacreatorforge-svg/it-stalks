package com.redstonedev.itstalks.client.renderer;

import com.redstonedev.itstalks.client.model.TheHollowModel;
import com.redstonedev.itstalks.entity.TheHollowEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib3.renderers.geo.GeoEntityRenderer;

@OnlyIn(Dist.CLIENT)
public class TheHollowRenderer extends GeoEntityRenderer<TheHollowEntity> {
    public TheHollowRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new TheHollowModel());
        this.shadowRadius = 0.4F;
    }
}
