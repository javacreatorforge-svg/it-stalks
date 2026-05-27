package com.redstonedev.itstalks.client.overlay;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Static state for the status overlay.
 *   phase 0 = STALKING ("Something is watching nearby" - light gray)
 *   phase 1 = CHASING  ("It is chasing" - yellow)
 *   phase 2 = AGGRESSIVE ("RUN" - red)
 *   phase 3 = clear
 */
@OnlyIn(Dist.CLIENT)
public final class StatusOverlayState {
    private StatusOverlayState() {}
    public static volatile int phase = 3;
    public static void set(int p) { phase = p; }
}
