package org.bukkit.craftbukkit.v1_20_R1.block;

import com.google.common.base.Preconditions;
import java.lang.ref.WeakReference;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_20_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R1.block.data.CraftBlockData;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftLocation;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftMagicNumbers;
import org.bukkit.material.Attachable;
import org.bukkit.material.MaterialData;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

public class CraftBlockState implements org.bukkit.block.BlockState {
    protected final CraftWorld world;
    private final BlockPos position;
    protected BlockState data;
    protected int flag;
    private WeakReference<LevelAccessor> weakWorld;

    protected CraftBlockState(final Block block) {
        this(block.getWorld(), ((CraftBlock) block).getPosition(), ((CraftBlock) block).getNMS());
        this.flag = 3;

        setWorldHandle(((CraftBlock) block).getHandle());
    }

    protected CraftBlockState(final Block block, int flag) {
        this(block);
        this.flag = flag;
    }

    // world can be null for non-placed BlockStates.
    protected CraftBlockState(@Nullable World world, BlockPos blockPosition, BlockState blockData) {
        this.world = (CraftWorld) world;
        position = blockPosition;
        data = blockData;
    }

    public void setWorldHandle(LevelAccessor generatorAccess) {
        if (generatorAccess instanceof net.minecraft.world.level.Level) {
            this.weakWorld = null;
        } else {
            this.weakWorld = new WeakReference<>(generatorAccess);
        }
    }

    // Returns null if weakWorld is not available and the BlockState is not placed.
    // If this returns a World instead of only a GeneratorAccess, this implies that this BlockState is placed.
    public LevelAccessor getWorldHandle() {
        if (weakWorld == null) {
            return this.isPlaced() ? world.getHandle() : null;
        }

        LevelAccessor access = weakWorld.get();
        if (access == null) {
            weakWorld = null;
            return this.isPlaced() ? world.getHandle() : null;
        }

        return access;
    }

    protected final boolean isWorldGeneration() {
        LevelAccessor generatorAccess = this.getWorldHandle();
        return generatorAccess != null && !(generatorAccess instanceof net.minecraft.world.level.Level);
    }

    protected final void ensureNoWorldGeneration() {
        Preconditions.checkState(!isWorldGeneration(), "This operation is not supported during world generation!");
    }

    @Override
    public World getWorld() {
        requirePlaced();
        return world;
    }

    @Override
    public int getX() {
        return position.getX();
    }

    @Override
    public int getY() {
        return position.getY();
    }

    @Override
    public int getZ() {
        return position.getZ();
    }

    @Override
    public Chunk getChunk() {
        requirePlaced();
        return world.getChunkAt(getX() >> 4, getZ() >> 4);
    }

    public void setData(BlockState data) {
        this.data = data;
    }

    public BlockPos getPosition() {
        return this.position;
    }

    public BlockState getHandle() {
        return this.data;
    }

    @Override
    public BlockData getBlockData() {
        return CraftBlockData.fromData(data);
    }

    @Override
    public void setBlockData(BlockData data) {
        Preconditions.checkArgument(data != null, "BlockData cannot be null");
        this.data = ((CraftBlockData) data).getState();
    }

    @Override
    public void setData(final MaterialData data) {
        Material mat = CraftMagicNumbers.getMaterial(this.data).getItemType();

        if ((mat == null) || (mat.getData() == null)) {
            this.data = CraftMagicNumbers.getBlock(data);
        } else {
            Preconditions.checkArgument((data.getClass() == mat.getData()) || (data.getClass() == MaterialData.class), "Provided data is not of type %s, found %s", mat.getData().getName(), data.getClass().getName());
            this.data = CraftMagicNumbers.getBlock(data);
        }
    }

    @Override
    public MaterialData getData() {
        return CraftMagicNumbers.getMaterial(data);
    }

    @Override
    public void setType(final Material type) {
        Preconditions.checkArgument(type != null, "Material cannot be null");
        Preconditions.checkArgument(type.isBlock(), "Material must be a block!");

        if (this.getType() != type) {
            this.data = CraftMagicNumbers.getBlock(type).defaultBlockState();
        }
    }

