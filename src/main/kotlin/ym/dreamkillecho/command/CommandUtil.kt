package ym.dreamkillecho.command

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import ym.dreamkillecho.bootstrap.PluginServices
import ym.dreamkillecho.storage.PlayerProfile
import java.util.UUID

object CommandUtil {
    fun deny(sender: CommandSender, services: PluginServices): Boolean {
        services.messages.send(sender, "no-permission")
        return true
    }

    fun requirePlayer(sender: CommandSender, services: PluginServices): Player? {
        val player = sender as? Player
        if (player == null) services.messages.send(sender, "player-only")
        return player
    }

    fun findProfile(target: String?, services: PluginServices): PlayerProfile? {
        return target?.let { name ->
            services.storage.cachedProfiles().firstOrNull { it.name.equals(name, true) }
                ?: runCatching { services.storage.profile(UUID.fromString(name)) }.getOrNull()
        }
    }

    fun previewPlaceholders(player: Player, theme: String, services: PluginServices): Map<String, String> {
        val stats = services.storage.stats(player.uniqueId)
        return mapOf(
            "killer" to player.name,
            "victim" to player.name,
            "mob" to player.name,
            "weapon" to "Diamond Sword",
            "world" to player.world.name,
            "killer_health" to "20",
            "victim_health" to "0",
            "distance" to "8.0",
            "streak" to stats.currentStreak.coerceAtLeast(1).toString(),
            "max_streak" to stats.maxStreak.toString(),
            "death_cause" to "preview",
            "prefix" to services.messages.prefix,
            "theme" to theme,
            "server" to services.config.settings.serverName
        )
    }
}
