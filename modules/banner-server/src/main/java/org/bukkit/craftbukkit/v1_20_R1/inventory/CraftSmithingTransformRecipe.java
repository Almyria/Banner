package org.bukkit.craftbukkit.v1_20_R1.inventory;

import com.mohistmc.banner.bukkit.BukkitMethodHooks;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftNamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.SmithingTransformRecipe;

public class CraftSmithingTransformRecipe extends SmithingTransformRecipe implements CraftRecipe {
    public CraftSmithingTransformRecipe(NamespacedKey key, ItemStack result, RecipeChoice template, RecipeChoice base, RecipeChoice addition) {
        super(key, result, template, base, addition);
    }

    public static CraftSmithingTransformRecipe fromBukkitRecipe(SmithingTransformRecipe recipe) {
        if (recipe instanceof CraftSmithingTransformRecipe) {
            return (CraftSmithingTransformRecipe) recipe;
        }
        CraftSmithingTransformRecipe ret = new CraftSmithingTransformRecipe(recipe.getKey(), recipe.getResult(), recipe.getTemplate(), recipe.getBase(), recipe.getAddition());
        return ret;
    }

    @Override
    public void addToCraftingManager() {
        ItemStack result = this.getResult();

        BukkitMethodHooks.getServer().getRecipeManager().addRecipe(new net.minecraft.world.item.crafting.SmithingTransformRecipe(CraftNamespacedKey.toMinecraft(this.getKey()), toNMS(this.getTemplate(), true), toNMS(this.getBase(), true), toNMS(this.getAddition(), true), CraftItemStack.asNMSCopy(result)));
    }
}
