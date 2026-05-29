package ym.dreamkillecho.gui

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

data class ThemeMenuConfig(
    val title: String,
    val size: Int,
    val pageSlots: List<Int>,
    val staticSlots: List<ThemeMenuStaticSlot>,
    val closeOnSelect: Boolean,
    val selectedStatus: String,
    val availableStatus: String,
    val lockedStatus: String,
    val available: ThemeMenuItemConfig,
    val selected: ThemeMenuItemConfig,
    val locked: ThemeMenuItemConfig
) {
    companion object {
        private val defaultGuiPlain = listOf(
            "X#######X",
            "#@@@@@@@#",
            "#@@@@@@@#",
            "#@@@@@@@#",
            "#@@@@@@@#",
            "L#S#B#N#X"
        )

        fun load(plugin: JavaPlugin): ThemeMenuConfig {
            val file = File(plugin.dataFolder, "gui/theme-menu.yml")
            val yaml = YamlConfiguration.loadConfiguration(file)
            val shape = normalizeShape(plugin, readPlain(yaml))
            val keys = readKeys(yaml.getConfigurationSection("GuiKey"))
            val templates = readTemplates(yaml.getConfigurationSection("templates"))
            val layout = readLayout(plugin, shape, keys)
            if (layout.pageSlots.isEmpty()) {
                plugin.logger.warning("[DreamKillEcho] gui/theme-menu.yml does not contain any '@' page slots.")
            }
            return ThemeMenuConfig(
                title = yaml.getString("Title") ?: yaml.getString("title")
                    ?: "&#8EC5FF&lKill Echo Themes &#94A3B8| &#F8FAFC<unlocked>/<total>",
                size = shape.size * 9,
                pageSlots = layout.pageSlots,
                staticSlots = layout.staticSlots,
                closeOnSelect = readBoolean(yaml, true, "CloseOnSelect", "close-on-select", "Options.CloseOnSelect"),
                selectedStatus = yaml.getString("Status.Selected") ?: yaml.getString("status.selected") ?: "selected",
                availableStatus = yaml.getString("Status.Available") ?: yaml.getString("status.available") ?: "available",
                lockedStatus = yaml.getString("Status.Locked") ?: yaml.getString("status.locked") ?: "locked",
                available = templates["theme-available"]
                    ?: templates["available"]
                    ?: readItem(findSection(yaml, "ThemeItems.Available", "items.available"), "LIME_DYE", "&#A8E6CF&l<display>"),
                selected = templates["theme-selected"]
                    ?: templates["selected"]
                    ?: readItem(findSection(yaml, "ThemeItems.Selected", "items.selected"), "NETHER_STAR", "&#FFD966&l<display>"),
                locked = templates["theme-locked"]
                    ?: templates["locked"]
                    ?: readItem(findSection(yaml, "ThemeItems.Locked", "items.locked"), "GRAY_DYE", "&#FF6B6B&l<display>")
            )
        }

        private fun readPlain(yaml: YamlConfiguration): List<String> {
            val guiPlain = yaml.getStringList("GuiPlain")
            if (guiPlain.isNotEmpty()) return guiPlain
            return yaml.getStringList("Shape")
        }

        private fun normalizeShape(plugin: JavaPlugin, configured: List<String>): List<String> {
            val source = if (configured.isEmpty()) defaultGuiPlain else configured
            val rows = source.take(6).mapIndexed { index, row ->
                when {
                    row.length == 9 -> row
                    row.length < 9 -> {
                        plugin.logger.warning("[DreamKillEcho] gui/theme-menu.yml row ${index + 1} is shorter than 9, padded with spaces.")
                        row.padEnd(9, ' ')
                    }
                    else -> {
                        plugin.logger.warning("[DreamKillEcho] gui/theme-menu.yml row ${index + 1} is longer than 9, truncated.")
                        row.take(9)
                    }
                }
            }
            if (rows.isEmpty()) return defaultGuiPlain
            if (source.size > 6) {
                plugin.logger.warning("[DreamKillEcho] gui/theme-menu.yml has more than 6 rows; extra rows were ignored.")
            }
            return rows
        }

        private fun readKeys(section: ConfigurationSection?): Map<Char, ThemeMenuGuiKey> {
            if (section == null) return emptyMap()
            val result = linkedMapOf<Char, ThemeMenuGuiKey>()
            for (key in section.getKeys(false)) {
                val symbol = key.firstOrNull() ?: continue
                val keySection = section.getConfigurationSection(key) ?: continue
                result[symbol] = ThemeMenuGuiKey(
                    iconFunction = keySection.getString("IconFunction") ?: keySection.getString("iconFunction"),
                    base = readItem(keySection, "PAPER", " "),
                    has = readVariant(keySection.getConfigurationSection("has")),
                    normal = readVariant(keySection.getConfigurationSection("normal"))
                )
            }
            return result
        }

        private fun readTemplates(section: ConfigurationSection?): Map<String, ThemeMenuItemConfig> {
            if (section == null) return emptyMap()
            val result = linkedMapOf<String, ThemeMenuItemConfig>()
            for (key in section.getKeys(false)) {
                val templateSection = section.getConfigurationSection(key) ?: continue
                result[key.lowercase()] = readItem(
                    templateSection,
                    templateSection.getString("material", "PAPER") ?: "PAPER",
                    templateSection.getString("name", key) ?: key
                )
            }
            return result
        }

        private fun readLayout(plugin: JavaPlugin, shape: List<String>, keys: Map<Char, ThemeMenuGuiKey>): ThemeMenuLayout {
            val pageSlots = mutableListOf<Int>()
            val staticSlots = mutableListOf<ThemeMenuStaticSlot>()
            for ((rowIndex, row) in shape.withIndex()) {
                for ((columnIndex, symbol) in row.withIndex()) {
                    if (symbol == ' ') continue
                    val slot = rowIndex * 9 + columnIndex
                    val key = keys[symbol]
                    if (symbol == '@' || key?.iconFunction.equals("item", true)) {
                        pageSlots += slot
                        continue
                    }
                    if (key == null) {
                        plugin.logger.warning("[DreamKillEcho] gui/theme-menu.yml symbol '$symbol' has no GuiKey mapping; treated as filler.")
                        continue
                    }
                    staticSlots += ThemeMenuStaticSlot(slot, symbol, key)
                }
            }
            return ThemeMenuLayout(pageSlots, staticSlots)
        }

        private fun readVariant(section: ConfigurationSection?): ThemeMenuItemConfig? {
            if (section == null) return null
            return readItem(
                section,
                section.getString("Material", section.getString("material", "PAPER") ?: "PAPER") ?: "PAPER",
                section.getString("Name", section.getString("name", " ")) ?: " "
            )
        }

        private fun readItem(section: ConfigurationSection?, fallbackMaterial: String, fallbackName: String): ThemeMenuItemConfig {
            return ThemeMenuItemConfig(
                material = readString(section, fallbackMaterial, "Material", "material"),
                amount = readInt(section, 1, "Amount", "amount"),
                name = readString(section, fallbackName, "Name", "name"),
                lore = readStringList(section, "Lore", "lore"),
                glow = readBoolean(section, false, "Glint", "glint", "Glow", "glow"),
                customModelData = readNullableInt(section, "CustomModelData", "custom-model-data", "customModelData")
            )
        }

        private fun findSection(root: ConfigurationSection, vararg paths: String): ConfigurationSection? {
            for (path in paths) {
                root.getConfigurationSection(path)?.let { return it }
            }
            return null
        }

        private fun readString(section: ConfigurationSection?, default: String, vararg keys: String): String {
            if (section == null) return default
            for (key in keys) {
                section.getString(key)?.let { return it }
            }
            return default
        }

        private fun readStringList(section: ConfigurationSection?, vararg keys: String): List<String> {
            if (section == null) return emptyList()
            for (key in keys) {
                val list = section.getStringList(key)
                if (list.isNotEmpty()) return list
            }
            return emptyList()
        }

        private fun readInt(section: ConfigurationSection?, default: Int, vararg keys: String): Int {
            if (section == null) return default
            for (key in keys) {
                if (section.contains(key)) return section.getInt(key, default)
            }
            return default
        }

        private fun readNullableInt(section: ConfigurationSection?, vararg keys: String): Int? {
            if (section == null) return null
            for (key in keys) {
                if (section.contains(key)) return section.getInt(key)
            }
            return null
        }

        private fun readBoolean(section: ConfigurationSection?, default: Boolean, vararg keys: String): Boolean {
            if (section == null) return default
            for (key in keys) {
                if (section.contains(key)) return section.getBoolean(key, default)
            }
            return default
        }
    }
}

data class ThemeMenuGuiKey(
    val iconFunction: String?,
    val base: ThemeMenuItemConfig?,
    val has: ThemeMenuItemConfig?,
    val normal: ThemeMenuItemConfig?
)

data class ThemeMenuStaticSlot(
    val slot: Int,
    val symbol: Char,
    val key: ThemeMenuGuiKey
)

data class ThemeMenuItemConfig(
    val material: String,
    val amount: Int,
    val name: String,
    val lore: List<String>,
    val glow: Boolean,
    val customModelData: Int?
)

private data class ThemeMenuLayout(
    val pageSlots: List<Int>,
    val staticSlots: List<ThemeMenuStaticSlot>
)
