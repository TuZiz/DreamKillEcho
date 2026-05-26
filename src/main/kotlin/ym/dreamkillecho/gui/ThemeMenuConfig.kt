package ym.dreamkillecho.gui

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

data class ThemeMenuConfig(
    val title: String,
    val size: Int,
    val themeSlots: List<Int>,
    val closeOnSelect: Boolean,
    val selectedStatus: String,
    val availableStatus: String,
    val lockedStatus: String,
    val fill: ThemeMenuItemConfig,
    val available: ThemeMenuItemConfig,
    val selected: ThemeMenuItemConfig,
    val locked: ThemeMenuItemConfig
) {
    companion object {
        private val defaultThemeSlots = listOf(10, 11, 12, 13, 14, 15, 16)

        fun load(plugin: JavaPlugin): ThemeMenuConfig {
            val file = File(plugin.dataFolder, "gui/theme-menu.yml")
            val yaml = YamlConfiguration.loadConfiguration(file)
            val size = normalizeSize(plugin, yaml.getInt("size", 27))
            return ThemeMenuConfig(
                title = yaml.getString("title", "<gradient:#8ec5ff:#e0c3fc><b>DreamKillEcho Themes</b></gradient>")!!,
                size = size,
                themeSlots = normalizeSlots(plugin, yaml.getIntegerList("theme-slots"), size),
                closeOnSelect = yaml.getBoolean("close-on-select", true),
                selectedStatus = yaml.getString("status.selected", "selected")!!,
                availableStatus = yaml.getString("status.available", "available")!!,
                lockedStatus = yaml.getString("status.locked", "locked")!!,
                fill = readItem(yaml.getConfigurationSection("fill"), "BLACK_STAINED_GLASS_PANE", " "),
                available = readItem(yaml.getConfigurationSection("items.available"), "LIME_DYE", "<#a8e6cf><display></#a8e6cf>"),
                selected = readItem(yaml.getConfigurationSection("items.selected"), "NETHER_STAR", "<#ffd966><display></#ffd966>"),
                locked = readItem(yaml.getConfigurationSection("items.locked"), "GRAY_DYE", "<#ff6b6b><display></#ff6b6b>")
            )
        }

        private fun normalizeSize(plugin: JavaPlugin, configured: Int): Int {
            if (configured in 9..54 && configured % 9 == 0) return configured
            plugin.logger.warning("[DreamKillEcho] Invalid gui/theme-menu.yml size: $configured, fallback to 27.")
            return 27
        }

        private fun normalizeSlots(plugin: JavaPlugin, configured: List<Int>, size: Int): List<Int> {
            val source = if (configured.isEmpty()) defaultThemeSlots else configured
            val result = source.distinct().filter { it in 0 until size }
            if (result.isEmpty()) {
                plugin.logger.warning("[DreamKillEcho] gui/theme-menu.yml theme-slots is empty or invalid, fallback to defaults.")
                return defaultThemeSlots.filter { it in 0 until size }
            }
            if (result.size != source.distinct().size) {
                plugin.logger.warning("[DreamKillEcho] Some gui/theme-menu.yml theme-slots are outside menu size and were ignored.")
            }
            return result
        }

        private fun readItem(section: ConfigurationSection?, fallbackMaterial: String, fallbackName: String): ThemeMenuItemConfig {
            return ThemeMenuItemConfig(
                material = section?.getString("material", fallbackMaterial) ?: fallbackMaterial,
                amount = section?.getInt("amount", 1) ?: 1,
                name = section?.getString("name", fallbackName) ?: fallbackName,
                lore = section?.getStringList("lore").orEmpty(),
                glow = section?.getBoolean("glow", false) ?: false,
                customModelData = section?.let { if (it.contains("custom-model-data")) it.getInt("custom-model-data") else null }
            )
        }
    }
}

data class ThemeMenuItemConfig(
    val material: String,
    val amount: Int,
    val name: String,
    val lore: List<String>,
    val glow: Boolean,
    val customModelData: Int?
)
