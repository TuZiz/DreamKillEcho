package ym.dreamkillecho.command

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import ym.dreamkillecho.DreamKillEcho
import ym.dreamkillecho.bootstrap.PluginServices
import ym.dreamkillecho.config.ConfigService
import ym.dreamkillecho.message.MessageService
import ym.dreamkillecho.review.CustomMessageResult
import ym.dreamkillecho.storage.CustomMessageStatus
import ym.dreamkillecho.util.Permissions
import ym.dreamkillecho.util.Resources
import java.util.UUID

class DreamKillEchoCommand(private val plugin: DreamKillEcho) : CommandExecutor, TabCompleter {
    @Volatile
    private var services: PluginServices? = null

    fun bind(services: PluginServices?) {
        this.services = services
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val services = services
        if (services == null) {
            sender.sendMessage("DreamKillEcho is loading.")
            return true
        }
        if (args.isEmpty() || args[0].equals("help", true)) return help(sender, services)
        return when (args[0].lowercase()) {
            "reload" -> reload(sender, services)
            "toggle" -> player(sender, services) { toggle(it, services) }
            "theme" -> player(sender, services) { theme(it, args.drop(1), services) }
            "custom" -> player(sender, services) { custom(it, args.drop(1), services) }
            "review" -> review(sender, services)
            "approve" -> approve(sender, args.getOrNull(1), services)
            "deny" -> deny(sender, args.getOrNull(1), services)
            "stats" -> stats(sender, args.getOrNull(1), services)
            "top" -> top(sender, args.getOrNull(1), services)
            "resetstats" -> resetStats(sender, args.getOrNull(1), services)
            else -> help(sender, services)
        }
    }

    private fun help(sender: CommandSender, services: PluginServices): Boolean {
        for (line in services.messages.list("help")) services.messages.sendRaw(sender, line)
        return true
    }

    private fun reload(sender: CommandSender, old: PluginServices): Boolean {
        if (!sender.hasPermission(Permissions.ADMIN_RELOAD)) return deny(sender, old)
        old.scheduler.runAsync {
            try {
                Resources.ensureDefaults(plugin, listOf("config.yml", "lang/zh_cn.yml", "themes.yml", "storage.yml"))
                val loaded = ConfigService.load(plugin)
                if (loaded.storage.type != old.config.storage.type) {
                    plugin.logger.warning("[DreamKillEcho] storage.type changed in reload; active connection keeps old type until restart.")
                }
                val newMessages = MessageService(plugin, loaded.language)
                val rebuilt = PluginServices.create(plugin, old.scheduler, loaded, newMessages, old.storage)
                old.scheduler.runMain {
                    old.messages.close()
                    services = rebuilt
                    plugin.replaceServices(rebuilt)
                    rebuilt.startTimers()
                    rebuilt.messages.send(sender, "reload-success")
                }
            } catch (ex: Exception) {
                old.scheduler.runMain {
                    old.messages.send(sender, "reload-failed", mapOf("reason" to (ex.message ?: "unknown")))
                    plugin.logger.severe("[DreamKillEcho] Reload failed: ${ex.message}")
                    ex.printStackTrace()
                }
            }
        }
        return true
    }

    private fun toggle(player: Player, services: PluginServices): Boolean {
        if (!player.hasPermission(Permissions.TOGGLE)) return deny(player, services)
        val profile = services.storage.profile(player.uniqueId, player.name)
        profile.receiveBroadcast = !profile.receiveBroadcast
        services.storage.markProfileDirty(player.uniqueId)
        services.messages.send(player, if (profile.receiveBroadcast) "toggle-on" else "toggle-off")
        return true
    }

