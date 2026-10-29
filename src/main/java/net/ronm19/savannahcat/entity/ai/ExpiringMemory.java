package net.ronm19.savannahcat.entity.ai;

import org.jspecify.annotations.Nullable;

/** A single bounded temporary memory. Values are UUIDs, positions or immutable decisions, never saved entities. */
public final class ExpiringMemory<T> {
    private @Nullable T value;
    private long expiresAt;

    public void remember(T value, long now, int lifetime) {
        this.value = value;
        this.expiresAt = now + Math.max(1, lifetime);
    }

    public @Nullable T get(long now) {
        if (now >= expiresAt) clear();
        return value;
    }

    public void clear() { value = null; expiresAt = 0; }
    public boolean isPresent(long now) { return get(now) != null; }
}
