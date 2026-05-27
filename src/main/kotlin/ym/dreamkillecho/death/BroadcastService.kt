package ym.dreamkillecho.death

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import net.kyori.adventure.text.Component
import ym.dreamkillecho.config.PluginSettings
import ym.dreamkillecho.message.MessageService
import ym.dreamkillecho.scheduler.SchedulerAdapter
import ym.dreamkillecho.storage.CustomMessageStatus
import ym.dreamkillecho.storage.StorageService
import ym.dreamkillecho.theme.ThemeService
import ym.dreamkillecho.util.PerKeyCooldown
import ym.dreamkillecho.util.Permissions

class BroadcastService(
    private val scheduler: SchedulerAdapter,
    private val settings: PluginSettings,
    private val messages: MessageService,
    private val themes: ThemeService,
    private val storage: StorageService
) {
    private val playerMessageCooldown = PerKeyCooldown(settings.antiSpam.perPlayerMessageCooldownSeconds * 1000L)
    private val foliaMode = scheduler.platformName.contains("Folia", ignoreCase = true)

    fun broadcast(context: DeathContext) {
        if (!settings.broadcast.enabled) return
        val killer = context.killer
        if (killer != null && foliaMode) {
            scheduler.runEntity(killer) {
                val placeholders = context.placeholders.toMutableMap()
                val template = playerKillTemplate(killer, context, placeholders)
                scheduler.runMain {
                    sendTemplate(receivers(context), template, placeholders, context.componentPlaceholders, useCooldown = true)
                }
            }
            return
        }
        if (foliaMode) {
            scheduler.runMain {
                sendTemplate(receivers(context), environmentTemplate(context), context.placeholders, context.componentPlaceholders, useCooldown = true)
            }
            return
        }
        val receivers = receivers(context)
        val template = if (killer != null) playerKillTemplate(killer, context, context.placeholders) else environmentTemplate(context)
        sendTemplate(receivers, template, context.placeholders, context.componentPlaceholders, useCooldown = true)
    }

    fun sendCard(context: DeathContext) {
        if (!settings.card.enabled || context.killer == null) return
        val killer = context.killer
        if (foliaMode) {
            scheduler.runEntity(killer) {
                if (!killer.hasPermission(Permissions.CARD_VIP) && !killer.hasPermission(Permissions.CARD_SVIP)) return@runEntity
                scheduler.runMain {
                    val receivers = when (settings.card.mode.lowercase()) {
                        "global" -> Bukkit.getOnlinePlayers().toList()
                        "nearby" -> nearbyOrFallback(context, settings.card.nearbyRange)
                        else -> listOf(killer)
                    }
                    sendLines(receivers, settings.card.lines, context.placeholders, context.componentPlaceholders)
                }
            }
            return
        }
        if (!killer.hasPermission(Permissions.CARD_VIP) && !killer.hasPermission(Permissions.CARD_SVIP)) return
        val receivers = when (settings.card.mode.lowercase()) {
            "global" -> Bukkit.getOnlinePlayers().toList()
            "nearby" -> nearbyOrFallback(context, settings.card.nearbyRange)
            else -> listOf(killer)
        }
        sendLines(receivers, settings.card.lines, context.placeholders, context.componentPlaceholders)
    }

    fun sendStreak(context: DeathContext, previousVictimStreak: Int, revenge: Boolean) {
        if (!settings.streaks.enabled) return
        val killer = context.killer ?: return
        val streak = context.placeholders["streak"]?.toIntOrNull() ?: return
        if (foliaMode) {
            scheduler.runMain {
                sendStreakTo(Bukkit.getOnlinePlayers().toList(), context, previousVictimStreak, revenge, streak)
            }
            return
        }
        sendStreakTo(Bukkit.getOnlinePlayers().toList(), context, previousVictimStreak, revenge, streak)
    }

    private fun sendStreakTo(
        receivers: List<Player>,
        context: DeathContext,
        previousVictimStreak: Int,
        revenge: Boolean,
        streak: Int
    ) {
        val streakTemplate = settings.streaks.messages[streak]
        if (streakTemplate != null) {
            sendTemplate(receivers, streakTemplate, context.placeholders, context.componentPlaceholders, useCooldown = false)
        }
        if (previousVictimStreak >= 3 && settings.streaks.shutdownMessage.isNotBlank()) {
            context.placeholders["streak"] = previousVictimStreak.toString()
            sendTemplate(receivers, settings.streaks.shutdownMessage, context.placeholders, context.componentPlaceholders, useCooldown = false)
            context.placeholders["streak"] = streak.toString()
        }
        if (revenge && settings.streaks.revengeMessage.isNotBlank()) {
            sendTemplate(receivers, settings.streaks.revengeMessage, context.placeholders, context.componentPlaceholders, useCooldown = false)
        }
    }

    private fun playerKillTemplate(killer: Player, context: DeathContext, placeholders: MutableMap<String, String>): String {
        val theme = themes.firstAvailable(killer, storage.profile(killer.uniqueId, killer.name).selectedTheme)
        placeholders["theme"] = theme.displayName
        val profile = storage.profile(killer.uniqueId, killer.name)
        return if (
            settings.custom.useAsThemeMessage &&
            profile.customMessageStatus == CustomMessageStatus.APPROVED &&
            !profile.customMessage.isNullOrBlank()
        ) {
            profile.customMessage!!
        } else {
            theme.message
        }
    }

    private fun environmentTemplate(context: DeathContext): String {
        val key = when (context.broadcastKey) {
            "fall", "lava", "fire", "drowning", "void", "explosion", "cactus", "suffocation", "magic", "lightning", "mob", "projectile" -> "broadcast.${context.broadcastKey}"
            else -> "broadcast.unknown"
        }
        return messages.raw(key)
    }

    private fun receivers(context: DeathContext): List<Player> {
        return when (settings.broadcast.rangeMode.lowercase()) {
            "nearby" -> nearbyOrFallback(context, settings.broadcast.nearbyRange)
            "killer" -> context.killer?.let { listOf(it) } ?: emptyList()
            else -> Bukkit.getOnlinePlayers().toList()
        }
    }

    private fun nearbyOrFallback(context: DeathContext, range: Double): List<Player> {
        if (foliaMode) return Bukkit.getOnlinePlayers().toList()
        val center = context.killer?.location ?: context.victimLocation ?: return emptyList()
        val squared = range * range
        return center.world?.players?.filter { it.location.distanceSquared(center) <= squared }.orEmpty()
    }

    private fun sendTemplate(
        receivers: List<Player>,
        template: String,
        placeholders: Map<String, String>,
        componentPlaceholders: Map<String, Component>,
        useCooldown: Boolean
    ) {
        val snapshot = placeholders.toMap()
        val componentSnapshot = componentPlaceholders.toMap()
        for (receiver in receivers.distinctBy { it.uniqueId }) {
            scheduler.runEntity(receiver) {
                if (canReceive(receiver) && (!useCooldown || playerMessageCooldown.tryAcquire(receiver.uniqueId.toString()))) {
                    messages.sendRaw(receiver, template, snapshot, componentSnapshot)
                }
            }
        }
    }

    private fun sendLines(
        receivers: List<Player>,
        lines: List<String>,
        placeholders: Map<String, String>,
        componentPlaceholders: Map<String, Component>
    ) {
        val snapshot = placeholders.toMap()
        val componentSnapshot = componentPlaceholders.toMap()
        for (receiver in receivers.distinctBy { it.uniqueId }) {
            scheduler.runEntity(receiver) {
                if (canReceive(receiver)) {
                    for (line in lines) messages.sendRaw(receiver, line, snapshot, componentSnapshot)
                }
            }
        }
    }

    private fun canReceive(player: Player): Boolean {
        if (player.hasPermission(settings.broadcast.bypassPermission)) return true
        return storage.profile(player.uniqueId, player.name).receiveBroadcast
    }
}
