package ym.dreamkillecho.config

data class PluginSettings(
    val serverName: String,
    val worldRules: WorldRules,
    val broadcast: BroadcastSettings,
    val card: CardSettings,
    val effects: EffectSettings,
    val custom: CustomMessageSettings,
    val streaks: StreakSettings,
    val antiSpam: AntiSpamSettings,
    val antiFarm: AntiFarmSettings,
    val flushIntervalSeconds: Long
)

data class WorldRules(
    val mode: String,
    val worlds: Set<String>,
    val blockedWorldBroadcast: Boolean,
    val blockedWorldStats: Boolean,
    val blockedWorldEffects: Boolean
) {
    fun allowed(worldName: String): Boolean {
        val normalized = worldName.lowercase()
        return when (mode.lowercase()) {
            "whitelist" -> normalized in worlds
            else -> normalized !in worlds
        }
    }
}

data class BroadcastSettings(
    val enabled: Boolean,
    val includeVanillaDeathMessage: Boolean,
    val rangeMode: String,
    val nearbyRange: Double,
    val bypassPermission: String
)

data class CardSettings(
    val enabled: Boolean,
    val mode: String,
    val nearbyRange: Double,
    val lines: List<String>
)

data class EffectSettings(
    val enabled: Boolean,
    val globalLimitPerMinute: Int,
    val title: TemplateToggle,
    val subtitle: String,
    val actionbar: TemplateToggle,
    val sound: SoundSettings,
    val particle: ParticleSettings,
    val firework: FireworkSettings,
    val bossbar: TemplateToggle
)

data class TemplateToggle(val enabled: Boolean, val message: String)
data class SoundSettings(val enabled: Boolean, val name: String, val volume: Float, val pitch: Float)
data class ParticleSettings(val enabled: Boolean, val name: String, val count: Int, val maxCount: Int)
data class FireworkSettings(val enabled: Boolean, val maxPerKill: Int)

data class CustomMessageSettings(
    val maxLength: Int,
    val cooldownSeconds: Long,
    val requireReview: Boolean,
    val useAsThemeMessage: Boolean,
    val blockedWords: List<String>,
    val deniedTags: Set<String>
)

data class StreakSettings(
    val enabled: Boolean,
    val messages: Map<Int, String>,
    val shutdownMessage: String,
    val revengeMessage: String
)

data class AntiSpamSettings(
    val sameKillerCooldownSeconds: Long,
    val sameVictimCooldownSeconds: Long,
    val maxBroadcastPerMinute: Int,
    val maxEffectPerMinute: Int,
    val perPlayerMessageCooldownSeconds: Long
)

data class AntiFarmSettings(
    val enabled: Boolean,
    val sameVictimNoStatsSeconds: Long,
    val sameIpNoStats: Boolean,
    val maxSameVictimCountPerDay: Int
)

data class StorageSettings(
    val type: String,
    val sqliteFile: String,
    val mysql: MysqlSettings
)

data class MysqlSettings(
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val password: String,
    val maximumPoolSize: Int,
    val minimumIdle: Int,
    val connectionTimeout: Long
)

data class LoadedConfigs(
    val settings: PluginSettings,
    val language: org.bukkit.configuration.file.YamlConfiguration,
    val themes: org.bukkit.configuration.file.YamlConfiguration,
    val storage: StorageSettings
)
