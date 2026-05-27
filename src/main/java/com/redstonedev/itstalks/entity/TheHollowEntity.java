package com.redstonedev.itstalks.entity;

import com.redstonedev.itstalks.init.ModSounds;
import com.redstonedev.itstalks.network.PacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import software.bernie.geckolib3.core.AnimationState;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.builder.ILoopType.EDefaultLoopTypes;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import software.bernie.geckolib3.util.GeckoLibUtil;

public class TheHollowEntity extends Monster implements IAnimatable {

    public enum Mode { STALKING, CHASING, AGGRESSIVE }

    /** Animation pose synced to the client. */
    public enum Pose { IDLE, STALKING_POSE, RUN, CRAWL, CROUCH, CLIMB, ATTACK, ATTACK2 }

    private static final EntityDataAccessor<Integer> DATA_MODE =
            SynchedEntityData.defineId(TheHollowEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_POSE =
            SynchedEntityData.defineId(TheHollowEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_CLIMBING =
            SynchedEntityData.defineId(TheHollowEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_GRAB_TARGET =
            SynchedEntityData.defineId(TheHollowEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ATTACK_VARIANT =
            SynchedEntityData.defineId(TheHollowEntity.class, EntityDataSerializers.INT);

    // Animation lengths in ticks (from JSON: 1.0417s / 1.375s at 20 TPS).
    private static final int ATTACK_DURATION_TICKS  = 21;
    private static final int ATTACK2_DURATION_TICKS = 28;

    // Speeds
    private static final double SPEED_STALK     = 0.0D;   // stationary
    private static final double SPEED_CHASE     = 0.30D;  // fast, but player can sprint away
    private static final double SPEED_AGGRO     = 0.42D;  // faster than player sprint
    private static final double SPEED_CROUCH    = 0.10D;  // slow when crouching under low ceilings
    private static final double SPEED_CRAWL     = 0.18D;

    private final AnimationFactory factory = GeckoLibUtil.createFactory(this);

    // Time-state
    private int aliveTicks = 0;
    private int modeTicks  = 0;       // ticks since current mode began
    private int chaseCycles = 0;      // 0..3, when reaches 3 -> AGGRESSIVE
    private int stareTicks  = 0;      // ticks of player stare while STALKING -> builds aggression
    private int unnoticedTicks = 0;   // ticks since player noticed us
    private int attackTicks = 0;      // countdown for attack animation
    private int blockBreakCooldown = 30;
    private boolean clientChaseSoundStarted = false;

    public TheHollowEntity(EntityType<? extends TheHollowEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.xpReward = 0;
        this.maxUpStep = 1.0F;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 200.0D)
                .add(Attributes.ATTACK_DAMAGE, 100.0D)   // aggressive mode hits hard
                .add(Attributes.MOVEMENT_SPEED, SPEED_CHASE)
                .add(Attributes.FOLLOW_RANGE, 96.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_MODE, Mode.STALKING.ordinal());
        this.entityData.define(DATA_POSE, Pose.IDLE.ordinal());
        this.entityData.define(DATA_CLIMBING, false);
        this.entityData.define(DATA_GRAB_TARGET, -1);
        this.entityData.define(DATA_ATTACK_VARIANT, 0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        WallClimberNavigation nav = new WallClimberNavigation(this, level);
        nav.setCanOpenDoors(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new BreakDoorGoal(this, d -> true));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(3, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 96.0F, 1.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1,
                new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // === Accessors ============================================================

    public Mode getMode() {
        int idx = this.entityData.get(DATA_MODE);
        Mode[] vs = Mode.values();
        return vs[Math.max(0, Math.min(vs.length - 1, idx))];
    }

    public Pose getHollowPose() {
        int idx = this.entityData.get(DATA_POSE);
        Pose[] vs = Pose.values();
        return vs[Math.max(0, Math.min(vs.length - 1, idx))];
    }

    public boolean isClimbing() { return this.entityData.get(DATA_CLIMBING); }
    public int getAttackVariant() { return this.entityData.get(DATA_ATTACK_VARIANT); }
    public boolean isAttacking() { return attackTicks > 0; }
    public boolean isAggressiveMode() { return getMode() == Mode.AGGRESSIVE; }

    public void setMode(Mode m) {
        Mode old = getMode();
        this.entityData.set(DATA_MODE, m.ordinal());
        modeTicks = 0;
        unnoticedTicks = 0;
        stareTicks = 0;
        broadcastStatusOverlay(m);
        if (m != Mode.AGGRESSIVE && old == Mode.AGGRESSIVE) stopChaseThemeForNearbyPlayers();
    }

    public void setHollowPose(Pose p) { this.entityData.set(DATA_POSE, p.ordinal()); }
    public void setClimbing(boolean climbing) { this.entityData.set(DATA_CLIMBING, climbing); }

    @Override public boolean onClimbable() { return this.isClimbing(); }

    // === Tick =================================================================

    @Override
    public void tick() {
        super.tick();
        if (this.level.isClientSide) {
            if (!clientChaseSoundStarted && isAggressiveMode()) {
                clientChaseSoundStarted = true;
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                        net.minecraftforge.api.distmarker.Dist.CLIENT,
                        () -> () -> com.redstonedev.itstalks.client.sound.ClientChaseSoundStarter.start(this));
            }
            if (clientChaseSoundStarted && !isAggressiveMode()) {
                clientChaseSoundStarted = false;
            }
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level.isClientSide) return;

        aliveTicks++;
        modeTicks++;

        // Grab attack takes priority over normal AI.
        if (attackTicks > 0) {
            tickGrabAttack();
            return;
        }

        if (blockBreakCooldown > 0) blockBreakCooldown--;

        Player nearest = this.level.getNearestPlayer(this, 96.0D);

        // Update climb state
        this.setClimbing(this.horizontalCollision);

        // Crouch check - if there's a block above us within 2 blocks, we have to crouch
        boolean lowCeiling = isLowCeiling();

        // Sync target speed to mode + crouching state.
        double targetSpeed = computeTargetSpeed(lowCeiling);
        AttributeInstance attr = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null && Math.abs(attr.getBaseValue() - targetSpeed) > 1e-6) {
            attr.setBaseValue(targetSpeed);
        }

        // Sync pose for the renderer based on actual motion + state.
        updatePose(nearest, lowCeiling);

        Mode mode = getMode();
        if (mode == Mode.STALKING)   tickStalking(nearest);
        else if (mode == Mode.CHASING) tickChasing(nearest);
        else                          tickAggressive(nearest);

        // Soft block breaking - only when chasing or aggressive and blocked.
        if ((mode == Mode.CHASING || mode == Mode.AGGRESSIVE) && blockBreakCooldown <= 0
                && this.horizontalCollision) {
            trySoftBreak();
            blockBreakCooldown = 30 + this.random.nextInt(20);
        }
    }

    private double computeTargetSpeed(boolean lowCeiling) {
        Mode m = getMode();
        if (m == Mode.STALKING) return SPEED_STALK;
        if (lowCeiling) return SPEED_CROUCH;
        if (m == Mode.AGGRESSIVE) return SPEED_AGGRO;
        return SPEED_CHASE;
    }

    private void updatePose(Player nearest, boolean lowCeiling) {
        Mode m = getMode();
        if (m == Mode.STALKING) {
            // 50% idle / 50% stalking_pose - decided once per stalking instance via modeTicks parity
            if ((modeTicks / 1) == 0) {
                // pose chosen on entry; use parity of a hash for stable randomness
            }
            // Pick on entry: aliveTicks % 2 was unreliable - use a stable per-spawn pick
            // We'll just check the synced data on entry to this mode and not change it
            if (getHollowPose() != Pose.IDLE && getHollowPose() != Pose.STALKING_POSE) {
                setHollowPose(this.random.nextBoolean() ? Pose.IDLE : Pose.STALKING_POSE);
            }
            return;
        }
        if (isClimbing()) {
            setHollowPose(Pose.CLIMB);
            return;
        }
        if (lowCeiling) {
            setHollowPose(Pose.CROUCH);
            return;
        }
        // Crawling when very low ceiling (1 block) - skip for now; use run/crouch only.
        // CHASING/AGGRESSIVE -> run
        setHollowPose(Pose.RUN);
    }

    // --- STALKING -------------------------------------------------------------

    private void tickStalking(Player nearest) {
        if (nearest == null) {
            unnoticedTicks++;
            // 3 minutes (3600 ticks) of no player -> despawn
            if (unnoticedTicks >= 3600) this.discard();
            return;
        }

        // Hold the entity still
        this.getNavigation().stop();

        boolean staring = isPlayerStaringAt(nearest);

        if (staring) {
            // If player looks directly at the Hollow during stalking -> disappears
            // But: anger builds the LONGER they stare. We use a simple two-tier check:
            //   < 20 ticks of stare (1 sec): just disappears
            //   But also, while in stalking the stare BUILDS aggression toward chasing.
            // To preserve "if the player looks at it, the hollow disappears", but ALSO have
            // the "the more they look, the more aggressive it gets" mechanic, we use stareTicks
            // to accumulate. When stareTicks exceeds a threshold, transition to CHASING
            // instead of just despawning.
            stareTicks++;
            if (stareTicks >= 60) {
                // 3 seconds of staring -> transitions to CHASING (with chaseCycles bonus if angered)
                setMode(Mode.CHASING);
                return;
            }
            // Otherwise: brief glance -> disappear (despawn)
            if (stareTicks >= 5 && stareTicks <= 8) {
                // ~quarter second window where a brief look just makes it vanish
                this.discard();
                return;
            }
            unnoticedTicks = 0;
        } else {
            stareTicks = Math.max(0, stareTicks - 1);
            unnoticedTicks++;
        }

        // Lock yaw so it always faces the player
        lockYawTo(nearest);

        // Play stalking sound periodically.
        if (modeTicks % 120 == 0) {
            this.level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.STALKING.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
        }

        // 3 minutes of being ignored -> disappear but anger is "banked" (we boost chaseCycles)
        if (unnoticedTicks >= 3600) {
            chaseCycles = Math.min(2, chaseCycles + 1); // come back angrier
            this.discard();
        }
    }

    // --- CHASING --------------------------------------------------------------

    private void tickChasing(Player nearest) {
        if (nearest == null) {
            // No player around - if 1 min passes, despawn (or count chase cycle)
            if (modeTicks >= 1200) advanceFromChase();
            return;
        }

        // If player stares directly at us during chase -> teleport behind them and reset
        if (isPlayerStaringAt(nearest) && this.distanceTo(nearest) < 32.0D) {
            teleportToNearbySpot(nearest);
            return;
        }

        // Touch player -> attack animation (50/50 attack vs attack2)
        if (this.distanceTo(nearest) < 1.8D && attackTicks <= 0) {
            startAttackOnPlayer(nearest);
            return;
        }

        // 1 minute window per chase cycle - if we can't catch them, advance/despawn
        if (modeTicks >= 1200) {
            advanceFromChase();
        }
    }

    private void advanceFromChase() {
        chaseCycles++;
        if (chaseCycles >= 3) {
            setMode(Mode.AGGRESSIVE);
        } else {
            setMode(Mode.STALKING);
        }
    }

    // --- AGGRESSIVE -----------------------------------------------------------

    private void tickAggressive(Player nearest) {
        // 2 minutes (2400 ticks) of aggression, then cycle back to STALKING and reset cycles
        if (modeTicks >= 2400) {
            chaseCycles = 0;
            setMode(Mode.STALKING);
            return;
        }
        // In AGGRESSIVE, looking at it does NOT make it disappear.
        // Touch + attack handled by vanilla MeleeAttackGoal at 100 damage.
    }

    // === Attack animations ====================================================

    /** Initiates a 50/50 attack vs attack2 grab on the player. */
    private void startAttackOnPlayer(Player player) {
        boolean variant2 = this.random.nextBoolean();
        this.entityData.set(DATA_ATTACK_VARIANT, variant2 ? 1 : 0);
        this.entityData.set(DATA_GRAB_TARGET, player.getId());
        attackTicks = variant2 ? ATTACK2_DURATION_TICKS : ATTACK_DURATION_TICKS;
        this.swing(InteractionHand.MAIN_HAND);
        this.setNoAi(true);
        setHollowPose(variant2 ? Pose.ATTACK2 : Pose.ATTACK);
    }

    private void tickGrabAttack() {
        int targetId = this.entityData.get(DATA_GRAB_TARGET);
        Entity targetEnt = this.level.getEntity(targetId);
        if (!(targetEnt instanceof Player)) {
            endGrabAttack();
            return;
        }
        Player grabbed = (Player) targetEnt;

        int variant = this.entityData.get(DATA_ATTACK_VARIANT);
        int totalTicks = variant == 1 ? ATTACK2_DURATION_TICKS : ATTACK_DURATION_TICKS;
        float progress = 1.0f - (attackTicks / (float) totalTicks);

        // Forward direction of the Hollow.
        float yawRad = (float) Math.toRadians(this.getYRot());
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);

        if (variant == 0) {
            // ATTACK animation: lift player to arms first half, then smooth pull to chest, then die.
            double tx, ty, tz;
            if (progress < 0.6f) {
                // Phase 1: lift to arm height (~80% of body height), 0.8 block in front.
                double t = progress / 0.6f;
                tx = this.getX() + forwardX * 0.8;
                tz = this.getZ() + forwardZ * 0.8;
                ty = this.getY() + this.getBbHeight() * (0.45 + 0.35 * t);
            } else {
                // Phase 2: smooth pull into chest (closer in + drop slightly).
                double t = (progress - 0.6f) / 0.4f;
                double dist = 0.8 - 0.5 * t;  // 0.8 -> 0.3 (inside the chest)
                tx = this.getX() + forwardX * dist;
                tz = this.getZ() + forwardZ * dist;
                ty = this.getY() + this.getBbHeight() * (0.80 - 0.15 * t); // 80% -> 65% (chest)
            }
            if (grabbed instanceof ServerPlayer) {
                ((ServerPlayer) grabbed).connection.teleport(tx, ty, tz, this.getYRot() + 180.0F, 0.0F);
            } else {
                grabbed.setPos(tx, ty, tz);
            }
            grabbed.setDeltaMovement(0, 0, 0);
            grabbed.fallDistance = 0;
        } else {
            // ATTACK2: smooth UP, rough DOWN (5 dmg), teleport back, 5 dmg.
            // Split into 4 phases:
            //   0.00 - 0.30 : smooth up to head height
            //   0.30 - 0.50 : rough drop down -> deal 5 damage at the bottom
            //   0.50 - 0.80 : teleport back behind the Hollow
            //   0.80 - 1.00 : deal 5 more damage
            if (progress < 0.3f) {
                double t = progress / 0.3f;
                double tx = this.getX() + forwardX * 0.6;
                double tz = this.getZ() + forwardZ * 0.6;
                double ty = this.getY() + this.getBbHeight() * (0.4 + 0.5 * t);
                teleportPlayer(grabbed, tx, ty, tz, this.getYRot() + 180.0F);
            } else if (progress < 0.5f) {
                double t = (progress - 0.3f) / 0.2f;
                double tx = this.getX() + forwardX * 0.6;
                double tz = this.getZ() + forwardZ * 0.6;
                double ty = this.getY() + this.getBbHeight() * (0.9 - 0.7 * t); // rough drop
                teleportPlayer(grabbed, tx, ty, tz, this.getYRot() + 180.0F);
                if (progress >= 0.48f) {
                    grabbed.hurt(DamageSource.mobAttack(this), 5.0F);
                }
            } else if (progress < 0.8f) {
                // Slide them slightly back without hurt.
                double t = (progress - 0.5f) / 0.3f;
                double dist = 0.6 - 0.2 * t;
                double tx = this.getX() + forwardX * dist;
                double tz = this.getZ() + forwardZ * dist;
                double ty = this.getY() + this.getBbHeight() * 0.2;
                teleportPlayer(grabbed, tx, ty, tz, this.getYRot() + 180.0F);
            } else {
                if (progress >= 0.98f) {
                    grabbed.hurt(DamageSource.mobAttack(this), 5.0F);
                }
            }
        }

        attackTicks--;
        if (attackTicks <= 0) {
            // End of animation
            if (variant == 0) {
                // ATTACK = kill the player at end
                if (this.level instanceof ServerLevel) {
                    ServerLevel sl = (ServerLevel) this.level;
                    sl.sendParticles(
                            new net.minecraft.core.particles.BlockParticleOption(
                                    net.minecraft.core.particles.ParticleTypes.BLOCK,
                                    Blocks.RED_CONCRETE.defaultBlockState()),
                            grabbed.getX(),
                            grabbed.getY() + grabbed.getBbHeight() * 0.5,
                            grabbed.getZ(),
                            80, 0.4, 0.4, 0.4, 0.3);
                }
                grabbed.hurt(DamageSource.mobAttack(this), 1_000_000.0F);
            }
            endGrabAttack();
        }
    }

    private void teleportPlayer(Player p, double x, double y, double z, float yaw) {
        if (p instanceof ServerPlayer) {
            ((ServerPlayer) p).connection.teleport(x, y, z, yaw, 0.0F);
        } else {
            p.setPos(x, y, z);
        }
        p.setDeltaMovement(0, 0, 0);
        p.fallDistance = 0;
    }

    private void endGrabAttack() {
        this.entityData.set(DATA_GRAB_TARGET, -1);
        this.setNoAi(false);
        attackTicks = 0;
        // Drop attack pose; updatePose will pick a normal one next tick.
        setHollowPose(Pose.RUN);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        // In CHASING mode, melee contact triggers the grab attack. In AGGRESSIVE,
        // standard melee damage (100 per attributes) - skip grab.
        Mode m = getMode();
        if (m == Mode.AGGRESSIVE) {
            this.swing(InteractionHand.MAIN_HAND);
            return super.doHurtTarget(target);
        }
        if (m == Mode.CHASING && target instanceof Player && attackTicks <= 0) {
            startAttackOnPlayer((Player) target);
            return true;
        }
        return super.doHurtTarget(target);
    }

    // === Look detection + teleport ============================================

    private void teleportToNearbySpot(Player player) {
        // Find a spot 12-20 blocks away, ideally behind the player and out of sight.
        Vec3 behindDir = player.getViewVector(1.0F).reverse();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = (this.random.nextDouble() - 0.5) * Math.PI; // ±90° from behind
            double cs = Math.cos(angle), sn = Math.sin(angle);
            double dx = behindDir.x * cs - behindDir.z * sn;
            double dz = behindDir.x * sn + behindDir.z * cs;
            double dist = 12 + this.random.nextInt(8);
            double tx = player.getX() + dx * dist;
            double tz = player.getZ() + dz * dist;
            int ty = this.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                    (int) tx, (int) tz);
            BlockPos target = new BlockPos((int) tx, ty, (int) tz);
            if (this.level.getBlockState(target).isAir()
                    && this.level.getBlockState(target.above()).isAir()) {
                this.teleportTo(tx, ty, tz);
                return;
            }
        }
    }

    // === Helpers ==============================================================

    private void lockYawTo(Player p) {
        double dx = p.getX() - this.getX();
        double dz = p.getZ() - this.getZ();
        float yaw = (float) (Math.atan2(dz, dx) * (180D / Math.PI)) - 90.0F;
        this.setYBodyRot(yaw);
        this.setYHeadRot(yaw);
        this.yHeadRotO = yaw;
        this.yBodyRotO = yaw;
        this.yRotO     = yaw;
        this.setYRot(yaw);
    }

    private boolean isPlayerStaringAt(Player p) {
        if (this.distanceTo(p) > 48.0D) return false;
        double dx = this.getX() - p.getX();
        double dy = this.getEyeY() - p.getEyeY();
        double dz = this.getZ() - p.getZ();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.001D) return false;
        dx /= len; dy /= len; dz /= len;
        Vec3 look = p.getViewVector(1.0F);
        double dot = look.x * dx + look.y * dy + look.z * dz;
        return dot > 0.92D && p.hasLineOfSight(this);
    }

