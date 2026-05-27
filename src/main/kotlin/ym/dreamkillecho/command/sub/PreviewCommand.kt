package ym.dreamkillecho.command.sub

import org.bukkit.command.CommandSender
import ym.dreamkillecho.command.CommandContext
import ym.dreamkillecho.command.CommandUtil
import ym.dreamkillecho.command.SubCommand

class PreviewCommand : SubCommand {
    override val names: Set<String> = setOf("preview")

    override fun execute(sender: CommandSender, args: List<String>, context: CommandContext): Boolean {
        val player = CommandUtil.requirePlayer(sender, context.services) ?: return true
        ThemeCommandActions.preview(player, args.getOrNull(0), context)
        return true
    }

    override fun tab(sender: CommandSender, args: List<String>, context: CommandContext): List<String> {
        if (args.size == 1) return ThemeCommandActions.themeIds(context, args[0])
        return emptyList()
    }
}
