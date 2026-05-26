package ym.dreamkillecho.message

import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class MessageService(private val plugin: JavaPlugin, private val yaml: YamlConfiguration) {
    private val miniMessage = MiniMessage.miniMessage()
    private val audiences = BukkitAudiences.create(plugin)
    val prefix: String get() = yaml.getString("prefix", "") ?: ""

    fun close() {
        audiences.close()
    }

    fun raw(key: String): String = yaml.getString(key, "<red>Missing message: $key</red>")!!

    fun list(key: String): List<String> = yaml.getStringList(key)

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
