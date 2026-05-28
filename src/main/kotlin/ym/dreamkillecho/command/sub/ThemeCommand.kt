package ym.dreamkillecho.command.sub

import org.bukkit.command.CommandSender
import ym.dreamkillecho.command.CommandContext
import ym.dreamkillecho.command.CommandUtil
import ym.dreamkillecho.command.SubCommand

class ThemeCommand : SubCommand {
    override val names: Set<String> = setOf("theme")

    override fun execute(sender: CommandSender, args: List<String>, context: CommandContext): Boolean {
        val player = CommandUtil.requirePlayer(sender, context.services) ?: return true
        when (args.getOrNull(0)?.lowercase()) {
            null, "gui", "menu", "open", "list" -> ThemeCommandActions.openMenu(player, context)
            "set" -> ThemeCommandActions.setTheme(player, args.getOrNull(1), context)
            "preview" -> ThemeCommandActions.preview(player, args.getOrNull(1), context)
            else -> context.services.messages.send(player, "theme-not-found")
        }
        return true
    }

    override fun tab(sender: CommandSender, args: List<String>, context: CommandContext): List<String> {
        if (args.size == 2 && (args[0].equals("set", true) || args[0].equals("preview", true))) {
            return ThemeCommandActions.themeIds(context, args[1])
        }
        return emptyList()
    }
}