    private fun theme(player: Player, args: List<String>, services: PluginServices): Boolean {
        when (args.getOrNull(0)?.lowercase()) {
            "list", null -> {
                services.messages.send(player, "theme-list-header")
                for (theme in services.themes.all()) {
                    services.messages.send(player, "theme-list-entry", mapOf(
                        "theme" to theme.id,
                        "display" to theme.displayName,
                        "status" to services.messages.raw(if (player.hasPermission(theme.permission)) "theme-owned" else "theme-locked")
                    ))
                }
            }
            "set" -> {
                val id = args.getOrNull(1) ?: return services.messages.send(player, "theme-not-found").let { true }
                val theme = services.themes.require(id) ?: return services.messages.send(player, "theme-not-found").let { true }
                if (!player.hasPermission(theme.permission)) return services.messages.send(player, "theme-no-permission").let { true }
                services.storage.profile(player.uniqueId, player.name).selectedTheme = id
                services.storage.markProfileDirty(player.uniqueId)
                services.messages.send(player, "theme-set-success", mapOf("theme" to theme.displayName))
            }
            "preview" -> {
                val id = args.getOrNull(1) ?: return services.messages.send(player, "theme-not-found").let { true }
                val theme = services.themes.require(id) ?: return services.messages.send(player, "theme-not-found").let { true }
                if (!player.hasPermission(theme.permission)) return services.messages.send(player, "theme-no-permission").let { true }
                val placeholders = previewPlaceholders(player, theme.displayName, services)
                services.messages.send(player, "theme-preview", mapOf("message" to services.messages.renderedString(theme.message, player, placeholders)))
                services.messages.sendRaw(player, theme.message, placeholders)
            }
            else -> services.messages.send(player, "theme-not-found")
        }
        return true
    }

    private fun custom(player: Player, args: List<String>, services: PluginServices): Boolean {
        if (!player.hasPermission(Permissions.CUSTOM_MESSAGE)) return deny(player, services)
        when (args.getOrNull(0)?.lowercase()) {
            "set" -> {
                val message = args.drop(1).joinToString(" ")
                val result = services.customMessages.set(player, message)
                when (result) {
                    CustomMessageResult.SAVED -> services.messages.send(player, "custom-set-success")
                    CustomMessageResult.PENDING -> services.messages.send(player, "custom-set-pending")
                    CustomMessageResult.COOLDOWN -> services.messages.send(player, "error-cooldown")
                    CustomMessageResult.TOO_LONG -> services.messages.send(player, "error-message-too-long", mapOf("max" to services.config.settings.custom.maxLength.toString()))
                    CustomMessageResult.BLOCKED_WORD -> services.messages.send(player, "error-blocked-word")
                    CustomMessageResult.INVALID_TAG -> services.messages.send(player, "error-invalid-tag")
                }
            }
            "preview" -> {
                val profile = services.storage.profile(player.uniqueId, player.name)
                val template = profile.customMessage ?: ""
                services.messages.send(player, "custom-preview", mapOf("message" to services.messages.renderedString(template, player, previewPlaceholders(player, "custom", services))))
                if (template.isNotBlank()) services.messages.sendRaw(player, template, previewPlaceholders(player, "custom", services))
            }
            "reset" -> {
                services.customMessages.reset(player)
                services.messages.send(player, "custom-reset")
            }
            "status" -> services.messages.send(player, "custom-status", mapOf("status" to services.storage.profile(player.uniqueId, player.name).customMessageStatus.name))
            else -> help(player, services)
        }
        return true
    }

    private fun review(sender: CommandSender, services: PluginServices): Boolean {
        if (!sender.hasPermission(Permissions.ADMIN_REVIEW)) return deny(sender, services)
        val pending = services.customMessages.pending()
        if (pending.isEmpty()) {
            services.messages.send(sender, "review-empty")
            return true
        }
        services.messages.send(sender, "review-header")
        for (profile in pending) {
            services.messages.send(sender, "review-entry", mapOf("player" to profile.name, "message" to (profile.customMessage ?: "")))
        }
        return true
    }

    private fun approve(sender: CommandSender, target: String?, services: PluginServices): Boolean {
        if (!sender.hasPermission(Permissions.ADMIN_REVIEW)) return deny(sender, services)
        val profile = findProfile(target, services) ?: return services.messages.send(sender, "error-player-not-found").let { true }
        services.customMessages.approve(profile)
        services.messages.send(sender, "approve-success", mapOf("player" to profile.name))
        return true
    }

    private fun deny(sender: CommandSender, target: String?, services: PluginServices): Boolean {
        if (!sender.hasPermission(Permissions.ADMIN_REVIEW)) return deny(sender, services)
        val profile = findProfile(target, services) ?: return services.messages.send(sender, "error-player-not-found").let { true }
        services.customMessages.deny(profile)
        services.messages.send(sender, "deny-success", mapOf("player" to profile.name))
        return true
    }

