package com.mohistmc.banner.mixin.server.level;

import com.google.common.collect.Lists;
import com.mohistmc.banner.BannerMod;
import com.mohistmc.banner.bukkit.BukkitMethodHooks;
import com.mohistmc.banner.bukkit.BukkitSnapshotCaptures;
import com.mohistmc.banner.bukkit.DistValidate;
import com.mohistmc.banner.bukkit.LevelPersistentData;
import com.mohistmc.banner.config.BannerConfig;
import com.mohistmc.banner.fabric.BannerDerivedWorldInfo;
import com.mohistmc.banner.fabric.WorldSymlink;
import com.mohistmc.banner.injection.server.level.InjectionServerLevel;
import com.mohistmc.banner.injection.world.level.storage.InjectionLevelStorageAccess;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Container;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelTicks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_20_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R1.block.CraftBlockState;
import org.bukkit.craftbukkit.v1_20_R1.block.CraftBlockStates;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.v1_20_R1.event.CraftEventFactory;
import org.bukkit.craftbukkit.v1_20_R1.generator.CustomChunkGenerator;
import org.bukkit.craftbukkit.v1_20_R1.util.BlockStateListPopulator;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftNamespacedKey;
import org.bukkit.craftbukkit.v1_20_R1.util.WorldUUID;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LightningStrike;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.server.MapInitializeEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(ServerLevel.class)
public abstract class MixinServerLevel extends Level implements WorldGenLevel, InjectionServerLevel {

    @Shadow public abstract LevelTicks<Block> getBlockTicks();

    @Shadow @Final private ServerChunkCache chunkSource;

    @Shadow public abstract List<ServerPlayer> players();

    @Shadow public abstract boolean sendParticles(ServerPlayer player, boolean longDistance, double posX, double posY, double posZ, Packet<?> packet);
    @Shadow @Final public ServerLevelData serverLevelData;

    @Shadow @NotNull public abstract MinecraftServer getServer();

    @Shadow public abstract <T extends ParticleOptions> int sendParticles(T type, double posX, double posY, double posZ, int particleCount, double xOffset, double yOffset, double zOffset, double speed);

    @Shadow @Final public PersistentEntitySectionManager<Entity> entityManager;
    @Shadow protected abstract void wakeUpAllPlayers();

    @Shadow @Final public static BlockPos END_SPAWN_POINT;

    @Shadow protected abstract boolean addEntity(Entity entity);

    @Shadow public abstract void addDuringTeleport(Entity entity);
    @Shadow public abstract boolean addWithUUID(Entity entity);

    @Shadow public abstract DimensionDataStorage getDataStorage();
    @Shadow public abstract ServerChunkCache getChunkSource();

    @Unique
    public LevelStorageSource.LevelStorageAccess convertable;
    @Unique
    public UUID uuid;
    @Unique
    public PrimaryLevelData K;

    @Unique
    private transient boolean banner$force;
    @Unique
    private final AtomicReference<CreatureSpawnEvent.SpawnReason> banner$reason = new AtomicReference<>();
    @Unique
    private final AtomicReference<Boolean> banner$timeSkipCancelled = new AtomicReference<>(false);
    @Unique
    public ResourceKey<LevelStem> typeKey;

    protected MixinServerLevel(WritableLevelData writableLevelData, ResourceKey<Level> resourceKey, RegistryAccess registryAccess, Holder<DimensionType> holder, Supplier<ProfilerFiller> supplier, boolean bl, boolean bl2, long l, int i) {
        super(writableLevelData, resourceKey, registryAccess, holder, supplier, bl, bl2, l, i);
    }

    @Unique
    public void banner$constructor(MinecraftServer minecraftServer, Executor backgroundExecutor, LevelStorageSource.LevelStorageAccess levelSave, ServerLevelData worldInfo, ResourceKey<Level> dimension, LevelStem levelStem, ChunkProgressListener statusListener, boolean isDebug, long seed, List<CustomSpawner> specialSpawners, boolean shouldBeTicking, RandomSequences randomSequences) {
        throw new RuntimeException();
    }

