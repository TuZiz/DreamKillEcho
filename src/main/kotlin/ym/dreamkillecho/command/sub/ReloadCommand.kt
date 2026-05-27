package ym.dreamkillecho.command.sub

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import ym.dreamkillecho.command.CommandContext
import ym.dreamkillecho.command.CommandUtil
import ym.dreamkillecho.command.SubCommand
import ym.dreamkillecho.config.ConfigService
import ym.dreamkillecho.message.MessageService
import ym.dreamkillecho.util.Permissions
import ym.dreamkillecho.util.Resources

class ReloadCommand : SubCommand {
    override val names: Set<String> = setOf("reload")

    override fun execute(sender: CommandSender, args: List<String>, context: CommandContext): Boolean {
        val old = context.services
        if (!sender.hasPermission(Permissions.ADMIN_RELOAD)) return CommandUtil.deny(sender, old)
        old.scheduler.runAsync {
            try {
                Resources.ensureDefaults(context.plugin, listOf("config.yml", "lang/zh_cn.yml", "lang/en_us.yml", "themes.yml", "storage.yml", "gui/theme-menu.yml"))
                val loaded = ConfigService.load(context.plugin)
                if (loaded.storage.type != old.config.storage.type) {
                    context.plugin.logger.warning("[DreamKillEcho] storage.type changed in reload; active connection keeps old type until restart.")
                }
                if (!context.plugin.isEnabled) return@runAsync
                val newMessages = MessageService(context.plugin, loaded.language, loaded.fallbackLanguage)
                val rebuilt = ym.dreamkillecho.bootstrap.PluginServices.create(context.plugin, old.scheduler, loaded, newMessages, old.storage)
                old.scheduler.runMain {
                    if (!context.plugin.isEnabled) {
                        newMessages.close()
                        return@runMain
                    }
                    old.messages.close()
                    context.plugin.replaceServices(rebuilt)
                    context.plugin.commandRouter?.bind(rebuilt)
                    sendSafely(sender, rebuilt, "reload-success")
                }
            } catch (ex: Exception) {
                old.scheduler.runMain {
                    sendSafely(sender, old, "reload-failed", mapOf("reason" to (ex.message ?: "unknown")))
                    context.plugin.logger.severe("[DreamKillEcho] Reload failed: ${ex.message}")
                    ex.printStackTrace()
                }
            }
        }
        return true
    }

    private fun sendSafely(sender: CommandSender, services: ym.dreamkillecho.bootstrap.PluginServices, key: String, placeholders: Map<String, String> = emptyMap()) {
        if (sender is Player) services.scheduler.runEntity(sender) { services.messages.send(sender, key, placeholders) }
        else services.messages.send(sender, key, placeholders)
    }
}
