package ym.dreamkillecho.command.sub

import org.bukkit.command.CommandSender
import ym.dreamkillecho.command.CommandContext
import ym.dreamkillecho.command.CommandUtil
import ym.dreamkillecho.command.SubCommand
import ym.dreamkillecho.util.Permissions

class ToggleCommand : SubCommand {
    override val names: Set<String> = setOf("toggle")

    override fun execute(sender: CommandSender, args: List<String>, context: CommandContext): Boolean {
        val player = CommandUtil.requirePlayer(sender, context.services) ?: return true
        if (!player.hasPermission(Permissions.TOGGLE)) return CommandUtil.deny(player, context.services)
        val profile = context.services.storage.profile(player.uniqueId, player.name)
        profile.receiveBroadcast = !profile.receiveBroadcast
        context.services.storage.markProfileDirty(player.uniqueId)
        context.services.messages.send(player, if (profile.receiveBroadcast) "toggle-on" else "toggle-off")
        return true
    }
}
