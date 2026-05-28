package ym.dreamkillecho.placeholder

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import ym.dreamkillecho.bootstrap.PluginServices
import ym.dreamkillecho.DreamKillEcho
import ym.dreamkillecho.theme.KillTheme

class DreamKillEchoExpansion(private val plugin: DreamKillEcho) : PlaceholderExpansion() {
    override fun getIdentifier(): String = "dreamkillecho"

    override fun getAuthor(): String = "DreamKillEcho"

    override fun getVersion(): String = plugin.description.version

    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String {
        val services = plugin.services ?: return ""
        val theme = resolveTheme(services, player)
        return when (params.lowercase()) {
            "theme" -> theme.displayName
            "theme_id" -> theme.id
            "theme_rarity" -> theme.rarity
            "rarity" -> theme.rarity
            else -> ""
        }
    }

    private fun resolveTheme(services: PluginServices, player: OfflinePlayer?): KillTheme {
        if (player == null) return services.themes.defaultTheme()
        val name = player.name ?: player.uniqueId.toString()
        val selected = services.storage.profile(player.uniqueId, name).selectedTheme
        return services.themes.selectedOrDefault(selected)
    }
}
