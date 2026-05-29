package ym.dreamkillecho.config

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import ym.dreamkillecho.gui.ThemeMenuConfig
import java.io.File

class ConfigService private constructor(
    val settings: PluginSettings,
    val language: YamlConfiguration,
    val fallbackLanguage: YamlConfiguration,
    val themes: YamlConfiguration,
    val themeMenu: ThemeMenuConfig,
    val storage: StorageSettings
) {
    companion object {
        fun load(plugin: JavaPlugin): ConfigService {
            val config = YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "config.yml"))
            val languageSettings = parseLanguage(config)
            val language = YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "lang/${languageSettings.defaultLanguage}.yml"))
            val fallbackLanguage = YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "lang/${languageSettings.fallbackLanguage}.yml"))
            val themes = YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "themes.yml"))
            val themeMenu = ThemeMenuConfig.load(plugin)
            val storageYaml = YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "storage.yml"))
            return ConfigService(parseSettings(config), language, fallbackLanguage, themes, themeMenu, parseStorage(storageYaml))
        }

        private fun parseLanguage(yaml: YamlConfiguration): LanguageSettings {
            return LanguageSettings(
                defaultLanguage = yaml.getString("language.default", "zh_cn")!!,
                fallbackLanguage = yaml.getString("language.fallback", "en_us")!!
            )
        }

        private fun parseSettings(yaml: YamlConfiguration): PluginSettings {
            val streakSection = yaml.getConfigurationSection("streaks.messages")
            val streakMessages = streakSection?.getKeys(false).orEmpty().mapNotNull { key ->
                key.toIntOrNull()?.let { it to streakSection!!.getString(key, "")!! }
            }.toMap()
            return PluginSettings(
                language = parseLanguage(yaml),
                serverName = yaml.getString("server-name", "DreamServer")!!,
                worldRules = WorldRules(
                    mode = yaml.getString("worlds.mode", "blacklist")!!,
                    worlds = yaml.getStringList("worlds.list").map { it.lowercase() }.toSet(),
                    blockedWorldBroadcast = yaml.getBoolean("worlds.blocked-world-broadcast", false),
                    blockedWorldStats = yaml.getBoolean("worlds.blocked-world-stats", false),
                    blockedWorldEffects = yaml.getBoolean("worlds.blocked-world-effects", false)
                ),
                broadcast = BroadcastSettings(
                    enabled = yaml.getBoolean("broadcast.enabled", true),
                    includeVanillaDeathMessage = yaml.getBoolean("broadcast.include-vanilla-death-message", false),
                    rangeMode = yaml.getString("broadcast.range-mode", "global")!!,
                    nearbyRange = yaml.getDouble("broadcast.nearby-range", 64.0),
                    bypassPermission = yaml.getString("broadcast.bypass-permission", "dreamkillecho.admin.bypass")!!
                ),
                card = CardSettings(
                    enabled = yaml.getBoolean("card.enabled", true),
                    mode = yaml.getString("card.mode", "killer")!!,
                    nearbyRange = yaml.getDouble("card.nearby-range", 64.0),
                    lines = yaml.getStringList("card.lines")
                ),
                effects = EffectSettings(
                    enabled = yaml.getBoolean("effects.enabled", true),
                    globalLimitPerMinute = yaml.getInt("effects.global-limit-per-minute", 30),
                    title = TemplateToggle(yaml.getBoolean("effects.title.enabled", true), yaml.getString("effects.title.title", "")!!),
                    subtitle = yaml.getString("effects.title.subtitle", "")!!,
                    actionbar = TemplateToggle(yaml.getBoolean("effects.actionbar.enabled", true), yaml.getString("effects.actionbar.message", "")!!),
                    sound = SoundSettings(
                        yaml.getBoolean("effects.sound.enabled", true),
                        yaml.getString("effects.sound.name", "entity.player.levelup")!!,
                        yaml.getDouble("effects.sound.volume", 0.7).toFloat(),
                        yaml.getDouble("effects.sound.pitch", 1.2).toFloat()
                    ),
                    particle = ParticleSettings(
                        yaml.getBoolean("effects.particle.enabled", true),
                        yaml.getString("effects.particle.name", "FIREWORK")!!,
                        yaml.getInt("effects.particle.count", 16),
                        yaml.getInt("effects.particle.max-count", 32)
                    ),
                    firework = FireworkSettings(
                        yaml.getBoolean("effects.firework.enabled", false),
                        yaml.getInt("effects.firework.max-per-kill", 1)
                    ),
                    bossbar = BossBarSettings(
                        yaml.getBoolean("effects.bossbar.enabled", false),
                        yaml.getString("effects.bossbar.message", "")!!,
                        yaml.getInt("effects.bossbar.seconds", 5).coerceAtLeast(1)
                    )
                ),
                custom = CustomMessageSettings(
                    maxLength = yaml.getInt("custom-message.max-length", 80),
                    cooldownSeconds = yaml.getLong("custom-message.cooldown-seconds", 60),
                    requireReview = yaml.getBoolean("custom-message.require-review", true),
                    useAsThemeMessage = yaml.getBoolean("custom-message.use-as-theme-message", false),
                    blockedWords = yaml.getStringList("custom-message.blocked-words"),
                    deniedTags = yaml.getStringList("custom-message.denied-tags").map { it.lowercase() }.toSet()
                ),
                streaks = StreakSettings(
                    enabled = yaml.getBoolean("streaks.enabled", true),
                    messages = streakMessages,
                    shutdownMessage = yaml.getString("streaks.shutdown-message", "")!!,
                    revengeMessage = yaml.getString("streaks.revenge-message", "")!!
                ),
                antiSpam = AntiSpamSettings(
                    sameKillerCooldownSeconds = yaml.getLong("anti-spam.same-killer-cooldown-seconds", 2),
                    sameVictimCooldownSeconds = yaml.getLong("anti-spam.same-victim-cooldown-seconds", 2),
                    maxBroadcastPerMinute = yaml.getInt("anti-spam.max-broadcast-per-minute", 60),
                    maxEffectPerMinute = yaml.getInt("anti-spam.max-effect-per-minute", 30),
                    perPlayerMessageCooldownSeconds = yaml.getLong("anti-spam.per-player-message-cooldown", 1)
                ),
                antiFarm = AntiFarmSettings(
                    enabled = yaml.getBoolean("anti-farm.enabled", true),
                    sameVictimNoStatsSeconds = yaml.getLong("anti-farm.same-victim-no-stats-seconds", 60),
                    sameIpNoStats = yaml.getBoolean("anti-farm.same-ip-no-stats", false),
                    maxSameVictimCountPerDay = yaml.getInt("anti-farm.max-same-victim-count-per-day", 5),
                    sameVictimRecordTtlSeconds = yaml.getLong("anti-farm.same-victim-record-ttl-seconds", 600),
                    revengeWindowSeconds = yaml.getLong("anti-farm.revenge-window-seconds", 600)
                ),
                flushIntervalSeconds = yaml.getLong("storage.flush-interval-seconds", 120)
            )
        }

        private fun parseStorage(yaml: YamlConfiguration): StorageSettings {
            return StorageSettings(
                type = yaml.getString("storage.type", "sqlite")!!.lowercase(),
                shutdownTimeoutSeconds = yaml.getLong("storage.shutdown-timeout-seconds", 5).coerceAtLeast(1),
                sqliteFile = yaml.getString("sqlite.file", "data.db")!!,
                mysql = MysqlSettings(
                    host = yaml.getString("mysql.host", "localhost")!!,
                    port = yaml.getInt("mysql.port", 3306),
                    database = yaml.getString("mysql.database", "dreamkillecho")!!,
                    username = yaml.getString("mysql.username", "root")!!,
                    password = yaml.getString("mysql.password", "password")!!,
                    maximumPoolSize = yaml.getInt("mysql.pool.maximum-pool-size", 10),
                    minimumIdle = yaml.getInt("mysql.pool.minimum-idle", 2),
                    connectionTimeout = yaml.getLong("mysql.pool.connection-timeout", 30000)
                )
            )
        }
    }
}
