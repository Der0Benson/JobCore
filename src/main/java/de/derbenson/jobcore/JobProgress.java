package de.derbenson.jobcore;

public final class JobProgress {

    private long xp;
    private int level;
    private double fractionalXp;

    
    public JobProgress() {
        this(0L, 0, 0.0D);
    }

    
    public JobProgress(final long xp, final int level, final double fractionalXp) {
        this.xp = Math.max(0L, xp);
        this.level = Math.max(0, level);
        this.fractionalXp = Math.max(0.0D, fractionalXp);
    }

    
    public long getXp() {
        return xp;
    }

    
    public void setXp(final long xp) {
        this.xp = Math.max(0L, xp);
    }

    
    public int getLevel() {
        return level;
    }

    
    public void setLevel(final int level) {
        this.level = Math.max(0, level);
    }

    
    public double getFractionalXp() {
        return fractionalXp;
    }

    
    public void setFractionalXp(final double fractionalXp) {
        this.fractionalXp = Math.max(0.0D, fractionalXp);
    }
}

