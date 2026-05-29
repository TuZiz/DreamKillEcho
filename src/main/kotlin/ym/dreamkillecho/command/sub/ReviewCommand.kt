package ym.dreamkillecho.command.sub

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import ym.dreamkillecho.command.CommandContext
import ym.dreamkillecho.command.CommandUtil
import ym.dreamkillecho.command.SubCommand
import ym.dreamkillecho.util.Permissions

class ReviewCommand : SubCommand {
    override val names: Set<String> = setOf("review", "approve", "deny")
    override val visibleNames: Set<String> = names

    override fun execute(sender: CommandSender, args: List<String>, context: CommandContext): Boolean {
        if (!sender.hasPermission(Permissions.ADMIN_REVIEW)) return CommandUtil.deny(sender, context.services)
        return when (context.subName) {
            "approve" -> decide(sender, args.getOrNull(0), context, approve = true)
            "deny" -> decide(sender, args.getOrNull(0), context, approve = false)
            else -> list(sender, context)
        }
    }

    private fun list(sender: CommandSender, context: CommandContext): Boolean {
        context.services.customMessages.pendingAsync().thenAccept { pending ->
            send(sender, context) {
                if (pending.isEmpty()) {
                    context.services.messages.send(sender, "review-empty")
                    return@send
                }
                context.services.messages.send(sender, "review-header")
                pending.take(10).forEach { profile ->
                    context.services.messages.send(
                        sender,
                        "review-line",
                        mapOf(
                            "player" to profile.name,
                            "uuid" to profile.uuid.toString(),
                            "message" to profile.customMessage.orEmpty()
                        )
                    )
                }
            }
        }
        return true
    }

    private fun decide(sender: CommandSender, target: String?, context: CommandContext, approve: Boolean): Boolean {
        if (target.isNullOrBlank()) {
            context.services.messages.send(sender, "error-player-not-found")
            return true
        }
        context.services.storage.findProfileAsync(target).thenAccept { profile ->
            send(sender, context) {
                if (profile == null) {
                    context.services.messages.send(sender, "error-player-not-found")
                    return@send
                }
                val changed = if (approve) context.services.customMessages.approve(profile) else context.services.customMessages.deny(profile)
                if (!changed) {
                    context.services.messages.send(sender, "review-not-pending")
                    return@send
                }
                context.services.messages.send(
                    sender,
                    if (approve) "review-approved" else "review-denied",
                    mapOf("player" to profile.name)
                )
            }
        }
        return true
    }

    private fun send(sender: CommandSender, context: CommandContext, task: () -> Unit) {
        if (sender is Player) context.services.scheduler.runEntity(sender, task) else context.services.scheduler.runMain(task)
    }
}
