package com.mohistmc.banner.injection.world.item.trading;

import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftMerchantRecipe;

public interface InjectionMerchantOffer {

    default CraftMerchantRecipe asBukkit() {
        return null;
    }

    default void bukkit(CraftMerchantRecipe bukkit) {

    }
}
