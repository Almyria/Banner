package com.mohistmc.banner.mixin.server.level;

import com.mohistmc.banner.bukkit.BukkitSnapshotCaptures;
import com.mohistmc.banner.bukkit.DoubleChestInventory;
import com.mohistmc.banner.injection.server.level.InjectionServerPlayer;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.BlockUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.PositionImpl;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.PlayerRespawnLogic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.stats.RecipeBook;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.WeatherType;
import org.bukkit.craftbukkit.v1_20_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R1.CraftWorldBorder;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_20_R1.event.CraftEventFactory;
import org.bukkit.craftbukkit.v1_20_R1.event.CraftPortalEvent;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_20_R1.util.BlockStateListPopulator;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftChatMessage;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftLocation;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedMainHandEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerSpawnChangeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.MainHand;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer extends Player implements InjectionServerPlayer {

    @Shadow public int lastSentExp;

    @Shadow protected abstract boolean bedInRange(BlockPos pos, Direction direction);

    @Shadow protected abstract boolean bedBlocked(BlockPos pos, Direction direction);
    @Shadow protected abstract int getCoprime(int i);

    @Shadow @Final public MinecraftServer server;
    @Shadow @Final public ServerPlayerGameMode gameMode;
    @Shadow private ResourceKey<Level> respawnDimension;

    @Shadow @Nullable public abstract BlockPos getRespawnPosition();

    @Shadow public abstract float getRespawnAngle();

    @Shadow public abstract void setServerLevel(ServerLevel serverLevel);

    @Shadow public abstract ServerLevel serverLevel();

    @Shadow @Nullable private Entity camera;
    @Shadow private int containerCounter;
    @Shadow public ServerGamePacketListenerImpl connection;

    @Shadow public abstract void initMenu(AbstractContainerMenu abstractContainerMenu);

    @Shadow public abstract boolean teleportTo(ServerLevel level, double x, double y, double z, Set<RelativeMovement> relativeMovements, float yRot, float xRot);

    @Shadow public abstract void teleportTo(ServerLevel newLevel, double x, double y, double z, float yaw, float pitch);

    @Shadow @Nullable private BlockPos respawnPosition;
    @Shadow private float respawnAngle;
    @Shadow private boolean respawnForced;

    @Shadow public abstract void setCamera(@Nullable Entity entityToSpectate);

    @Shadow @Nullable protected abstract PortalInfo findDimensionEntryPoint(ServerLevel destination);

    @Shadow public abstract void resetFallDistance();

    @Shadow public abstract boolean canHarmPlayer(Player other);
    @Shadow public abstract void nextContainerCounter();

    // CraftBukkit start
    @Unique
    public String displayName;
    @Unique
    public Component listName;
    @Unique
    public org.bukkit.Location compassTarget;
    @Unique
    public int newExp = 0;
    @Unique
    public int newLevel = 0;
    @Unique
    public int newTotalExp = 0;
    @Unique
    public boolean keepLevel = false;
    @Unique
    public double maxHealthCache;
    @Unique
    public boolean joining = true;
    @Unique
    public boolean sentListPacket = false;
    @Unique
    public Integer clientViewDistance;
    @Unique
    public String kickLeaveMessage = null; // SPIGOT-3034: Forward leave message to PlayerQuitEvent
    // CraftBukkit end

    @Unique
    public long timeOffset = 0;
    @Unique
    public WeatherType weather = null;
    @Unique
    public boolean relativeTime = true;
    @Unique
    public String locale = null; // CraftBukkit - add, lowercase // Paper - default to null
    @Unique
    private boolean banner$initialized = false;
    @Unique
    private float pluginRainPosition;
    @Unique
    private float pluginRainPositionPrevious;

    public MixinServerPlayer(Level level, BlockPos blockPos, float f, GameProfile gameProfile) {
        super(level, blockPos, f, gameProfile);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    public void banner$init(CallbackInfo ci) {
        this.displayName = getScoreboardName();
        this.banner$setBukkitPickUpLoot(true);
        this.maxHealthCache = this.getMaxHealth();
        this.banner$initialized = true;
    }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void banner$readExtra(CompoundTag compound, CallbackInfo ci) {
        this.getBukkitEntity().readExtraData(compound);
        String spawnWorld = compound.getString("SpawnWorld");
        CraftWorld oldWorld = (CraftWorld) Bukkit.getWorld(spawnWorld);
        if (oldWorld != null) {
            this.respawnDimension = oldWorld.getHandle().dimension();
        }
    }

    @Redirect(method = "addAdditionalSaveData", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hasExactlyOnePlayerPassenger()Z"))
    private boolean banner$nonPersistVehicle(Entity entity) {
        Entity entity1 = this.getVehicle();
        boolean persistVehicle = true;
        if (entity1 != null) {
            Entity vehicle;
            for (vehicle = entity1; vehicle != null; vehicle = vehicle.getVehicle()) {
                if (!vehicle.bridge$persist()) {
                    persistVehicle = false;
                    break;
                }
            }
        }
        return persistVehicle && entity.hasExactlyOnePlayerPassenger();
    }


    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void banner$writeExtra(CompoundTag compound, CallbackInfo ci) {
        this.getBukkitEntity().setExtraData(compound);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void banner$joining(CallbackInfo ci) {
        if (this.joining) {
            this.joining = false;
        }
    }

    @Redirect(method = "doTick", at = @At(value = "NEW", args = "class=net/minecraft/network/protocol/game/ClientboundSetHealthPacket"))
    private ClientboundSetHealthPacket banner$useScaledHealth(float healthIn, int foodLevelIn, float saturationLevelIn) {
        return new ClientboundSetHealthPacket(this.getBukkitEntity().getScaledHealth(), foodLevelIn, saturationLevelIn);
    }

    @Inject(method = "doTick", at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/ServerPlayer;tickCount:I"))
    private void banner$updateHealthAndExp(CallbackInfo ci) {
        if (this.maxHealthCache != this.getMaxHealth()) {
            this.getBukkitEntity().updateScaledHealth();
        }
        if (this.bridge$oldLevel() == -1) {
            this.banner$setOldLevel(this.experienceLevel);
        }
        if (this.bridge$oldLevel() != this.experienceLevel) {
            CraftEventFactory.callPlayerLevelChangeEvent(this.getBukkitEntity(), this.bridge$oldLevel(), this.experienceLevel);
            this.banner$setOldLevel(this.experienceLevel);
        }
        if (this.getBukkitEntity().hasClientWorldBorder()) {
            ((CraftWorldBorder) this.getBukkitEntity().getWorldBorder()).getHandle().tick();
        }
    }


    @Redirect(method = "awardKillScore", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/scores/Scoreboard;forAllObjectives(Lnet/minecraft/world/scores/criteria/ObjectiveCriteria;Ljava/lang/String;Ljava/util/function/Consumer;)V"))
    private void banner$useCustomScoreboard(Scoreboard instance, ObjectiveCriteria criteria, String scoreboardName, Consumer<Score> points) {
        this.level().getCraftServer().getScoreboardManager().getScoreboardScores(criteria, scoreboardName, points);
    }

    @Redirect(method = "handleTeamKill", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/scores/Scoreboard;forAllObjectives(Lnet/minecraft/world/scores/criteria/ObjectiveCriteria;Ljava/lang/String;Ljava/util/function/Consumer;)V"))
    private void banner$teamKill(Scoreboard instance, ObjectiveCriteria criteria, String scoreboardName, Consumer<Score> points) {
        this.level().getCraftServer().getScoreboardManager().getScoreboardScores(criteria, scoreboardName, points);
    }

    @Inject(method = "isPvpAllowed", cancellable = true, at = @At("HEAD"))
    private void banner$pvpMode(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue((this.level().bridge$pvpMode()));
    }

    @Override
    public void spawnIn(Level world) {
        this.setLevel(world);
        if (world == null) {
            this.unsetRemoved();
            Vec3 position = null;
            if (this.respawnDimension != null) {
                world = this.server.getLevel(this.respawnDimension);
                if (world != null && this.getRespawnPosition() != null) {
                    position = ServerPlayer.findRespawnPositionAndUseSpawnBlock((ServerLevel) world, this.getRespawnPosition(), this.getRespawnAngle(), false, false).orElse(null);
                }
            }
            if (world == null || position == null) {
                world = ((CraftWorld) Bukkit.getServer().getWorlds().get(0)).getHandle();
                position = Vec3.atCenterOf(world.getSharedSpawnPos());
            }
            this.setLevel(world);
            this.setPos(position);
        }
        this.gameMode.setLevel((ServerLevel) world);
    }

    @Override
    public void resetPlayerWeather() {
        this.weather = null;
        this.setPlayerWeather(this.level().getLevelData().isRaining() ? WeatherType.DOWNFALL : WeatherType.CLEAR, false);
    }

    @Override
    public void updateWeather(float oldRain, float newRain, float oldThunder, float newThunder) {
        if (this.weather == null) {
            // Vanilla
            if (oldRain != newRain) {
                this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, newRain));
            }
        } else {
            // Plugin
            if (this.pluginRainPositionPrevious != this.pluginRainPosition) {
                this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, this.pluginRainPosition));
            }
        }
        if (oldThunder != newThunder) {
            if (this.weather == WeatherType.DOWNFALL || this.weather == null) {
                this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, newThunder));
            } else {
                this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, 0.0f));
            }
        }
    }

    @Override
    public void tickWeather() {
        if (this.weather == null) {
            return;
        }
        this.pluginRainPositionPrevious = this.pluginRainPosition;
        if (this.weather == WeatherType.DOWNFALL) {
            this.pluginRainPosition += (float) 0.01;
        } else {
            this.pluginRainPosition -= (float) 0.01;
        }
        this.pluginRainPosition = Mth.clamp(this.pluginRainPosition, 0.0f, 1.0f);
    }

    @Override
    public void forceSetPositionRotation(double x, double y, double z, float yaw, float pitch) {
        this.moveTo(x, y, z, yaw, pitch);
        this.connection.resetPosition();
    }

    @Unique
    private transient PlayerSpawnChangeEvent.Cause banner$spawnChangeCause;

    @Override
    public void pushChangeSpawnCause(PlayerSpawnChangeEvent.Cause cause) {
        this.banner$spawnChangeCause = cause;
    }

    @Override
    public void setRespawnPosition(ResourceKey<Level> level, @Nullable BlockPos pos, float pitch, boolean flag, boolean flag1, PlayerSpawnChangeEvent.Cause cause) {
        banner$spawnChangeCause = cause;
        this.setRespawnPosition(level, pos, pitch, flag, flag1);
    }

    /**
     * @author wdog5
     * @reason bukkit
     */
    @Overwrite
    public void setRespawnPosition(ResourceKey<Level> resourceKey, @Nullable BlockPos blockPos, float x, boolean y, boolean z) {
        var cause = banner$spawnChangeCause == null ? PlayerSpawnChangeEvent.Cause.UNKNOWN : banner$spawnChangeCause;
        banner$spawnChangeCause = null;
        var newWorld = this.server.getLevel(blockPos == null ? Level.OVERWORLD : resourceKey);
        Location newSpawn = (blockPos != null) ? CraftLocation.toBukkit(blockPos, newWorld.getWorld(), x, 0) : null;

        PlayerSpawnChangeEvent event = new PlayerSpawnChangeEvent(this.getBukkitEntity(), newSpawn, y, cause);
        Bukkit.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        newSpawn = event.getNewSpawn();
        y = event.isForced();

        if (newSpawn != null) {
            resourceKey = ((CraftWorld) newSpawn.getWorld()).getHandle().dimension();
            blockPos = BlockPos.containing(newSpawn.getX(), newSpawn.getY(), newSpawn.getZ());
            x = newSpawn.getYaw();
        } else {
            resourceKey = Level.OVERWORLD;
            blockPos = null;
            x = 0.0F;
        }

        if (blockPos != null) {
            boolean flag = blockPos.equals(this.respawnPosition) && resourceKey.equals(this.respawnDimension);
            if (z && !flag) {
                this.sendSystemMessage(Component.translatable("block.minecraft.set_spawn"));
            }

            this.respawnPosition = blockPos;
            this.respawnDimension = resourceKey;
            this.respawnAngle = x;
            this.respawnForced = y;
        } else {
            this.respawnPosition = null;
            this.respawnDimension = Level.OVERWORLD;
            this.respawnAngle = 0.0F;
            this.respawnForced = false;
        }

    }

    @Inject(method = "sendSystemMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("HEAD"), cancellable = true)
    public void banner$sendSystemMessage(CallbackInfo ci) {
        if (this.connection == null) {
            ci.cancel();
        }
    }

    @Override
    public void setPlayerWeather(WeatherType type, boolean plugin) {
        if (!plugin && this.weather != null) {
            return;
        }
        if (plugin) {
            this.weather = type;
        }
        if (type == WeatherType.DOWNFALL) {
            this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0.0f));
        } else {
            this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0.0f));
        }
    }

    @Override
    public BlockPos getSpawnPoint(ServerLevel worldserver) {
        BlockPos blockposition = worldserver.getSharedSpawnPos();

        if (worldserver.dimensionType().hasSkyLight() && worldserver.serverLevelData.getGameType() != GameType.ADVENTURE) {
            int i = Math.max(0, this.server.getSpawnRadius(worldserver));
            int j = Mth.floor(worldserver.getWorldBorder().getDistanceToBorder((double) blockposition.getX(), (double) blockposition.getZ()));

            if (j < i) {
                i = j;
            }

            if (j <= 1) {
                i = 1;
            }

            long k = (long) (i * 2 + 1);
            long l = k * k;
            int i1 = l > 2147483647L ? Integer.MAX_VALUE : (int) l;
            int j1 = this.getCoprime(i1);
            int k1 = RandomSource.create().nextInt(i1);

            for (int l1 = 0; l1 < i1; ++l1) {
                int i2 = (k1 + j1 * l1) % i1;
                int j2 = i2 % (i * 2 + 1);
                int k2 = i2 / (i * 2 + 1);
                BlockPos blockposition1 = PlayerRespawnLogic.getOverworldRespawnPos(worldserver, blockposition.getX() + j2 - i, blockposition.getZ() + k2 - i);

                if (blockposition1 != null) {
                    return blockposition1;
                }
            }
        }

        return blockposition;
    }

    @Override
    public Either<BedSleepingProblem, Unit> getBedResult(BlockPos blockPos, Direction enumdirection) {
        boolean force = bridge$startSleepInBed_force().getAndSet(false);
        Either<Player.BedSleepingProblem, Unit> bedResult = null;
        Direction direction = this.level().getBlockState(blockPos).getValue(HorizontalDirectionalBlock.FACING);
        if (!this.isSleeping() && this.isAlive()) {
            if (!this.level().dimensionType().natural()) {
                bedResult = Either.left(Player.BedSleepingProblem.NOT_POSSIBLE_HERE);
            } else if (!this.bedInRange(blockPos, direction)) {
                bedResult = Either.left(Player.BedSleepingProblem.TOO_FAR_AWAY);
            } else if (this.bedBlocked(blockPos, direction)) {
                bedResult = Either.left(Player.BedSleepingProblem.OBSTRUCTED);
            } else {
                this.setRespawnPosition(this.level().dimension(), blockPos, this.getYRot(), false, true);
                if (this.level().isDay()) {
                    bedResult = Either.left(Player.BedSleepingProblem.NOT_POSSIBLE_NOW);
                } else {
                    if (!this.isCreative()) {
                        double d = 8.0;
                        double e = 5.0;
                        Vec3 vec3 = Vec3.atBottomCenterOf(blockPos);
                        List<Monster> list = this.level().getEntitiesOfClass(Monster.class, new AABB(vec3.x() - 8.0, vec3.y() - 5.0, vec3.z() - 8.0, vec3.x() + 8.0, vec3.y() + 5.0, vec3.z() + 8.0), (monster) -> {
                            return monster.isPreventingPlayerRest(this);
                        });
                        if (!list.isEmpty()) {
                            return Either.left(BedSleepingProblem.NOT_SAFE);
                        }
                    }

                    if (bedResult == null) {
                        bedResult = Either.right(Unit.INSTANCE);
                    }
                }
            }
        } else {
            bedResult = Either.left(Player.BedSleepingProblem.OTHER_PROBLEM);
        }

        if (bedResult.left().orElse(null) == Player.BedSleepingProblem.OTHER_PROBLEM) {
            return bedResult; // return immediately if the result is not bypassable by plugins
        }

        if (force) {
            bedResult = Either.right(Unit.INSTANCE);
        }

        bedResult = CraftEventFactory.callPlayerBedEnterEvent(this, blockPos, bedResult);

        if (bedResult.left().isPresent()) {
            return bedResult;
        }

        Either<Player.BedSleepingProblem, Unit> either = super.startSleepInBed(blockPos).ifRight((unit) -> {
            this.awardStat(Stats.SLEEP_IN_BED);
            CriteriaTriggers.SLEPT_IN_BED.trigger(((ServerPlayer) (Object) this));
        });
        if (!this.serverLevel().canSleepThroughNights()) {
            this.displayClientMessage(Component.translatable("sleep.not_possible"), true);
        }

        ((ServerLevel) this.level()).updateSleepingPlayerList();
        return either;
    }

    @Override
    public Scoreboard getScoreboard() {
        return this.getBukkitEntity().getScoreboard().getHandle();
    }

    @Override
    public void reset() {
        float exp = 0.0f;
        if (this.keepLevel) {
            exp = this.experienceProgress;
            this.newTotalExp = this.totalExperience;
            this.newLevel = this.experienceLevel;
        }
        this.setHealth(this.getMaxHealth());
        this.stopUsingItem();
        this.setRemainingFireTicks(0);
        this.resetFallDistance();
        this.foodData = new FoodData();
        this.foodData.setEntityhuman((ServerPlayer) (Object) this);
        this.experienceLevel = this.newLevel;
        this.totalExperience = this.newTotalExp;
        this.experienceProgress = 0.0f;
        this.deathTime = 0;
        this.setArrowCount(0, true);
        this.removeAllEffects(EntityPotionEffectEvent.Cause.DEATH);
        this.effectsDirty = true;
        this.containerMenu = this.inventoryMenu;
        this.lastHurtByPlayer = null;
        this.lastHurtByMob = null;
        this.combatTracker = new CombatTracker((ServerPlayer) (Object) this);
        this.lastSentExp = -1;
        if (this.keepLevel) {
            this.experienceProgress = exp;
        } else {
            this.giveExperiencePoints(this.newExp);
        }
        this.keepLevel = false;
        this.setDeltaMovement(0, 0, 0);
    }

    @Inject(method = "doCloseContainer", at = @At("HEAD"))
    private void banner$invClose(CallbackInfo ci) {
        if (this.containerMenu != this.inventoryMenu) {
            var old = BukkitSnapshotCaptures.getContainerOwner();
            BukkitSnapshotCaptures.captureContainerOwner((ServerPlayer) (Object) this);
            CraftEventFactory.handleInventoryCloseEvent((ServerPlayer) (Object) this);
            BukkitSnapshotCaptures.captureContainerOwner(old);
        }
    }

    @Inject(method = "setPlayerInput", cancellable = true, at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;setShiftKeyDown(Z)V"))
    private void banner$toggleSneak(float strafe, float forward, boolean jumping, boolean sneaking, CallbackInfo ci) {
        if (sneaking != this.isShiftKeyDown()) {
            PlayerToggleSneakEvent event = new PlayerToggleSneakEvent(this.getBukkitEntity(), sneaking);
            Bukkit.getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                ci.cancel();
            }
        }
    }

    @Redirect(method = "restoreFrom", at = @At(value = "INVOKE", target = "Lnet/minecraft/stats/ServerRecipeBook;copyOverData(Lnet/minecraft/stats/RecipeBook;)V"))
    private void banner$copyOverData(ServerRecipeBook instance, RecipeBook recipeBook) {}

    @Redirect(method = "awardStat", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/scores/Scoreboard;forAllObjectives(Lnet/minecraft/world/scores/criteria/ObjectiveCriteria;Ljava/lang/String;Ljava/util/function/Consumer;)V"))
    private void banner$addStats(Scoreboard instance, ObjectiveCriteria criteria, String scoreboardName, Consumer<Score> points) {
        this.level().getCraftServer().getScoreboardManager().getScoreboardScores(criteria, scoreboardName, points);
    }

    @Redirect(method = "resetStat", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/scores/Scoreboard;forAllObjectives(Lnet/minecraft/world/scores/criteria/ObjectiveCriteria;Ljava/lang/String;Ljava/util/function/Consumer;)V"))
    private void banner$takeStats(Scoreboard instance, ObjectiveCriteria criteria, String scoreboardName, Consumer<Score> points) {
        this.level().getCraftServer().getScoreboardManager().getScoreboardScores(criteria, scoreboardName, points);
    }

    @Redirect(method = "updateScoreForCriteria", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/scores/Scoreboard;forAllObjectives(Lnet/minecraft/world/scores/criteria/ObjectiveCriteria;Ljava/lang/String;Ljava/util/function/Consumer;)V"))
    private void banner$updateStats(Scoreboard instance, ObjectiveCriteria criteria, String scoreboardName, Consumer<Score> action) {
        // CraftBukkit - Use our scores instead
        this.level().getCraftServer().getScoreboardManager().getScoreboardScores(criteria, this.getScoreboardName(), action);
    }

    @Inject(method = "resetSentInfo", at = @At("HEAD"))
    private void banner$setExpUpdate(CallbackInfo ci) {
        this.lastSentExp = -1;
    }

    @Inject(method = "updateOptions", at = @At("HEAD"))
    private void banner$settingChange(ServerboundClientInformationPacket packetIn, CallbackInfo ci) {
        if (this.getMainArm() != packetIn.mainHand()) {
            PlayerChangedMainHandEvent event = new PlayerChangedMainHandEvent(this.getBukkitEntity(), (this.getMainArm() == HumanoidArm.LEFT) ? MainHand.LEFT : MainHand.RIGHT);
            Bukkit.getPluginManager().callEvent(event);
        }
        if (this.locale == null || !this.locale.equals(packetIn.language)) { // Paper - check for null
            PlayerLocaleChangeEvent event2 = new PlayerLocaleChangeEvent(this.getBukkitEntity(), packetIn.language());
            Bukkit.getPluginManager().callEvent(event2);
            this.server.bridge$server().getPluginManager().callEvent(new com.destroystokyo.paper.event.player.PlayerLocaleChangeEvent(this.getBukkitEntity(), this.locale, packetIn.language)); // Paper
        }
        this.locale = packetIn.language();
        this.clientViewDistance = packetIn.viewDistance();
    }

    @Inject(method = "trackChunk",
            at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V",
            shift = At.Shift.AFTER))
    private void banner$chunkLoad(ChunkPos chunkPos, Packet<?> packet, CallbackInfo ci) {
        // Paper start
        if(io.papermc.paper.event.packet.PlayerChunkLoadEvent.getHandlerList().getRegisteredListeners().length > 0){
            new io.papermc.paper.event.packet.PlayerChunkLoadEvent(this.getBukkitEntity().getWorld().getChunkAt(chunkPos.toLong()), this.getBukkitEntity()).callEvent();
        }
        // Paper end
    }

    @Inject(method = "untrackChunk",
            at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V",
            shift = At.Shift.AFTER))
    private void banner$chunkUnload(ChunkPos chunkPos, CallbackInfo ci) {
        // Paper start
        if(io.papermc.paper.event.packet.PlayerChunkUnloadEvent.getHandlerList().getRegisteredListeners().length > 0){
            new io.papermc.paper.event.packet.PlayerChunkUnloadEvent(this.getBukkitEntity().getWorld().getChunkAt(chunkPos.toLong()), this.getBukkitEntity()).callEvent();
        }
        // Paper end
    }

    @Inject(method = "setCamera",
            at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;level()Lnet/minecraft/world/level/Level;"),
            locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true)
    private void banner$spectorEvent(Entity entityToSpectate, CallbackInfo ci, Entity entity) {
        // Paper start - Add PlayerStartSpectatingEntityEvent and PlayerStopSpectatingEntity Event
        if (this.camera == this) {
            com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent playerStopSpectatingEntityEvent = new com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent(this.getBukkitEntity(), entity.getBukkitEntity());
            if (!playerStopSpectatingEntityEvent.callEvent()) {
                ci.cancel();
            }
        } else {
            com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent playerStartSpectatingEntityEvent = new com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent(this.getBukkitEntity(), entity.getBukkitEntity(), entity.getBukkitEntity());
            if (!playerStartSpectatingEntityEvent.callEvent()) {
                ci.cancel();
            }
        }
        // Paper end
    }

    public PortalInfo banner$findDimensionEntryPoint(ServerLevel destination) {
        return findDimensionEntryPoint(destination);
    }
    @Override
    public CraftPlayer getBukkitEntity() {
        return (CraftPlayer)super.getBukkitEntity();
    }

    @Override
    public boolean isImmobile() {
        return super.isImmobile() || !getBukkitEntity().isOnline();
    }

    @Override
    public String toString() {
        return super.toString() + "(" + this.getScoreboardName() + " at " + this.getX() + "," + this.getY() + "," + this.getZ() + ")";
    }

    @Override
    public int nextContainerCounterInt() {
        this.containerCounter = this.containerCounter % 100 + 1;
        return containerCounter; // CraftBukkit
    }

    private boolean banner$isFakeClose = false;

    /**
     * @author
     * @reason
     */
    @Overwrite
    public OptionalInt openMenu(@Nullable MenuProvider menu) {
        if (menu == null) {
            return OptionalInt.empty();
        } else {

            if (this.containerMenu != this.inventoryMenu) {
                banner$isFakeClose = false;
                this.closeContainer();
            }

            this.nextContainerCounter();
            AbstractContainerMenu abstractContainerMenu = menu.createMenu(this.containerCounter, this.getInventory(), this);

            if (abstractContainerMenu != null) {
                abstractContainerMenu.setTitle(menu.getDisplayName());
                boolean cancelled = false;
                BukkitSnapshotCaptures.captureContainerOwner((ServerPlayer) (Object) this);
                abstractContainerMenu = CraftEventFactory.callInventoryOpenEvent((ServerPlayer) (Object) this, abstractContainerMenu, cancelled);
                if (abstractContainerMenu == null && !cancelled) {
                    if (menu instanceof Container) {
                        ((Container) menu).stopOpen((ServerPlayer) (Object) this);
                    } else if (menu instanceof DoubleChestInventory) {
                        ((DoubleChestInventory) menu).inventorylargechest.stopOpen(this);
                    }
                    return OptionalInt.empty();
                }
            }
            if (abstractContainerMenu == null) {
                if (this.isSpectator()) {
                    this.displayClientMessage(Component.translatable("container.spectatorCantOpen").withStyle(ChatFormatting.RED), true);
                }

                return OptionalInt.empty();
            } else {
                this.containerMenu = abstractContainerMenu; // CraftBukkit
                this.connection.send(new ClientboundOpenScreenPacket(abstractContainerMenu.containerId, abstractContainerMenu.getType(), menu.getDisplayName()));
                this.initMenu(abstractContainerMenu);
                return OptionalInt.of(this.containerCounter);
            }
        }
    }

    @Unique
    private AtomicReference<HorseInventoryMenu> banner$horseMenu = new AtomicReference<>();

    @Inject(method = "openHorseInventory", at = @At("HEAD"), cancellable = true)
    private void banner$menuEvent(AbstractHorse abstractHorse, Container container, CallbackInfo ci) {
        // CraftBukkit start - Inventory open hook
        this.nextContainerCounterInt();
        AbstractContainerMenu banner$container = new HorseInventoryMenu(this.containerCounter, this.getInventory(), container, abstractHorse);
        banner$horseMenu.set((HorseInventoryMenu) banner$container);
        banner$container.setTitle(abstractHorse.getDisplayName());
        banner$container = CraftEventFactory.callInventoryOpenEvent(((ServerPlayer) (Object) this), banner$container);
        if (banner$container == null) {
            container.stopOpen(this);
            ci.cancel();
        }
    }

    @Redirect(method = "openHorseInventory", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;nextContainerCounter()V"))
    private void banner$cancelNext(ServerPlayer instance) {}

    @Redirect(method = "openHorseInventory", at = @At(value = "NEW", args = "class=net/minecraft/world/inventory/HorseInventoryMenu"))
    private HorseInventoryMenu banner$resetHorseMenu(int i, Inventory inventory, Container container, AbstractHorse abstractHorse) {
        return banner$horseMenu.get();
    }

    @Inject(method = "closeContainer", at = @At("HEAD"))
    private void banner$closeMenu(CallbackInfo ci) {
        if (banner$isFakeClose) {
            ci.cancel();
        }
    }

    @Unique
    private AtomicReference<String> banner$deathString = new AtomicReference<>("null");
    @Unique
    private AtomicReference<String> banner$deathMsg = new AtomicReference<>("null");

    @Unique
    private AtomicReference<PlayerDeathEvent> banner$deathEvent = new AtomicReference<>();

    @Inject(method = "die", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z",
            ordinal = 0),
            cancellable = true)
    private void banner$deathEvent(DamageSource damageSource, CallbackInfo ci) {
        // CraftBukkit start - fire PlayerDeathEvent
        if (this.isRemoved()) {
            ci.cancel();
        }
        java.util.List<org.bukkit.inventory.ItemStack> loot = new java.util.ArrayList<>(this.getInventory().getContainerSize());
        boolean keepInventory = this.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY) || this.isSpectator();

        if (!keepInventory) {
            for (ItemStack item : this.getInventory().getContents()) {
                if (!item.isEmpty() && !EnchantmentHelper.hasVanishingCurse(item)) {
                    loot.add(CraftItemStack.asCraftMirror(item));
                }
            }
        }
        // SPIGOT-5071: manually add player loot tables (SPIGOT-5195 - ignores keepInventory rule)
        this.dropFromLootTable(damageSource, this.lastHurtByPlayerTime > 0);
        for (org.bukkit.inventory.ItemStack item : this.bridge$drops()) {
            loot.add(item);
        }
        this.bridge$drops().clear(); // SPIGOT-5188: make sure to clear

        Component defaultMessage = this.getCombatTracker().getDeathMessage();
        String deathmessage = defaultMessage.getString();
        banner$deathMsg.set(deathmessage);
        keepLevel = keepInventory; // SPIGOT-2222: pre-set keepLevel
        org.bukkit.event.entity.PlayerDeathEvent event = CraftEventFactory.callPlayerDeathEvent(((ServerPlayer) (Object) this), loot, deathmessage, keepInventory);
        banner$deathEvent.set(event);

        // SPIGOT-943 - only call if they have an inventory open
        if (this.containerMenu != this.inventoryMenu) {
            this.closeContainer();
        }

        String deathMessage = event.getDeathMessage();
        banner$deathString.set(deathMessage);
    }

    @Inject(method = "die", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/damagesource/CombatTracker;getDeathMessage()Lnet/minecraft/network/chat/Component;"),
            cancellable = true)
    private void banner$checkDead(DamageSource damageSource, CallbackInfo ci) {
        boolean banner$flag = banner$deathString.get() != null && !banner$deathString.get().isEmpty();
        if (!banner$flag) { // TODO: allow plugins to override?
            ci.cancel();
        }
    }

    @Redirect(method = "die", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/damagesource/CombatTracker;getDeathMessage()Lnet/minecraft/network/chat/Component;"))
    private Component banner$restDeathMsg(CombatTracker instance) {
        Component banner$component;
        if (banner$deathString.get().equals(banner$deathMsg.get())) {
            banner$component = instance.getDeathMessage();
        } else {
            banner$component = CraftChatMessage.fromStringOrNull(banner$deathString.get());
        }
        return banner$component;
    }

    @Inject(method = "die", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;isSpectator()Z"))
    private void banner$checkEventDrop(DamageSource damageSource, CallbackInfo ci) {
        // SPIGOT-5478 must be called manually now
        this.dropExperience();
        // we clean the player's inventory after the EntityDeathEvent is called so plugins can get the exact state of the inventory.
        if (!banner$deathEvent.get().getKeepInventory()) {
            this.getInventory().clearContent();
        }
    }

    @Redirect(method = "die",
            at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;dropAllDeathLoot(Lnet/minecraft/world/damagesource/DamageSource;)V"))
    private void banner$cancelDrop(ServerPlayer instance, DamageSource damageSource) {
    }

    @Redirect(method = "die", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/scores/Scoreboard;forAllObjectives(Lnet/minecraft/world/scores/criteria/ObjectiveCriteria;Ljava/lang/String;Ljava/util/function/Consumer;)V"))
    private void banner$useBukkitScore(Scoreboard instance, ObjectiveCriteria criteria, String scoreboardName, Consumer<Score> action) {
        this.setCamera(((ServerPlayer) (Object) this)); // Remove spectated target
        // CraftBukkit end
        // CraftBukkit - Get our scores instead
        this.level().getCraftServer().getScoreboardManager().getScoreboardScores(ObjectiveCriteria.DEATH_COUNT, this.getScoreboardName(), Score::increment);
    }

    @Unique
    private AtomicReference<PlayerTeleportEvent.TeleportCause> banner$changeDimensionCause = new AtomicReference<>(PlayerTeleportEvent.TeleportCause.UNKNOWN);

    @Override
    public Entity changeDimension(ServerLevel worldserver, PlayerTeleportEvent.TeleportCause cause) {
        banner$changeDimensionCause.set(cause);
        return changeDimension(worldserver);
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;teleport(DDDFFLjava/util/Set;)V"))
    private void banner$forwardReason(ServerLevel level, double x, double y, double z, Set<RelativeMovement> relativeMovements, float yRot, float xRot, CallbackInfoReturnable<Boolean> cir) {
        this.connection.pushTeleportCause(banner$changeDimensionCause.getAndSet(PlayerTeleportEvent.TeleportCause.UNKNOWN));
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V", cancellable = true, at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lnet/minecraft/server/level/ServerPlayer;stopRiding()V"))
    private void banner$handleBy(ServerLevel world, double x, double y, double z, float yaw, float pitch, CallbackInfo ci) {
        if (banner$changeDimensionCause.get() != PlayerTeleportEvent.TeleportCause.UNKNOWN) {
            this.getBukkitEntity().teleport(new Location(world.getWorld(), x, y, z, yaw, pitch), banner$changeDimensionCause.getAndSet(PlayerTeleportEvent.TeleportCause.UNKNOWN));
            ci.cancel();
        }

    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lnet/minecraft/server/players/PlayerList;sendAllPlayerInfo(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private void banner$tp(ServerLevel world, double x, double y, double z, float yaw, float pitch, CallbackInfo ci) {
        this.onUpdateAbilities();
        for (MobEffectInstance mobEffect : this.getActiveEffects()) {
            this.connection.send(new ClientboundUpdateMobEffectPacket(this.getId(), mobEffect));
        }

        // Don't fire on respawn
        PlayerChangedWorldEvent event = new PlayerChangedWorldEvent(this.getBukkitEntity(), world.getWorld());
        Bukkit.getPluginManager().callEvent(event);

    }

    @Override
    public void teleportTo(ServerLevel worldserver, double d0, double d1, double d2, float f, float f1, PlayerTeleportEvent.TeleportCause cause) {
        pushChangeDimensionCause(cause);
        teleportTo(worldserver, d0, d1, d2, f, f1);
    }

    @Override
    public boolean teleportTo(ServerLevel worldserver, double d0, double d1, double d2, Set<RelativeMovement> pRelativeMovements, float f, float f1, PlayerTeleportEvent.TeleportCause cause) {
        pushChangeDimensionCause(cause);
        return teleportTo(worldserver, d0, d1, d2, pRelativeMovements, f, f1);
    }

    @Inject(method = "stopSleepInBed",
            at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;teleport(DDDFF)V"))
    private void banner$tpCauseExitBed(boolean wakeImmediately, boolean updateLevelForSleepingPlayers, CallbackInfo ci) {
        this.connection.pushTeleportCause(PlayerTeleportEvent.TeleportCause.EXIT_BED);
    }

    @Override
    public void pushChangeDimensionCause(PlayerTeleportEvent.TeleportCause cause) {
        banner$changeDimensionCause.set(cause);
    }

    @Inject(method = "startSleepInBed", at = @At("HEAD"), cancellable = true)
    private void banner$bedEvent(BlockPos blockPos,
                                 CallbackInfoReturnable<Either<BedSleepingProblem, Unit>> cir) {
        boolean force = bridge$startSleepInBed_force().getAndSet(false);
        Either<Player.BedSleepingProblem, Unit> bedResult = null;
        Direction direction = this.level().getBlockState(blockPos).getValue(HorizontalDirectionalBlock.FACING);
        if (!this.isSleeping() && this.isAlive()) {
            if (!this.level().dimensionType().natural()) {
                bedResult = Either.left(Player.BedSleepingProblem.NOT_POSSIBLE_HERE);
            } else if (!this.bedInRange(blockPos, direction)) {
                bedResult = Either.left(Player.BedSleepingProblem.TOO_FAR_AWAY);
            } else if (this.bedBlocked(blockPos, direction)) {
                bedResult = Either.left(Player.BedSleepingProblem.OBSTRUCTED);
            } else {
                if (this.level().isDay()) {
                    bedResult = Either.left(Player.BedSleepingProblem.NOT_POSSIBLE_NOW);
                } else {
                    if (bedResult == null) {
                        bedResult = Either.right(Unit.INSTANCE);
                    }
                }
            }
        } else {
            bedResult = Either.left(Player.BedSleepingProblem.OTHER_PROBLEM);
        }

        if (bedResult.left().orElse(null) == Player.BedSleepingProblem.OTHER_PROBLEM) {
            cir.setReturnValue(bedResult); // return immediately if the result is not bypassable by plugins
        }

        if (force) {
            bedResult = Either.right(Unit.INSTANCE);
        }

        bedResult = CraftEventFactory.callPlayerBedEnterEvent(this, blockPos, bedResult);

        if (bedResult.left().isPresent()) {
            cir.setReturnValue(bedResult);
        }
    }

    /**
     * @author wdog5
     * @reason bukkit
     */
    @Overwrite
    @Nullable
    public Component getTabListDisplayName() {
        return listName; // CraftBukkit
    }

    @Override
    public long getPlayerTime() {
        if (this.relativeTime) {
            return this.level().getDayTime() + this.timeOffset;
        }
        return this.level().getDayTime() - this.level().getDayTime() % 24000L + this.timeOffset;
    }

    @Override
    public CraftPortalEvent callPortalEvent(Entity entity, ServerLevel exitWorldServer, PositionImpl exitPosition, PlayerTeleportEvent.TeleportCause cause, int searchRadius, int creationRadius) {
        Location enter = this.getBukkitEntity().getLocation();
        Location exit = new Location(exitWorldServer.getWorld(), exitPosition.x(), exitPosition.y(), exitPosition.z(), this.getYRot(), this.getXRot());
        PlayerPortalEvent event = new PlayerPortalEvent(this.getBukkitEntity(), enter, exit, cause, 128, true, creationRadius);
        Bukkit.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled() || event.getTo() == null || event.getTo().getWorld() == null) {
            return null;
        }
        return new CraftPortalEvent(event);
    }

    @Override
    public Optional<BlockUtil.FoundRectangle> getExitPortal(ServerLevel worldserver, BlockPos blockposition, boolean flag, WorldBorder worldborder, int searchRadius, boolean canCreatePortal, int createRadius) {
        Optional<BlockUtil.FoundRectangle> optional = super.getExitPortal(worldserver, blockposition, flag, worldborder);
        if (optional.isPresent() || !canCreatePortal) {
            return optional;
        }
        Direction.Axis enumdirection_enumaxis = this.level().getBlockState(this.portalEntrancePos).getOptionalValue(NetherPortalBlock.AXIS).orElse(Direction.Axis.X);
        Optional<BlockUtil.FoundRectangle> optional1 =  worldserver.getPortalForcer().createPortal(blockposition, enumdirection_enumaxis, (ServerPlayer) (Object) this, createRadius);
        if (!optional1.isPresent()) {
            //  LOGGER.error("Unable to create a portal, likely target out of worldborder");
        }
        return optional1;
    }

    @Unique
    private transient BlockStateListPopulator banner$populator;


    @Inject(method = "createEndPlatform", at = @At("HEAD"))
    private void banner$playerCreatePortalBegin(ServerLevel level, BlockPos pos, CallbackInfo ci) {
        banner$populator = new BlockStateListPopulator(level);
    }

    @Redirect(method = "createEndPlatform", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean banner$playerCreatePortal(ServerLevel instance, BlockPos pos, BlockState blockState) {
        return banner$populator.setBlock(pos, blockState, 3);
    }

    @Inject(method = "createEndPlatform", at = @At("RETURN"))
    private void banner$playerCreatePortalEnd(ServerLevel level, BlockPos pos, CallbackInfo ci) {
        var blockList = banner$populator;
        banner$populator = null;
        var portalEvent = new PortalCreateEvent((List<org.bukkit.block.BlockState>) (List) blockList.getList(), level.getWorld(), this.getBukkitEntity(), PortalCreateEvent.CreateReason.END_PLATFORM);
        Bukkit.getPluginManager().callEvent(portalEvent);
        if (!portalEvent.isCancelled()) {
            blockList.updateList();
        }
    }

    @Override
    public Component bridge$listName() {
        return listName;
    }

    @Override
    public void banner$setListName(Component listName) {
        this.listName = listName;
    }

    @Override
    public Location bridge$compassTarget() {
        return compassTarget;
    }

    @Override
    public void banner$setCompassTarget(Location compassTarget) {
        this.compassTarget = compassTarget;
    }

    @Override
    public int bridge$newExp() {
        return newExp;
    }

    @Override
    public void banner$setNewExp(int newExp) {
        this.newExp = newExp;
    }

    @Override
    public int bridge$newLevel() {
        return newLevel;
    }

    @Override
    public void banner$setNewLevel(int newLevel) {
        this.newLevel = newLevel;
    }

    @Override
    public int bridge$newTotalExp() {
        return newTotalExp;
    }

    @Override
    public void banner$setNewTotalExp(int newTotalExp) {
        this.newTotalExp = newTotalExp;
    }

    @Override
    public boolean bridge$keepLevel() {
        return keepLevel;
    }

    @Override
    public void banner$setKeepLevel(boolean keepLevel) {
        this.keepLevel = keepLevel;
    }

    @Override
    public double bridge$maxHealthCache() {
        return maxHealthCache;
    }

    @Override
    public void banner$setMaxHealthCache(double maxHealthCache) {
        this.maxHealthCache = maxHealthCache;
    }

    @Override
    public boolean bridge$joining() {
        return joining;
    }

    @Override
    public void banner$setJoining(boolean joining) {
        this.joining = joining;
    }

    @Override
    public boolean bridge$sentListPacket() {
        return sentListPacket;
    }

    @Override
    public void banner$setSentListPacket(boolean sentListPacket) {
        this.sentListPacket = sentListPacket;
    }

    @Override
    public Integer bridge$clientViewDistance() {
        return clientViewDistance;
    }

    @Override
    public void banner$setClientViewDistance(Integer clientViewDistance) {
        this.clientViewDistance = clientViewDistance;
    }

    @Override
    public String bridge$kickLeaveMessage() {
        return kickLeaveMessage;
    }

    @Override
    public void banner$setKickLeaveMessage(String kickLeaveMessage) {
        this.kickLeaveMessage = kickLeaveMessage;
    }

    @Override
    public String bridge$displayName() {
        return displayName;
    }

    @Override
    public void banner$setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public long bridge$timeOffset() {
        return timeOffset;
    }

    @Override
    public void banner$setTimeOffset(long timeOffset) {
        this.timeOffset = timeOffset;
    }

    @Override
    public boolean bridge$relativeTime() {
        return relativeTime;
    }

    @Override
    public void banner$setRelativeTime(boolean relativeTime) {
        this.relativeTime = relativeTime;
    }

    @Override
    public String bridge$locale() {
        return locale;
    }

    @Override
    public void banner$setLocale(String locale) {
        this.locale = locale;
    }

    @Override
    public boolean banner$initialized() {
        return  banner$initialized;
    }
}
