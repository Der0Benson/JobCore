package de.derbenson.jobcore;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class QuestActivityListener implements Listener {

    private final QuestManager questManager;

    public QuestActivityListener(final QuestManager questManager) {
        this.questManager = questManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(final BlockPlaceEvent event) {
        questManager.recordObjective(
                event.getPlayer(),
                QuestObjectiveType.PLACE_BLOCK,
                event.getBlockPlaced().getType().name(),
                1
        );
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraftItem(final CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) {
            return;
        }

        final int craftedAmount = estimateCraftedAmount(event, player, result);
        if (craftedAmount <= 0) {
            return;
        }

        questManager.recordObjective(player, QuestObjectiveType.CRAFT_ITEM, result.getType().name(), craftedAmount);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFurnaceExtract(final FurnaceExtractEvent event) {
        final Material material = event.getItemType();
        if (material == null || material.isAir()) {
            return;
        }

        questManager.recordObjective(
                event.getPlayer(),
                QuestObjectiveType.SMELT_ITEM,
                material.name(),
                Math.max(1, event.getItemAmount())
        );
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerItemConsume(final PlayerItemConsumeEvent event) {
        final ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            return;
        }

        questManager.recordObjective(event.getPlayer(), QuestObjectiveType.CONSUME_ITEM, item.getType().name(), 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnchantItem(final EnchantItemEvent event) {
        final ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            return;
        }

        questManager.recordObjective(event.getEnchanter(), QuestObjectiveType.ENCHANT_ITEM, item.getType().name(), 1);
    }

    private int estimateCraftedAmount(final CraftItemEvent event, final Player player, final ItemStack result) {
        final int amountPerCraft = Math.max(1, result.getAmount());
        if (!event.isShiftClick()) {
            return amountPerCraft;
        }

        final int maxCraftsFromMatrix = getMaxCraftOperations(event);
        if (maxCraftsFromMatrix <= 0) {
            return 0;
        }

        final int inventoryCapacity = getRemainingCapacity(player.getInventory(), result);
        if (inventoryCapacity <= 0) {
            return 0;
        }

        return Math.min(maxCraftsFromMatrix * amountPerCraft, inventoryCapacity);
    }

    private int getMaxCraftOperations(final CraftItemEvent event) {
        int maxCrafts = Integer.MAX_VALUE;
        boolean foundIngredient = false;

        for (final ItemStack ingredient : event.getInventory().getMatrix()) {
            if (ingredient == null || ingredient.getType().isAir()) {
                continue;
            }

            foundIngredient = true;
            maxCrafts = Math.min(maxCrafts, ingredient.getAmount());
        }

        return foundIngredient ? maxCrafts : 0;
    }

    private int getRemainingCapacity(final PlayerInventory inventory, final ItemStack result) {
        final int maxStackSize = Math.max(1, result.getMaxStackSize());
        int capacity = 0;

        for (final ItemStack slot : inventory.getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                capacity += maxStackSize;
                continue;
            }

            if (slot.isSimilar(result)) {
                capacity += Math.max(0, maxStackSize - slot.getAmount());
            }
        }

        return capacity;
    }
}
