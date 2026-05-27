package ym.dreamkillecho.command

import org.bukkit.command.CommandSender
import ym.dreamkillecho.DreamKillEcho
import ym.dreamkillecho.bootstrap.PluginServices

data class CommandContext(
    val plugin: DreamKillEcho,
    val services: PluginServices,
    val subName: String
)

interface SubCommand {
    val names: Set<String>
    val visibleNames: Set<String>
        get() = names.firstOrNull()?.let { setOf(it) }.orEmpty()
    fun execute(sender: CommandSender, args: List<String>, context: CommandContext): Boolean
    fun tab(sender: CommandSender, args: List<String>, context: CommandContext): List<String> = emptyList()
}
