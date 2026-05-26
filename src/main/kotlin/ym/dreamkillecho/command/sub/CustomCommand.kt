package ym.dreamkillecho.command.sub

import org.bukkit.command.CommandSender
import ym.dreamkillecho.command.CommandContext
import ym.dreamkillecho.command.CommandUtil
import ym.dreamkillecho.command.SubCommand
import ym.dreamkillecho.review.CustomMessageResult
import ym.dreamkillecho.util.Permissions

class CustomCommand : SubCommand {
    override val names: Set<String> = setOf("custom")

    override fun execute(sender: CommandSender, args: List<String>, context: CommandContext): Boolean {
        val player = CommandUtil.requirePlayer(sender, context.services) ?: return true
        if (!player.hasPermission(Permissions.CUSTOM_MESSAGE)) return CommandUtil.deny(player, context.services)
        when (args.getOrNull(0)?.lowercase()) {
            "set" -> set(player, args.drop(1).joinToString(" "), context)
            "preview" -> preview(player, context)
            "reset" -> {
                context.services.customMessages.reset(player)
                context.services.messages.send(player, "custom-reset")
            }
            "status" -> context.services.messages.send(player, "custom-status", mapOf("status" to context.services.storage.profile(player.uniqueId, player.name).customMessageStatus.name))
            else -> HelpCommand().execute(player, emptyList(), context)
        }
        return true
    }

    override fun tab(sender: CommandSender, args: List<String>, context: CommandContext): List<String> {
        return if (args.size == 1) listOf("set", "preview", "reset", "status").filter { it.startsWith(args[0], true) } else emptyList()
    }

    private fun set(player: org.bukkit.entity.Player, message: String, context: CommandContext) {
        when (context.services.customMessages.set(player, message)) {
            CustomMessageResult.SAVED -> context.services.messages.send(player, "custom-set-success")
            CustomMessageResult.PENDING -> context.services.messages.send(player, "custom-set-pending")
            CustomMessageResult.COOLDOWN -> context.services.messages.send(player, "error-cooldown")
            CustomMessageResult.TOO_LONG -> context.services.messages.send(player, "error-message-too-long", mapOf("max" to context.services.config.settings.custom.maxLength.toString()))
            CustomMessageResult.BLOCKED_WORD -> context.services.messages.send(player, "error-blocked-word")
            CustomMessageResult.INVALID_TAG -> context.services.messages.send(player, "error-invalid-tag")
        }
    }

    private fun preview(player: org.bukkit.entity.Player, context: CommandContext) {
        val template = context.services.storage.profile(player.uniqueId, player.name).customMessage ?: ""
        val placeholders = CommandUtil.previewPlaceholders(player, "custom", context.services)
        context.services.messages.send(player, "custom-preview", mapOf("message" to context.services.messages.renderedString(template, player, placeholders)))
        if (template.isNotBlank()) context.services.messages.sendRaw(player, template, placeholders)
    }
}
