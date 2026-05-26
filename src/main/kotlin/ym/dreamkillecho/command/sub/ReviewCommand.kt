package ym.dreamkillecho.command.sub

import org.bukkit.command.CommandSender
import ym.dreamkillecho.command.CommandContext
import ym.dreamkillecho.command.CommandUtil
import ym.dreamkillecho.command.SubCommand
import ym.dreamkillecho.util.Permissions

class ReviewCommand : SubCommand {
    override val names: Set<String> = setOf("review", "approve", "deny")

    override fun execute(sender: CommandSender, args: List<String>, context: CommandContext): Boolean {
        if (!sender.hasPermission(Permissions.ADMIN_REVIEW)) return CommandUtil.deny(sender, context.services)
        return when (context.subName) {
            "approve" -> approve(sender, args.getOrNull(0), context)
            "deny" -> deny(sender, args.getOrNull(0), context)
            else -> review(sender, context)
        }
    }

    private fun review(sender: CommandSender, context: CommandContext): Boolean {
        val pending = context.services.customMessages.pending()
        if (pending.isEmpty()) {
            context.services.messages.send(sender, "review-empty")
            return true
        }
        context.services.messages.send(sender, "review-header")
        for (profile in pending) {
            context.services.messages.send(sender, "review-entry", mapOf("player" to profile.name, "message" to (profile.customMessage ?: "")))
        }
        return true
    }

    private fun approve(sender: CommandSender, target: String?, context: CommandContext): Boolean {
        val profile = CommandUtil.findProfile(target, context.services) ?: return context.services.messages.send(sender, "error-player-not-found").let { true }
        context.services.customMessages.approve(profile)
        context.services.messages.send(sender, "approve-success", mapOf("player" to profile.name))
        return true
    }

    private fun deny(sender: CommandSender, target: String?, context: CommandContext): Boolean {
        val profile = CommandUtil.findProfile(target, context.services) ?: return context.services.messages.send(sender, "error-player-not-found").let { true }
        context.services.customMessages.deny(profile)
        context.services.messages.send(sender, "deny-success", mapOf("player" to profile.name))
        return true
    }
}
