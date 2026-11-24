package io.github.jacksonvieira.ratelimiter.core;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * Drops idle keys from an in-memory limiter without a background thread: every {@code interval} calls, one
 * caller sweeps the map and removes the entries whose state is indistinguishable from a brand-new key.
 *
 * <p>Removing such an entry never changes a decision. The only race is a request that fetched the entry just
 * before it was removed: its update goes to the orphaned state and is forgotten, which can let at most one
 * extra request through for that key.
 */
final class IdleKeySweeper<V> {

    static final long DEFAULT_INTERVAL = 1024;

    private final ConcurrentMap<String, V> entries;
    private final Predicate<V> idle;
    private final long interval;
    private final AtomicLong calls = new AtomicLong();
    private final AtomicBoolean sweeping = new AtomicBoolean();

    IdleKeySweeper(ConcurrentMap<String, V> entries, Predicate<V> idle) {
        this(entries, idle, DEFAULT_INTERVAL);
    }

    IdleKeySweeper(ConcurrentMap<String, V> entries, Predicate<V> idle, long interval) {
        if (interval < 1) {
            throw new IllegalArgumentException("interval must be at least 1, got " + interval);
        }
        this.entries = entries;
        this.idle = idle;
        this.interval = interval;
    }

    void onCall() {
        if (calls.incrementAndGet() % interval == 0) {
            sweep();
        }
    }

    private void sweep() {
        if (!sweeping.compareAndSet(false, true)) {
            return;
        }
        try {
            entries.values().removeIf(idle);
        } finally {
            sweeping.set(false);
        }
    }
}
