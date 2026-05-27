package ym.dreamkillecho.command.sub

import org.bukkit.command.CommandSender
import ym.dreamkillecho.command.CommandContext
import ym.dreamkillecho.command.CommandUtil
import ym.dreamkillecho.command.SubCommand
import ym.dreamkillecho.util.Permissions

class ThemeCommand : SubCommand {
    override val names: Set<String> = setOf("theme")
    private val visibleActions = listOf("list", "set", "preview")

    override fun execute(sender: CommandSender, args: List<String>, context: CommandContext): Boolean {
        val player = CommandUtil.requirePlayer(sender, context.services) ?: return true
        when (args.getOrNull(0)?.lowercase()) {
            "list", null -> listThemes(player, context)
            "set" -> setTheme(player, args.getOrNull(1), context)
            "preview" -> preview(player, args.getOrNull(1), context)
            "gui", "menu", "open" -> openMenu(player, context)
            else -> context.services.messages.send(player, "theme-not-found")
        }
        return true
    }

    override fun tab(sender: CommandSender, args: List<String>, context: CommandContext): List<String> {
        if (args.size == 1) return visibleActions.filter { it.startsWith(args[0], true) }
        if (args.size == 2 && (args[0].equals("set", true) || args[0].equals("preview", true))) {
            return context.services.themes.all().map { it.id }.filter { it.startsWith(args[1], true) }
        }
        return emptyList()
    }

    private fun listThemes(player: org.bukkit.entity.Player, context: CommandContext) {
        context.services.messages.send(player, "theme-list-header")
        for (theme in context.services.themes.all()) {
            context.services.messages.send(player, "theme-list-entry", mapOf(
                "theme" to theme.id,
                "display" to theme.displayName,
                "status" to context.services.messages.raw(if (player.hasPermission(theme.permission)) "theme-owned" else "theme-locked")
            ))
        }
    }

    private fun openMenu(player: org.bukkit.entity.Player, context: CommandContext) {
        if (!player.hasPermission(Permissions.USE)) {
            CommandUtil.deny(player, context.services)
            return
        }
        context.services.themeMenu.open(player)
    }

    private fun setTheme(player: org.bukkit.entity.Player, id: String?, context: CommandContext) {
        val theme = id?.let { context.services.themes.require(it) }
        if (theme == null) {
            context.services.messages.send(player, "theme-not-found")
            return
        }
        if (!player.hasPermission(theme.permission)) {
            context.services.messages.send(player, "theme-no-permission")
            return
        }
        context.services.themes.select(player, theme, context.services.storage)
        context.services.messages.send(player, "theme-set-success", mapOf("theme" to theme.displayName))
    }

    private fun preview(player: org.bukkit.entity.Player, id: String?, context: CommandContext) {
        val theme = id?.let { context.services.themes.require(it) }
        if (theme == null) {
            context.services.messages.send(player, "theme-not-found")
            return
        }
        if (!player.hasPermission(theme.permission)) {
            context.services.messages.send(player, "theme-no-permission")
            return
        }
        val placeholders = CommandUtil.previewPlaceholders(player, theme.displayName, context.services)
        val componentPlaceholders = CommandUtil.previewComponentPlaceholders()
        context.services.messages.send(player, "theme-preview", mapOf("message" to context.services.messages.renderedString(theme.message, player, placeholders)))
        context.services.messages.sendRaw(player, theme.message, placeholders, componentPlaceholders)
    }
}
