package org.bukkit.craftbukkit.v1_20_R1.entity;

import com.google.common.base.Preconditions;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.world.item.Item;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_20_R1.CraftServer;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftInventory;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftMagicNumbers;
import org.bukkit.entity.Piglin;
import org.bukkit.inventory.Inventory;

public class CraftPiglin extends CraftPiglinAbstract implements Piglin {

    public CraftPiglin(CraftServer server, net.minecraft.world.entity.monster.piglin.Piglin entity) {
        super(server, entity);
    }

    @Override
    public boolean isAbleToHunt() {
        return getHandle().cannotHunt;
    }

    @Override
    public void setIsAbleToHunt(boolean flag) {
        getHandle().cannotHunt = flag;
    }

    @Override
    public boolean addBarterMaterial(Material material) {
        Preconditions.checkArgument(material != null, "material cannot be null");

        Item item = CraftMagicNumbers.getItem(material);
        return getHandle().bridge$allowedBarterItems().add(item);

    }

    @Override
    public boolean removeBarterMaterial(Material material) {
        Preconditions.checkArgument(material != null, "material cannot be null");

        Item item = CraftMagicNumbers.getItem(material);
        return getHandle().bridge$allowedBarterItems().remove(item);
    }

    @Override
    public boolean addMaterialOfInterest(Material material) {
        Preconditions.checkArgument(material != null, "material cannot be null");

        Item item = CraftMagicNumbers.getItem(material);
        return getHandle().bridge$interestItems().add(item);
    }

    @Override
    public boolean removeMaterialOfInterest(Material material) {
        Preconditions.checkArgument(material != null, "material cannot be null");

        Item item = CraftMagicNumbers.getItem(material);
        return getHandle().bridge$interestItems().remove(item);
    }

    @Override
    public Set<Material> getInterestList() {
        return Collections.unmodifiableSet(getHandle().bridge$interestItems().stream().map(CraftMagicNumbers::getMaterial).collect(Collectors.toSet()));
    }

    @Override
    public Set<Material> getBarterList() {
        return Collections.unmodifiableSet(getHandle().bridge$allowedBarterItems().stream().map(CraftMagicNumbers::getMaterial).collect(Collectors.toSet()));
    }

    @Override
    public Inventory getInventory() {
        return new CraftInventory(getHandle().inventory);
    }

    @Override
    public net.minecraft.world.entity.monster.piglin.Piglin getHandle() {
        return (net.minecraft.world.entity.monster.piglin.Piglin) super.getHandle();
    }

    @Override
    public String toString() {
        return "CraftPiglin";
    }
}
