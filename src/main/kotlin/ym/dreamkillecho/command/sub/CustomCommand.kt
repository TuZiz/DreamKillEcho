package ym.dreamkillecho.command.sub

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
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
        return when (args.getOrNull(0)?.lowercase()) {
            "set" -> set(player, args.drop(1), context)
            "reset" -> reset(player, context)
            "preview" -> preview(player, args.drop(1), context)
            else -> {
                context.services.messages.send(player, "custom-usage")
                true
            }
        }
    }

    override fun tab(sender: CommandSender, args: List<String>, context: CommandContext): List<String> {
        if (args.size == 1) return listOf("set", "reset", "preview").filter { it.startsWith(args[0], true) }
        return emptyList()
    }

    private fun set(player: Player, parts: List<String>, context: CommandContext): Boolean {
        val message = parts.joinToString(" ").trim()
        if (message.isBlank()) {
            context.services.messages.send(player, "custom-usage")
            return true
        }
        val result = context.services.customMessages.set(player, message)
        when (result.result) {
            CustomMessageResult.SAVED -> context.services.messages.send(player, "custom-set-success")
            CustomMessageResult.PENDING -> context.services.messages.send(player, "custom-set-pending")
            CustomMessageResult.COOLDOWN -> context.services.messages.send(player, "custom-cooldown", mapOf("time" to result.cooldownSeconds.toString()))
            CustomMessageResult.TOO_LONG -> context.services.messages.send(player, "custom-too-long", mapOf("max" to context.services.config.settings.custom.maxLength.toString()))
            CustomMessageResult.BLOCKED_WORD -> context.services.messages.send(player, "custom-blocked-word")
            CustomMessageResult.INVALID_TAG -> context.services.messages.send(player, "custom-invalid-tag")
        }
        return true
    }

    private fun reset(player: Player, context: CommandContext): Boolean {
        context.services.customMessages.reset(player)
        context.services.messages.send(player, "custom-reset-success")
        return true
    }

    private fun preview(player: Player, parts: List<String>, context: CommandContext): Boolean {
        val message = parts.joinToString(" ").trim()
        if (message.isBlank()) {
            context.services.messages.send(player, "custom-usage")
            return true
        }
        val preview = context.services.customMessages.preview(player, message)
        when (preview.result) {
            CustomMessageResult.TOO_LONG -> {
                context.services.messages.send(player, "custom-too-long", mapOf("max" to context.services.config.settings.custom.maxLength.toString()))
                return true
            }
            CustomMessageResult.BLOCKED_WORD -> {
                context.services.messages.send(player, "custom-blocked-word")
                return true
            }
            CustomMessageResult.INVALID_TAG -> {
                context.services.messages.send(player, "custom-invalid-tag")
                return true
            }
            else -> Unit
        }
        val rendered = context.services.messages.component(
            preview.message ?: message,
            player,
            CommandUtil.previewPlaceholders(player, "default", context.services),
            CommandUtil.previewComponentPlaceholders()
        )
        context.services.messages.send(player, "custom-preview", componentPlaceholders = mapOf("message" to rendered))
        return true
    }
}