    private boolean isLowCeiling() {
        BlockPos head = this.blockPosition().above((int) Math.ceil(this.getBbHeight()));
        return !this.level.getBlockState(head).isAir();
    }

    private void broadcastStatusOverlay(Mode m) {
        if (!(this.level instanceof ServerLevel)) return;
        ServerLevel sl = (ServerLevel) this.level;
        int phase = 3; // clear default
        if (m == Mode.STALKING)   phase = 0;
        else if (m == Mode.CHASING) phase = 1;
        else if (m == Mode.AGGRESSIVE) phase = 2;
        final int finalPhase = phase;
        for (ServerPlayer sp : sl.players()) {
            if (sp.distanceToSqr(this) < 96.0D * 96.0D) {
                PacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                        new PacketHandler.StatusOverlayPacket(finalPhase));
            }
        }
    }

    private void stopChaseThemeForNearbyPlayers() {
        if (!(this.level instanceof ServerLevel)) return;
        ServerLevel sl = (ServerLevel) this.level;
        ResourceLocation sound = ModSounds.CHASE_THEME.get().getLocation();
        ClientboundStopSoundPacket pkt = new ClientboundStopSoundPacket(sound, SoundSource.HOSTILE);
        for (ServerPlayer sp : sl.players()) {
            if (sp.distanceToSqr(this) < 96.0D * 96.0D) sp.connection.send(pkt);
        }
    }

    // --- Soft block breaking --------------------------------------------------

    private void trySoftBreak() {
        BlockPos center = this.blockPosition();
        BlockPos target = null;
        scan:
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 3; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (canHollowBreak(p)) { target = p; break scan; }
                }
            }
        }
        if (target != null) {
            this.level.destroyBlock(target, true, this);
        }
    }

    /** Hollow can break: doors, trapdoors, glass, and soft blocks (dirt/grass/sand/gravel/etc -
     *  anything with destroy speed under 1.0). It CANNOT break stone/cobble/obsidian/etc. */
    private boolean canHollowBreak(BlockPos pos) {
        BlockState bs = this.level.getBlockState(pos);
        if (bs.isAir()) return false;
        float hardness = bs.getDestroySpeed(this.level, pos);
        if (hardness < 0) return false; // unbreakable
        Block b = bs.getBlock();
        // Always allow doors/trapdoors regardless of hardness.
        if (b instanceof DoorBlock || b instanceof TrapDoorBlock) return true;
        // Disallow obviously protected blocks.
        if (b == Blocks.BEDROCK || b == Blocks.BARRIER || b == Blocks.COMMAND_BLOCK
                || b == Blocks.STRUCTURE_BLOCK || b == Blocks.JIGSAW || b == Blocks.LIGHT
                || b == Blocks.END_PORTAL_FRAME || b == Blocks.END_PORTAL
                || b == Blocks.NETHER_PORTAL || b == Blocks.VOID_AIR) return false;
        // Allow soft blocks only.
        return hardness < 1.0F;
    }

    // === Animations ===========================================================

    @Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<>(this, "loco", 3, this::locoPredicate));
        data.addAnimationController(new AnimationController<>(this, "attack", 0, this::attackPredicate));
    }

    private <E extends IAnimatable> PlayState locoPredicate(AnimationEvent<E> event) {
        AnimationController<?> controller = event.getController();
        // Don't conflict with attack controller during a grab.
        if (this.entityData.get(DATA_GRAB_TARGET) != -1) {
            return PlayState.STOP;
        }
        Pose p = getHollowPose();
        if (p == Pose.CLIMB) {
            controller.setAnimation(new AnimationBuilder().loop("animation.it_stalks:the_hollow.climb"));
        } else if (p == Pose.CRAWL) {
            controller.setAnimation(new AnimationBuilder().loop("animation.it_stalks:the_hollow.crawl"));
        } else if (p == Pose.CROUCH) {
            controller.setAnimation(new AnimationBuilder().loop("animation.it_stalks:the_hollow.crouch"));
        } else if (p == Pose.RUN) {
            controller.setAnimation(new AnimationBuilder().loop("animation.it_stalks:the_hollow.run"));
        } else if (p == Pose.STALKING_POSE) {
            controller.setAnimation(new AnimationBuilder().loop("animation.it_stalks:the_hollow.stalking_pose"));
        } else {
            controller.setAnimation(new AnimationBuilder().loop("animation.it_stalks:the_hollow.idle"));
        }
        return PlayState.CONTINUE;
    }

    private <E extends IAnimatable> PlayState attackPredicate(AnimationEvent<E> event) {
        AnimationController<?> controller = event.getController();
        if (attackTicks > 0 && controller.getAnimationState() == AnimationState.Stopped) {
            controller.markNeedsReload();
            String anim = getAttackVariant() == 1
                    ? "animation.it_stalks:the_hollow.attack2"
                    : "animation.it_stalks:the_hollow.attack";
            controller.setAnimation(new AnimationBuilder()
                    .addAnimation(anim, EDefaultLoopTypes.PLAY_ONCE));
            return PlayState.CONTINUE;
        }
        if (attackTicks <= 0 && controller.getAnimationState() != AnimationState.Stopped) {
            return PlayState.STOP;
        }
        return PlayState.CONTINUE;
    }

    @Override public AnimationFactory getFactory() { return factory; }

    // === Sounds ===============================================================

    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.HURT.get(); }
    @Override protected SoundEvent getDeathSound()              { return ModSounds.DEATH.get(); }
    @Override protected float getSoundVolume()                  { return 1.0F; }

    // === Damage / removal =====================================================

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);
        if (result && source.getEntity() instanceof LivingEntity) {
            // Getting hit forces transition to CHASING (if not already at aggressive)
            if (getMode() == Mode.STALKING) setMode(Mode.CHASING);
        }
        return result;
    }

    @Override
    public void remove(RemovalReason reason) {
        stopChaseThemeForNearbyPlayers();
        // Clear overlay
        if (this.level instanceof ServerLevel) {
            ServerLevel sl = (ServerLevel) this.level;
            for (ServerPlayer sp : sl.players()) {
                if (sp.distanceToSqr(this) < 96.0D * 96.0D) {
                    PacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                            new PacketHandler.StatusOverlayPacket(3));
                }
            }
        }
        super.remove(reason);
    }

    // === NBT ==================================================================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Mode",         this.entityData.get(DATA_MODE));
        tag.putInt("Pose",         this.entityData.get(DATA_POSE));
        tag.putInt("AliveTicks",   aliveTicks);
        tag.putInt("ModeTicks",    modeTicks);
        tag.putInt("ChaseCycles",  chaseCycles);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_MODE, tag.getInt("Mode"));
        this.entityData.set(DATA_POSE, tag.getInt("Pose"));
        aliveTicks  = tag.getInt("AliveTicks");
        modeTicks   = tag.getInt("ModeTicks");
        chaseCycles = tag.getInt("ChaseCycles");
    }
}