    @Unique
    public void banner$constructor(MinecraftServer minecraftServer, Executor backgroundExecutor, LevelStorageSource.LevelStorageAccess levelSave, PrimaryLevelData worldInfo, ResourceKey<Level> dimension, LevelStem levelStem, ChunkProgressListener statusListener, boolean isDebug, long seed, List<CustomSpawner> specialSpawners, boolean shouldBeTicking, RandomSequences randomSequences, org.bukkit.World.Environment env, org.bukkit.generator.ChunkGenerator gen, org.bukkit.generator.BiomeProvider biomeProvider) {
        banner$constructor(minecraftServer, backgroundExecutor, levelSave, worldInfo, dimension, levelStem, statusListener, isDebug, seed, specialSpawners, shouldBeTicking, randomSequences);
        this.banner$setGenerator(gen);
        this.banner$setEnvironment(env);
        this.banner$setBiomeProvider(biomeProvider);
        if (gen != null) {
            this.chunkSource.chunkMap.generator = new CustomChunkGenerator((ServerLevel) (Object) this, this.chunkSource.getGenerator(), gen);
        }
        getWorld();
    }

    @Inject(method = "<init>", at = @At(value = "RETURN"))
    private void banner$initWorldServer(MinecraftServer minecraftServer, Executor executor, LevelStorageSource.LevelStorageAccess levelStorageAccess, ServerLevelData serverLevelData, ResourceKey<Level> resourceKey, LevelStem levelStem, ChunkProgressListener chunkProgressListener, boolean bl, long l, List<CustomSpawner> list, boolean bl2, RandomSequences randomSequences, CallbackInfo ci) {
        this.banner$setPvpMode(minecraftServer.isPvpAllowed());
        this.convertable = levelStorageAccess;
        var typeKey = ((InjectionLevelStorageAccess) levelStorageAccess).bridge$getTypeKey();
        if (typeKey != null) {
            this.typeKey = typeKey;
        } else {
            var dimensions = BukkitMethodHooks.getServer().registryAccess().registryOrThrow(Registries.LEVEL_STEM);
            var key = dimensions.getResourceKey(levelStem);
            if (key.isPresent()) {
                this.typeKey = key.get();
            } else {
                BannerMod.LOGGER.warn("Assign {} to unknown level stem {}", resourceKey.location(), levelStem);
                this.typeKey = ResourceKey.create(Registries.LEVEL_STEM, resourceKey.location());
            }
        }
        if (serverLevelData instanceof PrimaryLevelData) {
            this.K = (PrimaryLevelData) serverLevelData;
        } else if (serverLevelData instanceof DerivedLevelData) {
            this.K = BannerDerivedWorldInfo.wrap(((DerivedLevelData) serverLevelData));
            ((DerivedLevelData) serverLevelData).setDimType(this.getTypeKey());
            if (BannerConfig.isSymlinkWorld) {
                WorldSymlink.create((DerivedLevelData) serverLevelData, levelStorageAccess.getDimensionPath(this.dimension()).toFile());
            }
        }
        this.uuid = WorldUUID.getUUID(levelStorageAccess.getDimensionPath(this.dimension()).toFile());
        this.getWorldBorder().banner$setWorld((ServerLevel) (Object) this);
        if (K != null) {
            K.setWorld((ServerLevel) (Object) this);
        }
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;getViewDistance()I"))
    private int banner$setViewDistance(PlayerList instance) {
        return this.bridge$spigotConfig().viewDistance;
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;getSimulationDistance()I"))
    private int banner$setSimulationDistance(PlayerList instance) {
        return this.bridge$spigotConfig().simulationDistance;
    }

    @Inject(method = "saveLevelData", at = @At("RETURN"))
    private void banner$savePdc(CallbackInfo ci) {
        var data = this.getDataStorage().computeIfAbsent(LevelPersistentData::new, () -> new LevelPersistentData(null), "bukkit_pdc");
        data.save(this.getWorld());
    }

    @Inject(method = "gameEvent", cancellable = true, at = @At("HEAD"))
    private void banner$gameEventEvent(GameEvent gameEvent, Vec3 pos, GameEvent.Context context, CallbackInfo ci) {
        var entity = context.sourceEntity();
        var i = gameEvent.getNotificationRadius();
        GenericGameEvent event = new GenericGameEvent(org.bukkit.GameEvent.getByKey(CraftNamespacedKey.fromMinecraft(BuiltInRegistries.GAME_EVENT.getKey(gameEvent))), new Location(this.getWorld(), pos.x(), pos.y(), pos.z()), (entity == null) ? null : entity.getBukkitEntity(), i, !Bukkit.isPrimaryThread());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Override
    public LevelChunk getChunkIfLoaded(int x, int z) {
        return this.chunkSource.getChunk(x, z, false);
    }

    @Override
    public <T extends ParticleOptions> int sendParticles(ServerPlayer sender, T t0, double d0, double d1, double d2, int i, double d3, double d4, double d5, double d6, boolean force) {
        ClientboundLevelParticlesPacket packet = new ClientboundLevelParticlesPacket(t0, force, d0, d1, d2, (float) d3, (float) d4, (float) d5, (float) d6, i);
        int j = 0;
        for (ServerPlayer entity : this.players()) {
            if (sender == null || entity.getBukkitEntity().canSee(sender.getBukkitEntity())) {
                if (this.sendParticles(entity, force, d0, d1, d2, packet)) {
                    ++j;
                }
            }
        }
        return j;
    }

    @Inject(method = "tickNonPassenger", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    private void banner$tickPortal(Entity entityIn, CallbackInfo ci) {
        entityIn.postTick();
    }

    @Inject(method = "tickPassenger", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lnet/minecraft/world/entity/Entity;rideTick()V"))
    private void banner$tickPortalPassenger(Entity ridingEntity, Entity passengerEntity, CallbackInfo ci) {
        passengerEntity.postTick();
    }

    @Inject(method = "tickChunk", at = @At(value = "INVOKE", ordinal = 0, target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    public void banner$thunder(LevelChunk chunkIn, int randomTickSpeed, CallbackInfo ci) {
        pushAddEntityReason(CreatureSpawnEvent.SpawnReason.LIGHTNING);
    }

    @Redirect(method = "tickChunk", at = @At(value = "INVOKE", ordinal = 1, target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean banner$thunder(ServerLevel serverWorld, Entity entityIn) {
        return strikeLightning(entityIn, LightningStrikeEvent.Cause.WEATHER);
    }

    @ModifyConstant(method = "tickChunk", constant = @Constant(intValue = 100000))
    private int banner$configChane(int constant) {
        return this.bridge$spigotConfig().thunderChance;
    }

    @Override
    public boolean strikeLightning(Entity entity) {
        return this.strikeLightning(entity, LightningStrikeEvent.Cause.UNKNOWN);
    }

    @Override
    public boolean strikeLightning(Entity entity, LightningStrikeEvent.Cause cause) {
        // Banner start - Compat for Modded Weather,ignore modded weather effect
        if (entity.getBukkitEntity() instanceof org.bukkit.entity.LightningStrike) {
            LightningStrikeEvent lightning = CraftEventFactory.callLightningStrikeEvent((LightningStrike) entity.getBukkitEntity(), cause);
            if (lightning.isCancelled()) {
                return false;
            }
        }
        // Banner end

        return this.addFreshEntity(entity);
    }

    @Inject(method = "tickChunk", cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD, at = @At(value = "INVOKE", ordinal = 0, shift = At.Shift.BEFORE, target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    public void banner$snowForm0(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci, ChunkPos chunkPos, boolean bl, int i, int j, ProfilerFiller profilerFiller, BlockPos blockPos, BlockPos blockPos2, Biome biome) {

        CraftBlockState craftBlockState = CraftBlockStates.getBlockState((ServerLevel) (Object) this, blockPos2, 3);
        craftBlockState.setData(Blocks.ICE.defaultBlockState());

        BlockFormEvent event = new BlockFormEvent(craftBlockState.getBlock(), craftBlockState);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "tickChunk", cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD, at = @At(value = "INVOKE", ordinal = 1, shift = At.Shift.BEFORE, target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    public void banner$snowForm1(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci, ChunkPos chunkPos, boolean bl, int i, int j, ProfilerFiller profilerFiller, BlockPos blockPos, BlockPos blockPos2, Biome biome, int k, BlockState blockState, int l, BlockState blockState2) {

        CraftBlockState craftBlockState = CraftBlockStates.getBlockState((ServerLevel) (Object) this, blockPos, 3);
        craftBlockState.setData(blockState2);

        BlockFormEvent event = new BlockFormEvent(craftBlockState.getBlock(), craftBlockState);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "tickChunk", cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD, at = @At(value = "INVOKE", ordinal = 2, shift = At.Shift.BEFORE, target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    public void banner$snowForm2(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci, ChunkPos chunkPos, boolean bl, int i, int j, ProfilerFiller profilerFiller, BlockPos blockPos, BlockPos blockPos2, Biome biome) {

        CraftBlockState craftBlockState = CraftBlockStates.getBlockState((ServerLevel) (Object) this, blockPos, 3);
        craftBlockState.setData(Blocks.SNOW.defaultBlockState());

        BlockFormEvent event = new BlockFormEvent(craftBlockState.getBlock(), craftBlockState);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "save", at = @At(value = "JUMP", ordinal = 0, opcode = Opcodes.IFNULL))
    private void banner$worldSaveEvent(ProgressListener progress, boolean flush, boolean skipSave, CallbackInfo ci) {
        if (DistValidate.isValid((LevelAccessor) this)) {
            Bukkit.getPluginManager().callEvent(new WorldSaveEvent(getWorld()));
        }
    }

    @Inject(method = "save", at = @At("TAIL"))
    private void banner$saveAllChunks(ProgressListener progress, boolean flush, boolean skipSave, CallbackInfo ci) {
        // CraftBukkit start - moved from MinecraftServer.saveAllChunks
        if (this.serverLevelData instanceof PrimaryLevelData worldInfo) {
            worldInfo.setWorldBorder(this.getWorldBorder().createSettings());
            worldInfo.setCustomBossEvents(this.getServer().getCustomBossEvents().save());
            this.convertable.saveDataTag(this.getServer().registryAccess(), worldInfo, this.getServer().getPlayerList().getSingleplayerData());
        }
        // CraftBukkit end
    }

    @Inject(method = "unload", at = @At("HEAD"))
    public void banner$closeOnChunkUnloading(LevelChunk chunkIn, CallbackInfo ci) {
        for (BlockEntity tileentity : chunkIn.getBlockEntities().values()) {
            if (tileentity instanceof Container) {
                for (HumanEntity h : Lists.newArrayList(((Container) tileentity).getViewers())) {
                    if (h instanceof CraftHumanEntity) {
                        ((CraftHumanEntity) h).getHandle().closeContainer();
                    }
                }
            }
        }
    }

    @Redirect(method = "sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;sendParticles(Lnet/minecraft/server/level/ServerPlayer;ZDDDLnet/minecraft/network/protocol/Packet;)Z"))
    public boolean banner$particleVisible(ServerLevel serverWorld, ServerPlayer player, boolean longDistance, double posX, double posY, double posZ, Packet<?> packet) {
        return this.sendParticles(player, banner$force, posX, posY, posZ, packet);
    }

    @Override
    public <T extends ParticleOptions> int sendParticles(T type, double posX, double posY, double posZ, int particleCount, double xOffset, double yOffset, double zOffset, double speed, boolean force) {
        banner$force = force;
        return this.sendParticles(type, posX, posY, posZ, particleCount, xOffset, yOffset, zOffset, speed);
    }

    @Inject(method = "addEntity", cancellable = true, at = @At(value = "HEAD"))
    private void banner$canAddEntity(Entity entityIn, CallbackInfoReturnable<Boolean> cir) {
        CreatureSpawnEvent.SpawnReason reason = banner$reason.get() == null ? CreatureSpawnEvent.SpawnReason.DEFAULT : banner$reason.get();
        if (!BannerConfig.spawnForChunk && reason.equals(SpawnReason.CHUNK_GEN)) {
            cir.setReturnValue(false);
        }

        if (!BannerConfig.spawnForNatural && reason.equals(SpawnReason.NATURAL)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "addEntity", cancellable = true, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;addNewEntity(Lnet/minecraft/world/level/entity/EntityAccess;)Z"))
    private void banner$addEntityEvent(Entity entityIn, CallbackInfoReturnable<Boolean> cir) {
        CreatureSpawnEvent.SpawnReason reason = banner$reason.get() == null ? CreatureSpawnEvent.SpawnReason.DEFAULT : banner$reason.get();
        banner$reason.set(null);
        if (DistValidate.isValid((LevelAccessor) this) && !CraftEventFactory.doEntityAddEventCalling((ServerLevel) (Object) this, entityIn, reason)) {
            cir.setReturnValue(false);
        }
    }

    // Banner start
    @Unique
    public AtomicBoolean canaddFreshEntity = new AtomicBoolean(false);

    @Override
    public boolean canAddFreshEntity() {
        return canaddFreshEntity.getAndSet(false);
    }
    // Banner end

    /**
     * @author Mgazul
     * @reason fix mixin
     */
    @Overwrite
    public boolean addFreshEntity(Entity entity) {
        boolean add = addEntity(entity);
        canaddFreshEntity.set(add);
        return add;
    }

    @Override
    public boolean addFreshEntity(Entity entity, CreatureSpawnEvent.SpawnReason reason) {
        pushAddEntityReason(reason);
        return addFreshEntity(entity);
    }

    @Inject(method = "addEntity", at = @At("RETURN"))
    public void banner$resetReason(Entity entityIn, CallbackInfoReturnable<Boolean> cir) {
        banner$reason.set(null);
    }

    @Override
    public boolean addWithUUID(Entity entity, CreatureSpawnEvent.SpawnReason reason) {
        pushAddEntityReason(reason);
        return this.addWithUUID(entity);
    }

    @Override
    public void addDuringTeleport(Entity entity, CreatureSpawnEvent.SpawnReason reason) {
        pushAddEntityReason(reason);
        addDuringTeleport(entity);
    }

    @Override
    public boolean tryAddFreshEntityWithPassengers(Entity entity, CreatureSpawnEvent.SpawnReason reason) {
        if (entity.getSelfAndPassengers().map(Entity::getUUID).anyMatch(this.entityManager::isLoaded)) {
            return false;
        }else {
            pushAddEntityReason(reason);
            return this.addAllEntities(entity, reason);
        }
    }

    @Override
    public boolean addEntity(Entity entity, CreatureSpawnEvent.SpawnReason reason) {
        return addFreshEntity(entity, reason);
    }

    /**
     * @author wdog5
     * @reason functionallyy replaced
     */
    @Overwrite
    @Nullable
    public MapItemSavedData getMapData(String mapName) {
        return BukkitMethodHooks.getServer().overworld().getDataStorage().get((nbt) -> {
            MapItemSavedData newMap = MapItemSavedData.load(nbt);
            newMap.banner$setId(mapName);
            MapInitializeEvent event = new MapInitializeEvent(newMap.bridge$mapView());
            Bukkit.getServer().getPluginManager().callEvent(event);
            return newMap;
        }, mapName);
    }

    @Inject(method = "setMapData", at = @At("HEAD"))
    private void banner$mapSetId(String id, MapItemSavedData data, CallbackInfo ci) {
        data.banner$setId(id);
    }

    @Inject(method = "blockUpdated", cancellable = true, at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;updateNeighborsAt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;)V"))
    private void banner$returnIfPopulate(BlockPos pos, Block block, CallbackInfo ci) {
        if (bridge$populating()) {
            ci.cancel();
        }
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setDayTime(J)V"))
    private void banner$timeSkip(ServerLevel world, long time) {
        TimeSkipEvent event = new TimeSkipEvent(this.getWorld(), TimeSkipEvent.SkipReason.NIGHT_SKIP, (time - time % 24000L) - this.getDayTime());
        if (this.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
            Bukkit.getPluginManager().callEvent(event);
            banner$timeSkipCancelled.set(event.isCancelled());
            if (!event.isCancelled()) {
                world.setDayTime(this.getDayTime() + event.getSkipAmount());
            }
        }
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;wakeUpAllPlayers()V"))
    private void banner$notWakeIfCancelled(ServerLevel world) {
        if (!banner$timeSkipCancelled.getAndSet(false)) {
            this.wakeUpAllPlayers();
        }
    }

    @ModifyVariable(method = "tickBlock", ordinal = 0, argsOnly = true, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private BlockPos banner$captureTickingBlock(BlockPos pos) {
        BukkitSnapshotCaptures.captureTickingBlock((ServerLevel) (Object) this, pos);
        return pos;
    }

    @ModifyVariable(method = "tickBlock", ordinal = 0, argsOnly = true, at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lnet/minecraft/world/level/block/state/BlockState;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private BlockPos banner$resetTickingBlock(BlockPos pos) {
        BukkitSnapshotCaptures.resetTickingBlock();
        return pos;
    }

    /**
     * @author wdog5
     * @reason
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Overwrite
    public static void makeObsidianPlatform(ServerLevel world) {
        BlockPos blockpos = END_SPAWN_POINT;
        int i = blockpos.getX();
        int j = blockpos.getY() - 2;
        int k = blockpos.getZ();
        BlockStateListPopulator blockList = new BlockStateListPopulator(world);
        BlockPos.betweenClosed(i - 2, j + 1, k - 2, i + 2, j + 3, k + 2).forEach((pos) -> {
            blockList.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        });
        BlockPos.betweenClosed(i - 2, j, k - 2, i + 2, j, k + 2).forEach((pos) -> {
            blockList.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), 3);
        });
        if (!DistValidate.isValid(world)) {
            blockList.updateList();
            BukkitSnapshotCaptures.getEndPortalEntity();
            return;
        }
        CraftWorld bworld = world.getWorld();
        boolean spawnPortal = BukkitSnapshotCaptures.getEndPortalSpawn();
        Entity entity = BukkitSnapshotCaptures.getEndPortalEntity();
        PortalCreateEvent portalEvent = new PortalCreateEvent((List) blockList.getList(), bworld, entity == null ? null : entity.getBukkitEntity(), PortalCreateEvent.CreateReason.END_PLATFORM);
        portalEvent.setCancelled(!spawnPortal);
        Bukkit.getPluginManager().callEvent(portalEvent);
        if (!portalEvent.isCancelled()) {
            blockList.updateList();
        }
    }

    @ModifyVariable(method = "tickChunk", ordinal = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private BlockPos banner$captureRandomTick(BlockPos pos) {
        BukkitSnapshotCaptures.captureTickingBlock((ServerLevel) (Object) this, pos);
        return pos;
    }

    @ModifyVariable(method = "tickChunk", ordinal = 0, at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lnet/minecraft/world/level/block/state/BlockState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private BlockPos banner$resetRandomTick(BlockPos pos) {
        BukkitSnapshotCaptures.resetTickingBlock();
        return pos;
    }

    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void banner$checkSpawnEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (BannerConfig.nospawnEntity.contains(entity.getBukkitEntity().getType().name())) {
            cir.setReturnValue(false);
        }
    }

    @Override
    public boolean addEntitySerialized(Entity entity, CreatureSpawnEvent.SpawnReason reason) {
        return addWithUUID(entity, reason);
    }

    @Override
    public PrimaryLevelData bridge$serverLevelDataCB() {
        return K;
    }

    @Override
    public LevelStorageSource.LevelStorageAccess bridge$convertable() {
        return convertable;
    }

    @Override
    public ResourceKey<LevelStem> getTypeKey() {
        return typeKey;
    }

    @Override
    public UUID bridge$uuid() {
        return uuid;
    }
}
