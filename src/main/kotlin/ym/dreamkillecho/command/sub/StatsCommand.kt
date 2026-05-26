package ym.dreamkillecho.command.sub

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import ym.dreamkillecho.command.CommandContext
import ym.dreamkillecho.command.CommandUtil
import ym.dreamkillecho.command.SubCommand
import ym.dreamkillecho.util.Permissions
import java.util.concurrent.CompletableFuture

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
        val self = sender as? Player
        if (target == null && self == null) return context.services.messages.send(sender, "player-only").let { true }
        val lookup = target ?: self!!.uniqueId.toString()
        context.services.storage.findProfileAsync(lookup).thenCompose { profile ->
            if (profile == null) {
                sendSafely(sender, context, "error-player-not-found")
                CompletableFuture.completedFuture(null)
            } else {
                context.services.storage.statsAsync(profile.uuid).thenApply { value -> profile to value }
            }
        }.thenAccept { result ->
            val (profile, value) = result ?: return@thenAccept
            sendSafely(sender, context, "stats-format", mapOf(
                "player" to profile.name,
                "kills" to value.kills.toString(),
                "deaths" to value.deaths.toString(),
                "streak" to value.currentStreak.toString(),
                "max_streak" to value.maxStreak.toString()
            ))
        }
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
        if (target == null) return context.services.messages.send(sender, "error-player-not-found").let { true }
        context.services.storage.findProfileAsync(target).thenAccept { profile ->
            if (profile == null) {
                sendSafely(sender, context, "error-player-not-found")
            } else {
                context.services.storage.resetStats(profile.uuid)
                sendSafely(sender, context, "resetstats-success", mapOf("player" to profile.name))
            }
        }
        return true
    }

    private fun sendSafely(sender: CommandSender, context: CommandContext, key: String, placeholders: Map<String, String> = emptyMap()) {
        val task = { context.services.messages.send(sender, key, placeholders) }
        if (sender is Player) context.services.scheduler.runEntity(sender, task) else context.services.scheduler.runMain(task)
    }
}
