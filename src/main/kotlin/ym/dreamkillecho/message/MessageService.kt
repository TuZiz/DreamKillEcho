package ym.dreamkillecho.message

import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
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
    private val placeholderBridge = PlaceholderBridge(plugin)
    val prefix: String get() = raw("prefix")

    fun close() {
        audiences.close()
    }

    fun raw(key: String): String {
        rawOrNull(key)?.let { return it }
        plugin.logger.warning("[DreamKillEcho] Missing language key: $key")
        return key
    }

    fun rawOrNull(key: String): String? {
        return yaml.getString(key) ?: fallback.getString(key)
    }

    fun list(key: String): List<String> {
        val primary = yaml.getStringList(key)
        if (primary.isNotEmpty()) return primary
        val fallbackList = fallback.getStringList(key)
        if (fallbackList.isNotEmpty()) return fallbackList
        plugin.logger.warning("[DreamKillEcho] Missing language list key: $key")
        return listOf(key)
    }

    fun send(
        sender: CommandSender,
        key: String,
        placeholders: Map<String, String> = emptyMap(),
        componentPlaceholders: Map<String, Component> = emptyMap()
    ) {
        sendRaw(sender, raw(key), placeholders, componentPlaceholders)
    }

    fun sendRaw(
        sender: CommandSender,
        template: String,
        placeholders: Map<String, String> = emptyMap(),
        componentPlaceholders: Map<String, Component> = emptyMap()
    ) {
        val parsed = render(template, sender as? Player, placeholders, componentPlaceholders)
        audiences.sender(sender).sendMessage(parsed)
    }

    fun actionBar(
        player: Player,
        template: String,
        placeholders: Map<String, String> = emptyMap(),
        componentPlaceholders: Map<String, Component> = emptyMap()
    ) {
        audiences.player(player).sendActionBar(render(template, player, placeholders, componentPlaceholders))
    }

    fun title(
        player: Player,
        title: String,
        subtitle: String,
        placeholders: Map<String, String> = emptyMap(),
        componentPlaceholders: Map<String, Component> = emptyMap()
    ) {
        audiences.player(player).showTitle(Title.title(
            render(title, player, placeholders, componentPlaceholders),
            render(subtitle, player, placeholders, componentPlaceholders)
        ))
    }

    fun showBossBar(
        player: Player,
        template: String,
        placeholders: Map<String, String> = emptyMap(),
        componentPlaceholders: Map<String, Component> = emptyMap()
    ): BossBar {
        val bar = BossBar.bossBar(render(template, player, placeholders, componentPlaceholders), 1.0f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS)
        audiences.player(player).showBossBar(bar)
        return bar
    }

    fun hideBossBar(player: Player, bar: BossBar) {
        audiences.player(player).hideBossBar(bar)
    }

    fun component(
        template: String,
        player: Player?,
        placeholders: Map<String, String> = emptyMap(),
        componentPlaceholders: Map<String, Component> = emptyMap()
    ): Component {
        return render(template, player, placeholders, componentPlaceholders)
    }

    fun renderedString(template: String, player: Player?, placeholders: Map<String, String> = emptyMap()): String {
        return replacePlaceholders(template, player, placeholders)
    }

    fun plainString(template: String, player: Player?, placeholders: Map<String, String> = emptyMap()): String {
        return renderedString(template, player, placeholders).replace(Regex("<[^>]+>"), "")
    }

    private fun render(
        template: String,
        player: Player?,
        placeholders: Map<String, String>,
        componentPlaceholders: Map<String, Component>
    ): Component {
        val parsedTemplate = applyPlaceholderApi(template.replace("<prefix>", prefix), player)
        return miniMessage.deserialize(parsedTemplate, buildResolver(placeholders, componentPlaceholders))
    }

    private fun buildResolver(
        placeholders: Map<String, String>,
        componentPlaceholders: Map<String, Component>
    ): TagResolver {
        val builder = TagResolver.builder()
        for ((key, value) in placeholders) {
            if (!componentPlaceholders.containsKey(key)) {
                builder.resolver(Placeholder.unparsed(key, value))
            }
        }
        for ((key, value) in componentPlaceholders) {
            builder.resolver(Placeholder.component(key, value))
        }
        return builder.build()
    }

    private fun replacePlaceholders(template: String, player: Player?, placeholders: Map<String, String>): String {
        var result = template.replace("<prefix>", prefix)
        for ((key, value) in placeholders) {
            result = result.replace("<$key>", escapePlain(value))
        }
        return applyPlaceholderApi(result, player)
    }

    private fun applyPlaceholderApi(template: String, player: Player?): String {
        return placeholderBridge.apply(player, template)
    }

    private fun escapePlain(value: String): String {
        return value.replace("\\", "\\\\").replace("<", "\\<")
    }
}
