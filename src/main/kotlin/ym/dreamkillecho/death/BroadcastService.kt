package ym.dreamkillecho.death

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import ym.dreamkillecho.config.PluginSettings
import ym.dreamkillecho.message.MessageService
import ym.dreamkillecho.storage.CustomMessageStatus
import ym.dreamkillecho.storage.StorageService
import ym.dreamkillecho.theme.ThemeService
import ym.dreamkillecho.util.Permissions

class BroadcastService(
    private val settings: PluginSettings,
    private val messages: MessageService,
    private val themes: ThemeService,
    private val storage: StorageService
) {
    fun broadcast(context: DeathContext) {
        if (!settings.broadcast.enabled) return
        val killer = context.killer
        val theme = if (killer != null) {
            themes.firstAvailable(killer, storage.profile(killer.uniqueId, killer.name).selectedTheme)
        } else {
            themes.get("default")!!
        }
        context.placeholders["theme"] = theme.displayName
        val template = if (
            killer != null &&
            settings.custom.useAsThemeMessage &&
            storage.profile(killer.uniqueId, killer.name).customMessageStatus == CustomMessageStatus.APPROVED &&
            !storage.profile(killer.uniqueId, killer.name).customMessage.isNullOrBlank()
        ) {
            storage.profile(killer.uniqueId, killer.name).customMessage!!
        } else {
            theme.message
        }
        for (receiver in receivers(context)) {
            if (canReceive(receiver)) messages.sendRaw(receiver, template, context.placeholders)
        }
    }

    fun sendCard(context: DeathContext) {
        if (!settings.card.enabled || context.killer == null) return
        val killer = context.killer
        if (!killer.hasPermission(Permissions.CARD_VIP) && !killer.hasPermission(Permissions.CARD_SVIP)) return
        val receivers = when (settings.card.mode.lowercase()) {
            "global" -> Bukkit.getOnlinePlayers().toList()
            "nearby" -> nearby(context, settings.card.nearbyRange)
            else -> listOf(killer)
        }
        for (receiver in receivers) {
            if (canReceive(receiver)) {
                for (line in settings.card.lines) messages.sendRaw(receiver, line, context.placeholders)
            }
        }
    }

    fun sendStreak(context: DeathContext, previousVictimStreak: Int, revenge: Boolean) {
        val killer = context.killer ?: return
        val streak = context.placeholders["streak"]?.toIntOrNull() ?: return
        val streakTemplate = settings.streaks.messages[streak]
        if (streakTemplate != null) {
            for (receiver in Bukkit.getOnlinePlayers()) if (canReceive(receiver)) messages.sendRaw(receiver, streakTemplate, context.placeholders)
        }
        if (previousVictimStreak >= 3 && settings.streaks.shutdownMessage.isNotBlank()) {
            context.placeholders["streak"] = previousVictimStreak.toString()
            for (receiver in Bukkit.getOnlinePlayers()) if (canReceive(receiver)) messages.sendRaw(receiver, settings.streaks.shutdownMessage, context.placeholders)
            context.placeholders["streak"] = streak.toString()
        }
        if (revenge && settings.streaks.revengeMessage.isNotBlank()) {
            for (receiver in Bukkit.getOnlinePlayers()) if (canReceive(receiver)) messages.sendRaw(receiver, settings.streaks.revengeMessage, context.placeholders)
        }
    }

    private fun receivers(context: DeathContext): List<Player> {
        return when (settings.broadcast.rangeMode.lowercase()) {
            "nearby" -> nearby(context, settings.broadcast.nearbyRange)
            "killer" -> context.killer?.let { listOf(it) } ?: emptyList()
            else -> Bukkit.getOnlinePlayers().toList()
        }
    }

    private fun nearby(context: DeathContext, range: Double): List<Player> {
        val center = context.killer?.location ?: context.victim.location
        val squared = range * range
        return center.world?.players?.filter { it.location.distanceSquared(center) <= squared }.orEmpty()
    }

    private fun canReceive(player: Player): Boolean {
        if (player.hasPermission(settings.broadcast.bypassPermission)) return true
        return storage.profile(player.uniqueId, player.name).receiveBroadcast
    }
}
