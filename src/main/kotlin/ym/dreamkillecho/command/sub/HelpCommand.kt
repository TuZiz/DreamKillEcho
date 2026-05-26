package ym.dreamkillecho.command.sub

import org.bukkit.command.CommandSender
import ym.dreamkillecho.command.CommandContext
import ym.dreamkillecho.command.SubCommand

class HelpCommand : SubCommand {
    override val names: Set<String> = setOf("help")

    override fun execute(sender: CommandSender, args: List<String>, context: CommandContext): Boolean {
        for (line in context.services.messages.list("help")) context.services.messages.sendRaw(sender, line)
        return true
    }
}
