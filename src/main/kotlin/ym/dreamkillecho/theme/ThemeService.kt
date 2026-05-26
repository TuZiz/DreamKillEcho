package ym.dreamkillecho.theme

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

data class KillTheme(
    val id: String,
    val displayName: String,
    val permission: String,
    val priority: Int,
    val message: String
)

class ThemeService(private val plugin: JavaPlugin, yaml: YamlConfiguration) {
    private val themes: Map<String, KillTheme> = loadThemes(yaml)

    fun all(): List<KillTheme> = themes.values.sortedWith(compareByDescending<KillTheme> { it.priority }.thenBy { it.id })

    fun get(id: String?): KillTheme? = themes[id ?: "default"] ?: themes["default"]

    fun require(id: String): KillTheme? = themes[id]

    fun firstAvailable(player: Player, selected: String?): KillTheme {
        if (!selected.isAutoTheme()) {
            val selectedTheme = require(selected!!)
            if (selectedTheme != null && player.hasPermission(selectedTheme.permission)) return selectedTheme
        }
        return highestAvailable(player)
    }

    private fun highestAvailable(player: Player): KillTheme {
        return themes.values
            .filter { player.hasPermission(it.permission) }
            .maxWithOrNull(compareBy<KillTheme> { it.priority }.thenBy { it.id })
            ?: themes["default"]
            ?: themes.values.first()
    }

    private fun loadThemes(yaml: YamlConfiguration): Map<String, KillTheme> {
        val section = yaml.getConfigurationSection("themes") ?: return defaultTheme()
        val result = linkedMapOf<String, KillTheme>()
        for (id in section.getKeys(false)) {
            val path = "themes.$id"
            val display = yaml.getString("$path.display-name")
            val permission = yaml.getString("$path.permission")
            val priority = yaml.getInt("$path.priority", 0)
            val message = yaml.getString("$path.message")
            if (display.isNullOrBlank() || permission.isNullOrBlank() || message.isNullOrBlank()) {
                plugin.logger.warning("[DreamKillEcho] Skipped invalid theme: $id")
                continue
            }
            result[id] = KillTheme(id, display, permission, priority, message)
        }
        if ("default" !in result) {
            result["default"] = defaultTheme().values.first()
        }
        return result
    }

    private fun defaultTheme(): Map<String, KillTheme> = mapOf(
        "default" to KillTheme("default", "Default", "dreamkillecho.default", 0, "<prefix> <killer> killed <victim>")
    )

    private fun String?.isAutoTheme(): Boolean {
        return isNullOrBlank() || equals("default", ignoreCase = true) || equals("auto", ignoreCase = true)
    }
}
