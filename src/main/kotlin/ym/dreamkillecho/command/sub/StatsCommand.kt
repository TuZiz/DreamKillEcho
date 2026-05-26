package ym.dreamkillecho.command.sub

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import ym.dreamkillecho.command.CommandContext
import ym.dreamkillecho.command.CommandUtil
import ym.dreamkillecho.command.SubCommand
import ym.dreamkillecho.util.Permissions

class StatsCommand : SubCommand {
    override val names: Set<String> = setOf("stats", "top", "resetstats")

    override fun execute(sender: CommandSender, args: List<String>, context: CommandContext): Boolean {
        return when (context.subName) {
            "top" -> top(sender, args.getOrNull(0), context)
            "resetstats" -> reset(sender, args.getOrNull(0), context)
            else -> stats(sender, args.getOrNull(0), context)
        }
    }

    override fun tab(sender: CommandSender, args: List<String>, context: CommandContext): List<String> {
        return if (context.subName == "top" && args.size == 1) listOf("kills", "streak").filter { it.startsWith(args[0], true) } else emptyList()
    }

    private fun stats(sender: CommandSender, target: String?, context: CommandContext): Boolean {
        if (!sender.hasPermission(Permissions.ADMIN_STATS) && sender !is Player) return CommandUtil.deny(sender, context.services)
        val player = target?.let { Bukkit.getOfflinePlayer(it) } ?: (sender as? Player) ?: return context.services.messages.send(sender, "player-only").let { true }
        val value = context.services.storage.stats(player.uniqueId)
        context.services.messages.send(sender, "stats-format", mapOf(
            "player" to (player.name ?: target ?: player.uniqueId.toString()),
            "kills" to value.kills.toString(),
            "deaths" to value.deaths.toString(),
            "streak" to value.currentStreak.toString(),
            "max_streak" to value.maxStreak.toString()
        ))
        return true
    }

    private fun top(sender: CommandSender, type: String?, context: CommandContext): Boolean {
        if (!sender.hasPermission(Permissions.ADMIN_STATS)) return CommandUtil.deny(sender, context.services)
        context.services.storage.topStats(type ?: "kills", 10).thenAccept { rows ->
            val sendTask = {
                rows.forEachIndexed { index, row ->
                    val profile = context.services.storage.profile(row.uuid)
                    val value = if (type.equals("streak", true)) row.maxStreak else row.kills
                    context.services.messages.send(sender, "top-format", mapOf("rank" to (index + 1).toString(), "player" to profile.name, "value" to value.toString()))
                }
            }
            if (sender is Player) context.services.scheduler.runEntity(sender, sendTask) else context.services.scheduler.runMain(sendTask)
        }
        return true
    }

    private fun reset(sender: CommandSender, target: String?, context: CommandContext): Boolean {
        if (!sender.hasPermission(Permissions.ADMIN_RESET_STATS)) return CommandUtil.deny(sender, context.services)
        val offline = target?.let { Bukkit.getOfflinePlayer(it) } ?: return context.services.messages.send(sender, "error-player-not-found").let { true }
        context.services.storage.resetStats(offline.uniqueId)
        context.services.messages.send(sender, "resetstats-success", mapOf("player" to (offline.name ?: target)))
        return true
    }
}
