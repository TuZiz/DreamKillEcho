package ym.dreamkillecho.death

import ym.dreamkillecho.config.PluginSettings
import ym.dreamkillecho.util.PerKeyCooldown
import ym.dreamkillecho.util.RollingWindowLimiter
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

class AntiAbuseService(settings: PluginSettings) {
    private val antiSpam = settings.antiSpam
    private val antiFarm = settings.antiFarm
    private val killerCooldown = PerKeyCooldown(antiSpam.sameKillerCooldownSeconds * 1000L)
    private val victimCooldown = PerKeyCooldown(antiSpam.sameVictimCooldownSeconds * 1000L)
    private val broadcastLimiter = RollingWindowLimiter(antiSpam.maxBroadcastPerMinute, 60_000L)
    private val effectLimiter = RollingWindowLimiter(antiSpam.maxEffectPerMinute, 60_000L)
    private val sameVictimTimes = ConcurrentHashMap<String, Long>()
    private val dailyCounts = ConcurrentHashMap<String, Pair<LocalDate, Int>>()

    fun evaluate(context: DeathContext): KillProcessResult {
        val killer = context.killer ?: return KillProcessResult(false, broadcastLimiter.tryAcquire(), false, 0, false)
        val pairKey = "${killer.uniqueId}:${context.victim.uniqueId}"
        val reverseKey = "${context.victim.uniqueId}:${killer.uniqueId}"
        val now = System.currentTimeMillis()
        val farmBlocked = if (antiFarm.enabled) {
            val last = sameVictimTimes[pairKey] ?: 0L
            sameVictimTimes[pairKey] = now
            val tooSoon = now - last < antiFarm.sameVictimNoStatsSeconds * 1000L
            val today = LocalDate.now()
            val current = dailyCounts[pairKey]
            val count = if (current?.first == today) current.second + 1 else 1
            dailyCounts[pairKey] = today to count
            val sameIp = antiFarm.sameIpNoStats &&
                context.killerIp != null &&
                context.killerIp == context.victimIp
            tooSoon || sameIp || count > antiFarm.maxSameVictimCountPerDay
        } else {
            false
        }
        val spamAllowed = killerCooldown.tryAcquire(killer.uniqueId.toString()) &&
            victimCooldown.tryAcquire(context.victim.uniqueId.toString())
        val broadcast = spamAllowed && broadcastLimiter.tryAcquire()
        val effect = effectLimiter.tryAcquire()
        val revenge = sameVictimTimes.containsKey(reverseKey)
        return KillProcessResult(
            countStats = !farmBlocked,
            shouldBroadcast = broadcast,
            shouldEffect = effect,
            shutdownStreak = 0,
            revenge = revenge
        )
    }
}
