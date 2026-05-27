package com.redstonedev.itstalks.client.overlay;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

@OnlyIn(Dist.CLIENT)
public class StatusOverlay implements IGuiOverlay {
    public static final StatusOverlay INSTANCE = new StatusOverlay();

    @Override
    public void render(net.minecraftforge.client.gui.overlay.ForgeGui gui, PoseStack poseStack,
                       float partialTick, int width, int height) {
        int phase = StatusOverlayState.phase;
        if (phase == 3) return;

        Font font = Minecraft.getInstance().font;
        String text;
        int color;
        float scale;
        if (phase == 0) {
            text  = "Something is watching nearby";
            color = 0xFFC8C8C8; // light gray
            scale = 1.5F;
        } else if (phase == 1) {
            text  = "It is chasing";
            color = 0xFFFFCC00; // yellow
            scale = 2.0F;
        } else {
            text  = "RUN";
            color = 0xFFFF0000; // red
            scale = 4.0F;
        }

        poseStack.pushPose();
        poseStack.translate(width / 2.0F, height / 4.0F, 0);
        poseStack.scale(scale, scale, 1.0F);
        Component comp = Component.literal(text);
        int textWidth = font.width(comp);
        GuiComponent.drawString(poseStack, font, comp, -textWidth / 2, 0, color);
        poseStack.popPose();
    }
}
