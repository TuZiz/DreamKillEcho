package ym.dreamkillecho.command

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import ym.dreamkillecho.DreamKillEcho
import ym.dreamkillecho.bootstrap.PluginServices
import ym.dreamkillecho.command.sub.CustomCommand
import ym.dreamkillecho.command.sub.GuiCommand
import ym.dreamkillecho.command.sub.HelpCommand
import ym.dreamkillecho.command.sub.PreviewCommand
import ym.dreamkillecho.command.sub.ReloadCommand
import ym.dreamkillecho.command.sub.ReviewCommand
import ym.dreamkillecho.command.sub.SetCommand
import ym.dreamkillecho.command.sub.StatsCommand
import ym.dreamkillecho.command.sub.ThemeCommand
import ym.dreamkillecho.command.sub.ToggleCommand
import ym.dreamkillecho.util.Permissions

class CommandRouter(private val plugin: DreamKillEcho) : CommandExecutor, TabCompleter {
    @Volatile
    private var services: PluginServices? = null
    private val subCommands: List<SubCommand> = listOf(
        HelpCommand(),
        ReloadCommand(),
        ToggleCommand(),
        GuiCommand(),
        SetCommand(),
        PreviewCommand(),
        ThemeCommand(),
        CustomCommand(),
        ReviewCommand(),
        StatsCommand()
    )

    fun bind(services: PluginServices?) {
        this.services = services
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission(Permissions.USE)) {
            val current = services
            if (current != null) {
                CommandUtil.deny(sender, current)
            } else {
                sender.sendMessage(CommandUtil.FALLBACK_NO_PERMISSION)
            }
            return true
        }
        val current = services
        if (current == null) {
            sender.sendMessage(CommandUtil.FALLBACK_PLUGIN_NOT_READY)
            return true
        }
        val name = args.firstOrNull()?.lowercase() ?: "help"
        val sub = subCommands.firstOrNull { name in it.names } ?: subCommands.first()
        return sub.execute(sender, args.drop(1), CommandContext(plugin, current, name))
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (!sender.hasPermission(Permissions.USE)) return emptyList()
        val current = services ?: return emptyList()
        if (args.size == 1) {
            return subCommands.flatMap { it.visibleNames }.distinct().filter { it.startsWith(args[0], true) }
        }
        val sub = subCommands.firstOrNull { args[0].lowercase() in it.names } ?: return emptyList()
        return sub.tab(sender, args.drop(1), CommandContext(plugin, current, args[0].lowercase()))
    }
}
