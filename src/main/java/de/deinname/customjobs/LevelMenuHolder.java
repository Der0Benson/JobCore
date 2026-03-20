package de.deinname.customjobs;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class LevelMenuHolder implements InventoryHolder {

    private final LevelMenuView view;
    private final Job selectedJob;
    private final int page;
    private Inventory inventory;

    
    public LevelMenuHolder(final LevelMenuView view, final Job selectedJob, final int page) {
        this.view = view;
        this.selectedJob = selectedJob;
        this.page = page;
    }

    
    public LevelMenuView getView() {
        return view;
    }

    
    public Job getSelectedJob() {
        return selectedJob;
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
