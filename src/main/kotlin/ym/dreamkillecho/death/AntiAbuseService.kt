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
    private val sameVictimTtlMillis = (antiFarm.sameVictimRecordTtlSeconds.coerceAtLeast(antiFarm.sameVictimNoStatsSeconds) * 1000L)
        .coerceAtLeast(60_000L)
    private val revengeWindowMillis = (antiFarm.revengeWindowSeconds * 1000L).coerceAtLeast(0L)

    fun evaluate(
        context: DeathContext,
        trackStats: Boolean = true,
        trackBroadcast: Boolean = true,
        trackEffects: Boolean = true
    ): KillProcessResult {
        val now = System.currentTimeMillis()
        cleanup(now)
        val killer = context.killer
        if (killer == null) {
            return KillProcessResult(
                countStats = false,
                shouldBroadcast = trackBroadcast && broadcastLimiter.tryAcquire(),
                shouldEffect = trackEffects && effectLimiter.tryAcquire(),
                shutdownStreak = 0,
                revenge = false
            )
        }

        val pairKey = "${killer.uniqueId}:${context.victim.uniqueId}"
        val reverseKey = "${context.victim.uniqueId}:${killer.uniqueId}"
        val farmBlocked = trackStats && antiFarm.enabled && updateFarmState(pairKey, context, now)
        val spamAllowed = !trackBroadcast || (
            killerCooldown.tryAcquire(killer.uniqueId.toString()) &&
                victimCooldown.tryAcquire(context.victim.uniqueId.toString())
            )
        val broadcast = trackBroadcast && spamAllowed && broadcastLimiter.tryAcquire()
        val effect = trackEffects && effectLimiter.tryAcquire()
        val reverseKillTime = sameVictimTimes[reverseKey] ?: 0L
        val revenge = trackStats && revengeWindowMillis > 0L && now - reverseKillTime <= revengeWindowMillis
        return KillProcessResult(
            countStats = trackStats && !farmBlocked,
            shouldBroadcast = broadcast,
            shouldEffect = effect,
            shutdownStreak = 0,
            revenge = revenge
        )
    }

    private fun updateFarmState(pairKey: String, context: DeathContext, now: Long): Boolean {
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
        return tooSoon || sameIp || count > antiFarm.maxSameVictimCountPerDay
    }

    private fun cleanup(now: Long) {
        sameVictimTimes.entries.removeIf { now - it.value > sameVictimTtlMillis }
        val today = LocalDate.now()
        dailyCounts.entries.removeIf { it.value.first != today }
    }
}
