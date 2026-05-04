package de.derbenson.jobcore.api.event;

import de.derbenson.jobcore.Job;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class JobCoreExperienceGainEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Job job;
    private final int originalAmount;
    private final boolean direct;
    private int amount;
    private boolean cancelled;

    public JobCoreExperienceGainEvent(
            final Player player,
            final Job job,
            final int originalAmount,
            final boolean direct
    ) {
        this.player = player;
        this.job = job;
        this.originalAmount = Math.max(0, originalAmount);
        this.amount = Math.max(0, originalAmount);
        this.direct = direct;
    }

    public Player getPlayer() {
        return player;
    }

    public Job getJob() {
        return job;
    }

    public int getOriginalAmount() {
        return originalAmount;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(final int amount) {
        this.amount = Math.max(0, amount);
    }

    public boolean isDirect() {
        return direct;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(final boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
