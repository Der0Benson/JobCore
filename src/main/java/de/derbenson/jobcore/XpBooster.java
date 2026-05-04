package de.derbenson.jobcore;

public record XpBooster(double bonusMultiplier, long expiresAtMillis) {

    public XpBooster {
        bonusMultiplier = Math.max(0.0D, bonusMultiplier);
        expiresAtMillis = Math.max(0L, expiresAtMillis);
    }

    public boolean isExpired(final long nowMillis) {
        return expiresAtMillis <= nowMillis;
    }
}
