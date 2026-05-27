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
        val foliaMode = services.scheduler.platformName.contains("Folia", ignoreCase = true)
        if (!services.config.settings.broadcast.includeVanillaDeathMessage) event.deathMessage = null
        val context = services.deathAnalyzer.analyze(event)
        val rules = services.config.settings.worldRules
        val worldAllowed = rules.allowed(context.world)
        val allowStats = worldAllowed || rules.blockedWorldStats
        val allowBroadcast = worldAllowed || rules.blockedWorldBroadcast
        val allowEffects = worldAllowed || rules.blockedWorldEffects
        if (!allowStats && !allowBroadcast && !allowEffects) return

        val process = services.antiAbuse.evaluate(
            context = context,
            trackStats = allowStats,
            trackBroadcast = allowBroadcast,
            trackEffects = allowEffects
        )
        val existingVictimStats = services.storage.stats(context.victim.uniqueId)
        var previousVictimStreak = existingVictimStats.currentStreak
        services.deathAnalyzer.fillPlaceholders(context, 0, existingVictimStats.maxStreak, services.messages.prefix, "default", services.config.settings.serverName)
        if (allowStats) {
            val update = services.storage.recordDeath(context.victim.uniqueId, context.killer?.uniqueId, process.countStats)
            previousVictimStreak = update.previousVictimStreak
            if (context.killer != null && process.countStats) {
                val themeName = if (foliaMode) {
                    "default"
                } else {
                    services.themes.firstAvailable(
                        context.killer,
                        services.storage.profile(context.killer.uniqueId, context.killer.name).selectedTheme
                    ).displayName
                }
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
        services.storage.logKill(KillLog(context.killer?.uniqueId, context.victim.uniqueId, context.weapon, context.world, context.deathCause, context.distance))
    }
}
