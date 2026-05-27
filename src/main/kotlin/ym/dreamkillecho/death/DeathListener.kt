package ym.dreamkillecho.death

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import ym.dreamkillecho.DreamKillEcho
import ym.dreamkillecho.storage.KillLog

class DeathListener(private val plugin: DreamKillEcho) : Listener {
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val services = plugin.services ?: return
        services.storage.preparePlayerAsync(event.player.uniqueId, event.player.name)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val services = plugin.services ?: return
        services.storage.markProfileDirty(event.player.uniqueId)
        services.storage.markStatsDirty(event.player.uniqueId)
        services.storage.flushAsync()
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        val services = plugin.services ?: return
        if (!services.config.settings.broadcast.includeVanillaDeathMessage) event.deathMessage = null
        val context = services.deathAnalyzer.analyze(event)
        val rules = services.config.settings.worldRules
        val worldAllowed = rules.allowed(context.world)
        val allowStats = worldAllowed || rules.blockedWorldStats
        val allowBroadcast = worldAllowed || rules.blockedWorldBroadcast
        val allowEffects = worldAllowed || rules.blockedWorldEffects
        if (!allowStats && !allowBroadcast && !allowEffects) return

        val killer = context.killer
        if (killer != null) {
            services.scheduler.runEntity(killer) {
                if (plugin.isEnabled) processDeath(context, allowStats, allowBroadcast, allowEffects)
            }
            return
        }
        processDeath(context, allowStats, allowBroadcast, allowEffects)
    }

    private fun processDeath(
        context: DeathContext,
        allowStats: Boolean,
        allowBroadcast: Boolean,
        allowEffects: Boolean
    ) {
        val services = plugin.services ?: return
        val killer = context.killer
        if (killer != null) {
            services.deathAnalyzer.enrichKillerSnapshot(context, killer)
        }
        val process = services.antiAbuse.evaluate(
            context = context,
            trackStats = allowStats,
            trackBroadcast = allowBroadcast,
            trackEffects = allowEffects
        )
        val existingVictimStats = services.storage.stats(context.victimUuid)
        var previousVictimStreak = existingVictimStats.currentStreak
        val themeName = killer?.let {
            services.themes.firstAvailable(
                it,
                services.storage.profile(it.uniqueId, it.name).selectedTheme
            ).displayName
        } ?: "default"
        services.deathAnalyzer.fillPlaceholders(context, 0, existingVictimStats.maxStreak, services.messages.prefix, themeName, services.config.settings.serverName)
        if (allowStats) {
            val update = services.storage.recordDeath(context.victimUuid, context.killerUuid, process.countStats)
            previousVictimStreak = update.previousVictimStreak
            if (killer != null && process.countStats) {
                services.deathAnalyzer.fillPlaceholders(
                    context,
                    update.killerStreak,
                    update.killerMaxStreak,
                    services.messages.prefix,
                    themeName,
                    services.config.settings.serverName
                )
            } else {
                services.deathAnalyzer.fillPlaceholders(context, 0, update.victimMaxStreak, services.messages.prefix, "default", services.config.settings.serverName)
            }
        }
        if (allowBroadcast && process.shouldBroadcast) {
            services.broadcast.broadcast(context)
            services.broadcast.sendCard(context)
            services.broadcast.sendStreak(context, previousVictimStreak, process.revenge)
        }
        if (allowEffects && process.shouldEffect) {
            services.effects.play(context)
        }
        if (allowStats) {
            services.storage.logKill(KillLog(context.killerUuid, context.victimUuid, context.weapon, context.world, context.deathCause, context.distance))
        }
    }
}
