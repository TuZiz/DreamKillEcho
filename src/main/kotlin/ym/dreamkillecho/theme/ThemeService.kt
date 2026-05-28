package ym.dreamkillecho.theme

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import ym.dreamkillecho.storage.StorageService

data class KillTheme(
    val id: String,
    val displayName: String,
    val permission: String,
    val rarity: String,
    val message: String
)

class ThemeService(private val plugin: JavaPlugin, yaml: YamlConfiguration) {
    private val themes: Map<String, KillTheme> = loadThemes(yaml)

    fun all(): List<KillTheme> = themes.values.toList()

    fun get(id: String?): KillTheme? = themes[id ?: "default"] ?: themes["default"]

    fun require(id: String): KillTheme? = themes[id]

    fun defaultTheme(): KillTheme = themes["default"] ?: themes.values.first()

    fun selectedOrDefault(selected: String?): KillTheme {
        if (!selected.isAutoTheme()) {
            require(selected!!)?.let { return it }
        }
        return defaultTheme()
    }

    fun isUnlocked(player: Player, theme: KillTheme): Boolean = player.hasPermission(theme.permission)

    fun select(player: Player, theme: KillTheme, storage: StorageService) {
        storage.updateProfile(player.uniqueId, player.name) { profile ->
            profile.selectedTheme = theme.id
        }
    }

    fun firstAvailable(player: Player, selected: String?): KillTheme {
        if (!selected.isAutoTheme()) {
            val selectedTheme = require(selected!!)
            if (selectedTheme != null && isUnlocked(player, selectedTheme)) return selectedTheme
        }
        return defaultTheme()
    }

    private fun loadThemes(yaml: YamlConfiguration): Map<String, KillTheme> {
        val section = yaml.getConfigurationSection("themes") ?: return defaultThemeMap()
        val result = linkedMapOf<String, KillTheme>()
        for (id in section.getKeys(false)) {
            val path = "themes.$id"
            val display = yaml.getString("$path.display-name")
            val permission = yaml.getString("$path.permission")
            val rarity = yaml.getString("$path.rarity", "Common")!!
            val message = yaml.getString("$path.message")
            if (display.isNullOrBlank() || permission.isNullOrBlank() || message.isNullOrBlank()) {
                plugin.logger.warning("[DreamKillEcho] Skipped invalid theme: $id")
                continue
            }
            result[id] = KillTheme(id, display, permission, rarity, message)
        }
        if ("default" !in result) {
            result["default"] = defaultThemeMap().values.first()
        }
        return result
    }

    private fun defaultThemeMap(): Map<String, KillTheme> = mapOf(
        "default" to KillTheme("default", "Default", "dreamkillecho.default", "Common", "<prefix> <killer> killed <victim>")
    )

    private fun String?.isAutoTheme(): Boolean {
        return isNullOrBlank() || equals("default", ignoreCase = true) || equals("auto", ignoreCase = true)
    }
}
