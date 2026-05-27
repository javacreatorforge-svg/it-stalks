package com.redstonedev.itstalks.event;

import com.redstonedev.itstalks.ItStalks;
import com.redstonedev.itstalks.entity.TheHollowEntity;
import com.redstonedev.itstalks.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.Random;

public class ForgeEvents {

    private static final Random RNG = new Random();
    private int tickCounter = 0;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickCounter++;
        if (tickCounter % 100 != 0) return; // ~5s
        if (event.getServer() == null) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            trySpawn(level);
        }
    }

    private boolean hasHollow(ServerLevel level) {
        return !level.getEntities(ModEntities.THE_HOLLOW.get(), w -> !w.isRemoved()).isEmpty();
    }

    private void trySpawn(ServerLevel level) {
        List<? extends ServerPlayer> players = level.players();
        if (players.isEmpty()) return;
        if (hasHollow(level)) return;

        for (ServerPlayer player : players) {
            // Night-biased, but can also spawn during day. ~1-in-300 per 5s at night = avg 25min.
            boolean night = !level.isDay();
            int chance = night ? 300 : 1200;
            if (RNG.nextInt(chance) != 0) continue;

            BlockPos spawnPos = pickSpawnPos(level, player);
            if (spawnPos == null) continue;

            TheHollowEntity h = ModEntities.THE_HOLLOW.get().create(level);
            if (h == null) return;
            h.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                    level.getRandom().nextFloat() * 360F, 0);
            h.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos),
                    MobSpawnType.EVENT, null, null);
            level.addFreshEntity(h);
            ItStalks.LOGGER.debug("Spawned The Hollow near {}", player.getName().getString());
            return;
        }
    }

    private BlockPos pickSpawnPos(ServerLevel level, ServerPlayer player) {
        BlockPos origin = player.blockPosition();
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = RNG.nextDouble() * Math.PI * 2.0;
            double dist  = 14 + RNG.nextInt(18); // 14-32 blocks
            int x = origin.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = origin.getZ() + (int) Math.round(Math.sin(angle) * dist);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos candidate = new BlockPos(x, y, z);
            BlockState here = level.getBlockState(candidate);
            BlockState above = level.getBlockState(candidate.above());
            BlockState below = level.getBlockState(candidate.below());
            if (here.isAir() && above.isAir() && !below.isAir()) return candidate;
        }
        return null;
    }
}
