package ym.dreamkillecho.command.sub

import org.bukkit.command.CommandSender
import ym.dreamkillecho.command.CommandContext
import ym.dreamkillecho.command.CommandUtil
import ym.dreamkillecho.command.SubCommand
import ym.dreamkillecho.util.Permissions

class GuiCommand : SubCommand {
    override val names: Set<String> = setOf("gui", "menu")

    override fun execute(sender: CommandSender, args: List<String>, context: CommandContext): Boolean {
        val player = CommandUtil.requirePlayer(sender, context.services) ?: return true
        if (!player.hasPermission(Permissions.USE)) return CommandUtil.deny(player, context.services)
        context.services.themeMenu.open(player)
        return true
    }
}
