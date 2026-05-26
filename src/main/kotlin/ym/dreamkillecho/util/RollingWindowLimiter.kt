package ym.dreamkillecho.util

import java.time.Clock
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

class RollingWindowLimiter(
    private val maxEvents: Int,
    private val windowMillis: Long,
    private val clock: Clock = Clock.systemUTC()
) {
    private val events = ArrayDeque<Long>()

    @Synchronized
    fun tryAcquire(): Boolean {
        if (maxEvents <= 0) return false
        val now = clock.millis()
        while (events.isNotEmpty() && now - events.first() > windowMillis) {
            events.removeFirst()
        }
        if (events.size >= maxEvents) return false
        events.addLast(now)
        return true
    }
}

class PerKeyCooldown(private val cooldownMillis: Long, private val clock: Clock = Clock.systemUTC()) {
    private val last = ConcurrentHashMap<String, Long>()

    fun tryAcquire(key: String): Boolean {
        if (cooldownMillis <= 0L) return true
        val now = clock.millis()
        val previous = last[key] ?: 0L
        if (now - previous < cooldownMillis) return false
        last[key] = now
        return true
    }
}
