package de.derbenson.jobcore;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class QuestMenuHolder implements InventoryHolder {

    private final int page;
    private Inventory inventory;

    public QuestMenuHolder(final int page) {
        this.page = page;
    }

    public int getPage() {
        return page;
    }

    public void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
