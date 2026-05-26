package ym.dreamkillecho.message

import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class MessageService(
    private val plugin: JavaPlugin,
    private val yaml: YamlConfiguration,
    private val fallback: YamlConfiguration
) {
    private val miniMessage = MiniMessage.miniMessage()
    private val audiences = BukkitAudiences.create(plugin)
    val prefix: String get() = raw("prefix")

    fun close() {
        audiences.close()
    }

    fun raw(key: String): String {
        yaml.getString(key)?.let { return it }
        fallback.getString(key)?.let { return it }
        plugin.logger.warning("[DreamKillEcho] Missing language key: $key")
        return key
    }

    fun list(key: String): List<String> {
        val primary = yaml.getStringList(key)
        if (primary.isNotEmpty()) return primary
        val fallbackList = fallback.getStringList(key)
        if (fallbackList.isNotEmpty()) return fallbackList
        plugin.logger.warning("[DreamKillEcho] Missing language list key: $key")
        return listOf(key)
    }

    fun send(sender: CommandSender, key: String, placeholders: Map<String, String> = emptyMap()) {
        sendRaw(sender, raw(key), placeholders)
    }

    fun sendRaw(sender: CommandSender, template: String, placeholders: Map<String, String> = emptyMap()) {
        val parsed = render(template, sender as? Player, placeholders)
        audiences.sender(sender).sendMessage(parsed)
    }

    fun actionBar(player: Player, template: String, placeholders: Map<String, String> = emptyMap()) {
        audiences.player(player).sendActionBar(render(template, player, placeholders))
    }

    fun title(player: Player, title: String, subtitle: String, placeholders: Map<String, String> = emptyMap()) {
        audiences.player(player).showTitle(Title.title(render(title, player, placeholders), render(subtitle, player, placeholders)))
    }

    fun component(template: String, player: Player?, placeholders: Map<String, String> = emptyMap()): Component {
        return render(template, player, placeholders)
    }

    fun renderedString(template: String, player: Player?, placeholders: Map<String, String> = emptyMap()): String {
        return replacePlaceholders(template, player, placeholders)
    }

    fun plainString(template: String, player: Player?, placeholders: Map<String, String> = emptyMap()): String {
        return renderedString(template, player, placeholders).replace(Regex("<[^>]+>"), "")
    }

    private fun render(template: String, player: Player?, placeholders: Map<String, String>): Component {
        return miniMessage.deserialize(replacePlaceholders(template, player, placeholders))
    }

    private fun replacePlaceholders(template: String, player: Player?, placeholders: Map<String, String>): String {
        var result = template.replace("<prefix>", prefix)
        for ((key, value) in placeholders) {
            result = result.replace("<$key>", escapePlain(value))
        }
        if (player != null && plugin.server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            result = runCatching {
                val clazz = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
                clazz.getMethod("setPlaceholders", Player::class.java, String::class.java).invoke(null, player, result) as String
            }.getOrDefault(result)
        }
        return result
    }

    private fun escapePlain(value: String): String {
        return value.replace("\\", "\\\\").replace("<", "\\<")
    }
}
