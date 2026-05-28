package ym.dreamkillecho

import org.bukkit.event.HandlerList
import org.bukkit.plugin.java.JavaPlugin
import ym.dreamkillecho.bootstrap.PluginServices
import ym.dreamkillecho.command.CommandRouter
import ym.dreamkillecho.config.ConfigService
import ym.dreamkillecho.death.DeathListener
import ym.dreamkillecho.gui.ThemeMenuListener
import ym.dreamkillecho.message.MessageService
import ym.dreamkillecho.placeholder.DreamKillEchoExpansion
import ym.dreamkillecho.scheduler.SchedulerAdapter
import ym.dreamkillecho.storage.StorageService
import ym.dreamkillecho.util.Resources

class DreamKillEcho : JavaPlugin() {
    lateinit var schedulerAdapter: SchedulerAdapter
        private set

    @Volatile
    var services: PluginServices? = null
        private set

    var commandRouter: CommandRouter? = null
        private set

    @Volatile
    private var disabling: Boolean = false

    private var placeholderExpansion: Any? = null

    override fun onEnable() {
        disabling = false
        schedulerAdapter = SchedulerAdapter.create(this)
        val executor = CommandRouter(this)
        commandRouter = executor
        getCommand("dreamkillecho")?.setExecutor(executor)
        getCommand("dreamkillecho")?.tabCompleter = executor

        schedulerAdapter.runAsync {
            try {
                Resources.ensureDefaults(this, listOf("config.yml", "lang/zh_cn.yml", "lang/en_us.yml", "themes.yml", "storage.yml", "gui/theme-menu.yml"))
                if (!isEnabled || disabling) return@runAsync
                val configService = ConfigService.load(this)
                schedulerAdapter.runMain {
                    var built: PluginServices? = null
                    try {
                        if (!isEnabled || disabling) return@runMain
                        val messageService = MessageService(this, configService.language, configService.fallbackLanguage)
                        val storage = StorageService(this, configService.storage)
                        built = PluginServices.create(this, schedulerAdapter, configService, messageService, storage)
                        startStorageAndPublish(built, executor)
                    } catch (ex: Exception) {
                        built?.shutdown(closeStorage = false)
                        handleStartupFailure(ex)
                    }
                }
            } catch (ex: Exception) {
                handleStartupFailure(ex)
            }
        }
    }

    override fun onDisable() {
        disabling = true
        HandlerList.unregisterAll(this)
        val active = services
        services = null
        unregisterPlaceholderExpansion()
        commandRouter?.bind(null)
        active?.shutdown(closeStorage = false)
        if (active != null) {
            Thread({ active.storage.shutdown() }, "DreamKillEcho-StorageShutdown").apply { isDaemon = false }.start()
        }
        if (::schedulerAdapter.isInitialized) {
            schedulerAdapter.cancelAll()
        }
    }

    fun replaceServices(services: PluginServices?) {
        this.services = services
    }

    private fun startStorageAndPublish(built: PluginServices, executor: CommandRouter) {
        schedulerAdapter.runAsync {
            try {
                built.storage.start()
                if (!isEnabled || disabling) {
                    schedulerAdapter.runMain { built.shutdown(closeStorage = false) }
                    built.storage.shutdown()
                    return@runAsync
                }
                schedulerAdapter.runMain {
                    if (!isEnabled || disabling) {
                        built.shutdown(closeStorage = false)
                        schedulerAdapter.runAsync { built.storage.shutdown() }
                        return@runMain
                    }
                    services = built
                    executor.bind(built)
                    registerPlaceholderExpansion()
                    server.pluginManager.registerEvents(DeathListener(this), this)
                    server.pluginManager.registerEvents(ThemeMenuListener(this), this)
                    built.storage.prepareOnlinePlayers(built.scheduler)
                    built.startTimers()
                    logger.info("[DreamKillEcho] Enabled on ${schedulerAdapter.platformName}.")
                }
            } catch (ex: Exception) {
                schedulerAdapter.runMain { built.shutdown(closeStorage = false) }
                built.storage.shutdown()
                handleStartupFailure(ex)
            }
        }
    }

    private fun handleStartupFailure(ex: Exception) {
        logger.severe("[DreamKillEcho] Startup failed: ${ex.message}")
        ex.printStackTrace()
        if (isEnabled && !disabling) {
            schedulerAdapter.runMain {
                if (isEnabled && !disabling) server.pluginManager.disablePlugin(this)
            }
        }
    }

    private fun registerPlaceholderExpansion() {
        if (!server.pluginManager.isPluginEnabled("PlaceholderAPI")) return
        runCatching {
            unregisterPlaceholderExpansion()
            val expansion = DreamKillEchoExpansion(this)
            expansion.register()
            placeholderExpansion = expansion
            logger.info("[DreamKillEcho] PlaceholderAPI expansion registered.")
        }.onFailure {
            logger.warning("[DreamKillEcho] Failed to register PlaceholderAPI expansion: ${it.message}")
        }
    }

    private fun unregisterPlaceholderExpansion() {
        val expansion = placeholderExpansion ?: return
        placeholderExpansion = null
        runCatching {
            expansion.javaClass.getMethod("unregister").invoke(expansion)
        }.onFailure {
            logger.warning("[DreamKillEcho] Failed to unregister PlaceholderAPI expansion: ${it.message}")
        }
    }
}
