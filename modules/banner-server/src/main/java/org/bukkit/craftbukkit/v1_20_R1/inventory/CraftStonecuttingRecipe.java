package org.bukkit.craftbukkit.v1_20_R1.inventory;

import com.mohistmc.banner.bukkit.BukkitMethodHooks;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftNamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.StonecuttingRecipe;

public class CraftStonecuttingRecipe extends StonecuttingRecipe implements CraftRecipe {
    public CraftStonecuttingRecipe(NamespacedKey key, ItemStack result, RecipeChoice source) {
        super(key, result, source);
    }

    public static CraftStonecuttingRecipe fromBukkitRecipe(StonecuttingRecipe recipe) {
        if (recipe instanceof CraftStonecuttingRecipe) {
            return (CraftStonecuttingRecipe) recipe;
        }
        CraftStonecuttingRecipe ret = new CraftStonecuttingRecipe(recipe.getKey(), recipe.getResult(), recipe.getInputChoice());
        ret.setGroup(recipe.getGroup());
        return ret;
    }

    @Override
    public void addToCraftingManager() {
        ItemStack result = this.getResult();

        BukkitMethodHooks.getServer().getRecipeManager().addRecipe(new net.minecraft.world.item.crafting.StonecutterRecipe(CraftNamespacedKey.toMinecraft(this.getKey()), this.getGroup(), toNMS(this.getInputChoice(), true), CraftItemStack.asNMSCopy(result)));
    }
}