    private fun stats(sender: CommandSender, target: String?, services: PluginServices): Boolean {
        if (!sender.hasPermission(Permissions.ADMIN_STATS) && sender !is Player) return deny(sender, services)
        val player = target?.let { Bukkit.getOfflinePlayer(it) } ?: (sender as? Player) ?: return services.messages.send(sender, "player-only").let { true }
        val value = services.storage.stats(player.uniqueId)
        services.messages.send(sender, "stats-format", mapOf(
            "player" to (player.name ?: target ?: player.uniqueId.toString()),
            "kills" to value.kills.toString(),
            "deaths" to value.deaths.toString(),
            "streak" to value.currentStreak.toString(),
            "max_streak" to value.maxStreak.toString()
        ))
        return true
    }

    private fun top(sender: CommandSender, type: String?, services: PluginServices): Boolean {
        if (!sender.hasPermission(Permissions.ADMIN_STATS)) return deny(sender, services)
        services.storage.topStats(type ?: "kills", 10).thenAccept { rows ->
            services.scheduler.runMain {
                rows.forEachIndexed { index, row ->
                    val profile = services.storage.profile(row.uuid)
                    val value = if (type.equals("streak", true)) row.maxStreak else row.kills
                    services.messages.send(sender, "top-format", mapOf("rank" to (index + 1).toString(), "player" to profile.name, "value" to value.toString()))
                }
            }
        }
        return true
    }

    private fun resetStats(sender: CommandSender, target: String?, services: PluginServices): Boolean {
        if (!sender.hasPermission(Permissions.ADMIN_RESET_STATS)) return deny(sender, services)
        val offline = target?.let { Bukkit.getOfflinePlayer(it) } ?: return services.messages.send(sender, "error-player-not-found").let { true }
        services.storage.resetStats(offline.uniqueId)
        services.messages.send(sender, "resetstats-success", mapOf("player" to (offline.name ?: target)))
        return true
    }

    private fun player(sender: CommandSender, services: PluginServices, action: (Player) -> Boolean): Boolean {
        val player = sender as? Player ?: return services.messages.send(sender, "player-only").let { true }
        return action(player)
    }

    private fun deny(sender: CommandSender, services: PluginServices): Boolean {
        services.messages.send(sender, "no-permission")
        return true
    }

    private fun findProfile(target: String?, services: PluginServices) = target?.let { name ->
        services.storage.cachedProfiles().firstOrNull { it.name.equals(name, true) }
            ?: runCatching { services.storage.profile(UUID.fromString(name)) }.getOrNull()
    }

    private fun previewPlaceholders(player: Player, theme: String, services: PluginServices): Map<String, String> {
        val stats = services.storage.stats(player.uniqueId)
        return mapOf(
            "killer" to player.name,
            "victim" to player.name,
            "weapon" to "Diamond Sword",
            "world" to player.world.name,
            "killer_health" to "10",
            "victim_health" to "0",
            "distance" to "8.0",
            "streak" to stats.currentStreak.coerceAtLeast(1).toString(),
            "max_streak" to stats.maxStreak.toString(),
            "death_cause" to "preview",
            "prefix" to services.messages.prefix,
            "theme" to theme,
            "server" to services.config.settings.serverName
        )
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        val services = services ?: return emptyList()
        if (args.size == 1) return listOf("help", "reload", "toggle", "theme", "custom", "review", "approve", "deny", "stats", "top", "resetstats")
            .filter { it.startsWith(args[0], true) }
        if (args.size == 2 && args[0].equals("theme", true)) return listOf("list", "set", "preview").filter { it.startsWith(args[1], true) }
        if (args.size == 3 && args[0].equals("theme", true)) return services.themes.all().map { it.id }.filter { it.startsWith(args[2], true) }
        if (args.size == 2 && args[0].equals("custom", true)) return listOf("set", "preview", "reset", "status").filter { it.startsWith(args[1], true) }
        if (args.size == 2 && args[0].equals("top", true)) return listOf("kills", "streak").filter { it.startsWith(args[1], true) }
        return emptyList()
    }
}