    @Override
    public Material getType() {
        return CraftMagicNumbers.getMaterial(data.getBlock());
    }

    public void setFlag(int flag) {
        this.flag = flag;
    }

    public int getFlag() {
        return flag;
    }

    @Override
    public byte getLightLevel() {
        return getBlock().getLightLevel();
    }

    @Override
    public CraftBlock getBlock() {
        requirePlaced();
        return CraftBlock.at(getWorldHandle(), position);
    }

    @Override
    public boolean update() {
        return update(false);
    }

    @Override
    public boolean update(boolean force) {
        return update(force, true);
    }

    @Override
    public boolean update(boolean force, boolean applyPhysics) {
        if (!isPlaced()) {
            return true;
        }
        LevelAccessor access = getWorldHandle();
        CraftBlock block = getBlock();

        if (block.getType() != getType()) {
            if (!force) {
                return false;
            }
        }

        BlockState newBlock = this.data;
        block.setTypeAndData(newBlock, applyPhysics);
        if (access instanceof net.minecraft.world.level.Level) {
            world.getHandle().sendBlockUpdated(
                    position,
                    block.getNMS(),
                    newBlock,
                    3
            );
        }

        // Update levers etc
        if (false && applyPhysics && getData() instanceof Attachable) { // Call does not map to new API
            world.getHandle().updateNeighborsAt(position.relative(CraftBlock.blockFaceToNotch(((Attachable) getData()).getAttachedFace())), newBlock.getBlock());
        }

        return true;
    }

    @Override
    public byte getRawData() {
        return CraftMagicNumbers.toLegacyData(data);
    }

    @Override
    public Location getLocation() {
        return CraftLocation.toBukkit(this.position, this.world);
    }

    @Override
    public Location getLocation(Location loc) {
        if (loc != null) {
            loc.setWorld(world);
            loc.setX(getX());
            loc.setY(getY());
            loc.setZ(getZ());
            loc.setYaw(0);
            loc.setPitch(0);
        }

        return loc;
    }

    @Override
    public void setRawData(byte data) {
        this.data = CraftMagicNumbers.getBlock(getType(), data);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final CraftBlockState other = (CraftBlockState) obj;
        if (this.world != other.world && (this.world == null || !this.world.equals(other.world))) {
            return false;
        }
        if (this.position != other.position && (this.position == null || !this.position.equals(other.position))) {
            return false;
        }
        if (this.data != other.data && (this.data == null || !this.data.equals(other.data))) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 73 * hash + (this.world != null ? this.world.hashCode() : 0);
        hash = 73 * hash + (this.position != null ? this.position.hashCode() : 0);
        hash = 73 * hash + (this.data != null ? this.data.hashCode() : 0);
        return hash;
    }

    @Override
    public void setMetadata(String metadataKey, MetadataValue newMetadataValue) {
        requirePlaced();
        world.getBlockMetadata().setMetadata(getBlock(), metadataKey, newMetadataValue);
    }

    @Override
    public List<MetadataValue> getMetadata(String metadataKey) {
        requirePlaced();
        return world.getBlockMetadata().getMetadata(getBlock(), metadataKey);
    }

    @Override
    public boolean hasMetadata(String metadataKey) {
        requirePlaced();
        return world.getBlockMetadata().hasMetadata(getBlock(), metadataKey);
    }

    @Override
    public void removeMetadata(String metadataKey, Plugin owningPlugin) {
        requirePlaced();
        world.getBlockMetadata().removeMetadata(getBlock(), metadataKey, owningPlugin);
    }

    @Override
    public boolean isPlaced() {
        return world != null;
    }

    protected void requirePlaced() {
        Preconditions.checkState(isPlaced(), "The blockState must be placed to call this method");
    }

    // Banner start
    @Override
    public String toString() {
        return this.data.toString();
    }
    // Banner end
}
