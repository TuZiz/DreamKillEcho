package ym.dreamkillecho.death

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import ym.dreamkillecho.bootstrap.PluginServices
import ym.dreamkillecho.storage.KillLog

class DeathListener(private val services: PluginServices) : Listener {
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        services.storage.preparePlayerAsync(event.player.uniqueId, event.player.name)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        services.storage.markProfileDirty(event.player.uniqueId)
        services.storage.markStatsDirty(event.player.uniqueId)
        services.storage.flushAsync()
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        if (!services.config.settings.broadcast.includeVanillaDeathMessage) event.deathMessage = null
        val context = services.deathAnalyzer.analyze(event)
        val worldAllowed = services.config.settings.worldRules.allowed(context.world)
        val process = services.antiAbuse.evaluate(context)
        val victimStats = services.storage.stats(context.victim.uniqueId)
        val previousVictimStreak = victimStats.currentStreak
        services.deathAnalyzer.fillPlaceholders(context, 0, victimStats.maxStreak, services.messages.prefix, "default", services.config.settings.serverName)
        if (worldAllowed || services.config.settings.worldRules.blockedWorldStats) {
            victimStats.deaths += 1
            victimStats.currentStreak = 0
            services.storage.markStatsDirty(context.victim.uniqueId)
            if (context.killer != null && process.countStats) {
                val killerStats = services.storage.stats(context.killer.uniqueId)
                killerStats.kills += 1
                killerStats.currentStreak += 1
                killerStats.maxStreak = killerStats.maxStreak.coerceAtLeast(killerStats.currentStreak)
                killerStats.lastVictimUuid = context.victim.uniqueId
                killerStats.lastKillTime = System.currentTimeMillis()
                services.storage.markStatsDirty(context.killer.uniqueId)
                services.deathAnalyzer.fillPlaceholders(
                    context,
                    killerStats.currentStreak,
                    killerStats.maxStreak,
                    services.messages.prefix,
                    services.themes.firstAvailable(context.killer, services.storage.profile(context.killer.uniqueId, context.killer.name).selectedTheme).displayName,
                    services.config.settings.serverName
                )
            } else {
                services.deathAnalyzer.fillPlaceholders(context, 0, victimStats.maxStreak, services.messages.prefix, "default", services.config.settings.serverName)
            }
        }
        if ((worldAllowed || services.config.settings.worldRules.blockedWorldBroadcast) && process.shouldBroadcast) {
            services.broadcast.broadcast(context)
            services.broadcast.sendCard(context)
            services.broadcast.sendStreak(context, previousVictimStreak, process.revenge)
        }
        if ((worldAllowed || services.config.settings.worldRules.blockedWorldEffects) && process.shouldEffect) {
            services.effects.play(context)
        }
        services.storage.logKill(KillLog(context.killer?.uniqueId, context.victim.uniqueId, context.weapon, context.world, context.deathCause, context.distance))
    }
}
