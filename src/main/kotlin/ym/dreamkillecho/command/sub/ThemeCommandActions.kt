package ym.dreamkillecho.command.sub

import org.bukkit.entity.Player
import ym.dreamkillecho.command.CommandContext
import ym.dreamkillecho.command.CommandUtil
import ym.dreamkillecho.util.Permissions

object ThemeCommandActions {
    fun openMenu(player: Player, context: CommandContext) {
        if (!player.hasPermission(Permissions.USE)) {
            CommandUtil.deny(player, context.services)
            return
        }
        context.services.themeMenu.open(player)
    }

    fun listThemes(player: Player, context: CommandContext) {
        context.services.messages.send(player, "theme-list-header")
        for (theme in context.services.themes.all()) {
            context.services.messages.send(
                player,
                "theme-list-entry",
                mapOf(
                    "theme" to theme.id,
                    "display" to theme.displayName,
                    "status" to context.services.messages.raw(if (player.hasPermission(theme.permission)) "theme-owned" else "theme-locked")
                )
            )
        }
    }

    fun setTheme(player: Player, id: String?, context: CommandContext) {
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

    fun preview(player: Player, id: String?, context: CommandContext) {
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
        context.services.messages.send(
            player,
            "theme-preview",
            mapOf("message" to context.services.messages.renderedString(theme.message, player, placeholders))
        )
        context.services.messages.sendRaw(player, theme.message, placeholders, componentPlaceholders)
    }

    fun themeIds(context: CommandContext, prefix: String): List<String> {
        return context.services.themes.all().map { it.id }.filter { it.startsWith(prefix, true) }
    }
}
