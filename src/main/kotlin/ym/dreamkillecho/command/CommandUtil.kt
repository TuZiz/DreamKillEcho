package ym.dreamkillecho.command

import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import ym.dreamkillecho.bootstrap.PluginServices
import ym.dreamkillecho.storage.PlayerProfile
import java.util.UUID

object CommandUtil {
    const val FALLBACK_NO_PERMISSION = "DreamKillEcho: no permission."
    const val FALLBACK_PLUGIN_NOT_READY = "DreamKillEcho is loading."

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
                ?: runCatching { UUID.fromString(name) }.getOrNull()?.let { uuid ->
                    services.storage.cachedProfiles().firstOrNull { it.uuid == uuid }
                }
        }
    }

    fun previewPlaceholders(player: Player, theme: String, services: PluginServices): Map<String, String> {
        val stats = services.storage.stats(player.uniqueId)
        val killTheme = services.themes.require(theme)
        return mapOf(
            "killer" to player.name,
            "victim" to player.name,
            "mob" to player.name,
            "weapon" to previewWeaponName(services),
            "world" to player.world.name,
            "killer_health" to "20",
            "victim_health" to "0",
            "distance" to "8.0",
            "streak" to stats.currentStreak.coerceAtLeast(1).toString(),
            "max_streak" to stats.maxStreak.toString(),
            "death_cause" to "preview",
            "prefix" to services.messages.prefix,
            "theme" to (killTheme?.displayName ?: theme),
            "theme_id" to (killTheme?.id ?: theme),
            "rarity" to (killTheme?.rarity ?: ""),
            "theme_rarity" to (killTheme?.rarity ?: ""),
            "server" to services.config.settings.serverName
        )
    }

    fun previewComponentPlaceholders(services: PluginServices): Map<String, Component> {
        val weaponName = previewWeaponName(services)
        return mapOf(
            "weapon" to Component.text(weaponName)
        )
    }

    private fun previewWeaponName(services: PluginServices): String {
        return services.messages.rawOrNull("weapon.material.diamond_sword") ?: "Diamond Sword"
    }
}
