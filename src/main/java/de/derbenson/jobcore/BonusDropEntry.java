package de.derbenson.jobcore;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public record BonusDropEntry(Material material, int amount, int weight) {

    
    public ItemStack toItemStack() {
        return new ItemStack(material, Math.max(1, amount));
    }
}

